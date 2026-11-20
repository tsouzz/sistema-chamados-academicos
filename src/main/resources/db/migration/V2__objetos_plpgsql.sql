-- ============================================================================
-- V2: triggers, functions e stored procedure (PL/pgSQL)
-- ============================================================================

-- 1. Data de conclusão: preenchida quando o status vira final, limpa se o chamado é reaberto
CREATE OR REPLACE FUNCTION fn_trg_data_conclusao_chamado()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('CONCLUIDO', 'CANCELADO') THEN
        IF TG_OP = 'INSERT' THEN
            NEW.data_conclusao := COALESCE(NEW.data_conclusao, CURRENT_DATE);
        ELSIF OLD.status IN ('CONCLUIDO', 'CANCELADO') THEN
            NEW.data_conclusao := OLD.data_conclusao;   -- já era final: preserva
ELSE
            NEW.data_conclusao := CURRENT_DATE;
END IF;
ELSE
        NEW.data_conclusao := NULL;
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_data_conclusao_chamado
    BEFORE INSERT OR UPDATE ON chamado
                         FOR EACH ROW
                         EXECUTE FUNCTION fn_trg_data_conclusao_chamado();

-- 2. Auditoria de status. O motivo pode ser informado pela sessão (app.motivo_status),
--    o autor vem de chamado.id_ultimo_editor (NULL = sistema).
CREATE OR REPLACE FUNCTION fn_trg_auditoria_status_chamado()
RETURNS TRIGGER AS $$
DECLARE
v_motivo TEXT := NULLIF(current_setting('app.motivo_status', true), '');
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO historico_chamado (id_chamado, status_anterior, status_novo, observacao, id_usuario)
        VALUES (NEW.id_chamado, NULL, NEW.status, 'Abertura inicial do chamado no sistema.', NEW.id_ultimo_editor);
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.status IS DISTINCT FROM NEW.status) THEN
            INSERT INTO historico_chamado (id_chamado, status_anterior, status_novo, observacao, id_usuario)
            VALUES (NEW.id_chamado, OLD.status, NEW.status,
                    COALESCE(v_motivo, 'Transição operacional de status registrada.'),
                    NEW.id_ultimo_editor);
END IF;
END IF;
RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_chamado
    AFTER INSERT OR UPDATE OF status ON chamado
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_auditoria_status_chamado();

-- 3. Histórico imutável (RNF04)
CREATE OR REPLACE FUNCTION fn_trg_historico_imutavel()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'historico_chamado é imutável: % não permitido.', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_historico_imutavel
    BEFORE UPDATE OR DELETE ON historico_chamado
    FOR EACH ROW
    EXECUTE FUNCTION fn_trg_historico_imutavel();

-- 4. Encerramento automático (RF08): chamados aguardando o solicitante além do limite
--    são CANCELADOS (não concluídos), com o motivo registrado no histórico.
CREATE OR REPLACE PROCEDURE sp_encerrar_chamados_inativos(p_dias_limite INT DEFAULT 15)
LANGUAGE plpgsql
AS $$
DECLARE
v_total INT;
BEGIN
    IF p_dias_limite IS NULL OR p_dias_limite < 1 THEN
        RAISE EXCEPTION 'O limite de dias deve ser maior ou igual a 1.';
END IF;

    PERFORM set_config('app.motivo_status',
        format('Cancelado automaticamente por inatividade do solicitante (%s dias).', p_dias_limite),
        true);

UPDATE chamado
SET status = 'CANCELADO',
    data_atualizacao = CURRENT_DATE,
    id_ultimo_editor = NULL,
    versao = versao + 1
WHERE status = 'AGUARDANDO_SOLICITANTE'
  AND data_atualizacao <= (CURRENT_DATE - p_dias_limite);

GET DIAGNOSTICS v_total = ROW_COUNT;
PERFORM set_config('app.motivo_status', '', true);

    RAISE NOTICE 'Rotina executada. Chamados cancelados por inatividade: %', v_total;
END;
$$;

-- 5. Dias de tramitação: até a conclusão, ou até hoje se ainda está em andamento
CREATE OR REPLACE FUNCTION fn_dias_resolucao_chamado(p_id_chamado BIGINT)
RETURNS INT
LANGUAGE plpgsql
AS $$
DECLARE
v_abertura  DATE;
    v_conclusao DATE;
BEGIN
SELECT data_abertura, data_conclusao
INTO v_abertura, v_conclusao
FROM chamado
WHERE id_chamado = p_id_chamado;

IF NOT FOUND THEN
        RAISE EXCEPTION 'Chamado com ID % não encontrado.', p_id_chamado;
END IF;

RETURN COALESCE(v_conclusao, CURRENT_DATE) - v_abertura;
END;
$$;

-- 6. Indicadores por departamento (RF09), filtráveis por período de abertura.
--    Parâmetros NULL = sem limite. Tempo médio considera só chamados CONCLUIDOS;
--    taxa de resolutividade = concluídos / total (em %).
CREATE OR REPLACE FUNCTION fn_indicadores(p_inicio DATE DEFAULT NULL, p_fim DATE DEFAULT NULL)
RETURNS TABLE (
    departamento        VARCHAR,
    nome_departamento   VARCHAR,
    total_chamados      BIGINT,
    qtd_abertos         BIGINT,
    qtd_em_analise      BIGINT,
    qtd_aguardando      BIGINT,
    qtd_concluidos      BIGINT,
    qtd_cancelados      BIGINT,
    media_dias_conclusao NUMERIC,
    taxa_resolutividade NUMERIC
)
LANGUAGE sql
STABLE
AS $$
SELECT d.sigla,
       d.nome,
       COUNT(c.id_chamado),
       COUNT(*) FILTER (WHERE c.status = 'ABERTO'),
    COUNT(*) FILTER (WHERE c.status = 'EM_ANALISE'),
    COUNT(*) FILTER (WHERE c.status = 'AGUARDANDO_SOLICITANTE'),
    COUNT(*) FILTER (WHERE c.status = 'CONCLUIDO'),
    COUNT(*) FILTER (WHERE c.status = 'CANCELADO'),
    COALESCE(ROUND(AVG(c.data_conclusao - c.data_abertura) FILTER (WHERE c.status = 'CONCLUIDO'), 1), 0),
       COALESCE(ROUND(100.0 * COUNT(*) FILTER (WHERE c.status = 'CONCLUIDO')
                          / NULLIF(COUNT(c.id_chamado), 0), 1), 0)
FROM departamento d
         JOIN categoria cat ON cat.id_departamento = d.id_departamento
         LEFT JOIN chamado c ON c.id_categoria = cat.id_categoria
    AND (p_inicio IS NULL OR c.data_abertura >= p_inicio)
    AND (p_fim    IS NULL OR c.data_abertura <= p_fim)
GROUP BY d.id_departamento, d.sigla, d.nome
ORDER BY 3 DESC, d.sigla;
$$;