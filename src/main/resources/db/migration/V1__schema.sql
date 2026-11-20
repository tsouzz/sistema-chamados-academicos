-- ============================================================================
-- V1: estrutura das tabelas (DDL)
-- ============================================================================

-- Busca por texto (ILIKE '%termo%') em titulo e descricao
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE departamento (
                              id_departamento BIGSERIAL PRIMARY KEY,
                              nome            VARCHAR(80)  NOT NULL UNIQUE,
                              sigla           VARCHAR(10)  NOT NULL UNIQUE,
                              email_contato   VARCHAR(120) NOT NULL
);

CREATE TABLE usuario (
                         id_usuario      BIGSERIAL PRIMARY KEY,
                         nome            VARCHAR(100) NOT NULL,
                         email           VARCHAR(120) NOT NULL UNIQUE,
                         senha_hash      VARCHAR(100) NOT NULL,              -- BCrypt
                         papel           VARCHAR(20)  NOT NULL DEFAULT 'ALUNO',
                         id_departamento BIGINT,                              -- setor do técnico
                         data_cadastro   DATE         NOT NULL DEFAULT CURRENT_DATE,
                         CONSTRAINT fk_usuario_departamento FOREIGN KEY (id_departamento)
                             REFERENCES departamento (id_departamento) ON DELETE RESTRICT,
                         CONSTRAINT chk_usuario_papel CHECK (papel IN ('ALUNO', 'PROFESSOR', 'TECNICO', 'GESTOR')),
                         CONSTRAINT chk_usuario_tecnico_setor CHECK (papel <> 'TECNICO' OR id_departamento IS NOT NULL)
);

CREATE TABLE categoria (
                           id_categoria      BIGSERIAL PRIMARY KEY,
                           nome              VARCHAR(80)  NOT NULL UNIQUE,
                           prioridade_padrao VARCHAR(20)  NOT NULL DEFAULT 'BAIXA',
                           id_departamento   BIGINT       NOT NULL,
                           CONSTRAINT fk_categoria_departamento FOREIGN KEY (id_departamento)
                               REFERENCES departamento (id_departamento) ON DELETE RESTRICT,
                           CONSTRAINT chk_categoria_prioridade CHECK (prioridade_padrao IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE'))
);

CREATE TABLE chamado (
                         id_chamado             BIGSERIAL PRIMARY KEY,
                         titulo                 VARCHAR(120) NOT NULL,
                         descricao              VARCHAR(500) NOT NULL,
                         status                 VARCHAR(30)  NOT NULL DEFAULT 'ABERTO',
                         prioridade             VARCHAR(30)  NOT NULL,
                         data_abertura          DATE         NOT NULL DEFAULT CURRENT_DATE,
                         data_atualizacao       DATE         NOT NULL DEFAULT CURRENT_DATE,
                         data_conclusao         DATE,                          -- preenchida por trigger
                         observacao_atendimento VARCHAR(500),
                         versao                 BIGINT       NOT NULL DEFAULT 0,  -- controle otimista (@Version)
                         id_solicitante         BIGINT       NOT NULL,
                         id_responsavel         BIGINT,
                         id_categoria           BIGINT       NOT NULL,
                         id_ultimo_editor       BIGINT,                        -- usuário da última alteração
                         CONSTRAINT fk_chamado_solicitante FOREIGN KEY (id_solicitante)
                             REFERENCES usuario (id_usuario) ON DELETE RESTRICT,
                         CONSTRAINT fk_chamado_responsavel FOREIGN KEY (id_responsavel)
                             REFERENCES usuario (id_usuario) ON DELETE SET NULL,
                         CONSTRAINT fk_chamado_categoria FOREIGN KEY (id_categoria)
                             REFERENCES categoria (id_categoria) ON DELETE RESTRICT,
                         CONSTRAINT fk_chamado_ultimo_editor FOREIGN KEY (id_ultimo_editor)
                             REFERENCES usuario (id_usuario) ON DELETE SET NULL,
                         CONSTRAINT chk_chamado_status CHECK (status IN ('ABERTO', 'EM_ANALISE', 'AGUARDANDO_SOLICITANTE', 'CONCLUIDO', 'CANCELADO')),
                         CONSTRAINT chk_chamado_prioridade CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'URGENTE')),
    -- data_conclusao existe se, e somente se, o status é final
                         CONSTRAINT chk_chamado_conclusao CHECK ((status IN ('CONCLUIDO', 'CANCELADO')) = (data_conclusao IS NOT NULL))
);

-- Log de auditoria: gravado por trigger e imutável (UPDATE/DELETE bloqueados em V2)
CREATE TABLE historico_chamado (
                                   id_historico     BIGSERIAL PRIMARY KEY,
                                   id_chamado       BIGINT       NOT NULL,
                                   status_anterior  VARCHAR(30),
                                   status_novo      VARCHAR(30)  NOT NULL,
                                   data_modificacao TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   observacao       VARCHAR(255),
                                   id_usuario       BIGINT,                              -- NULL = ação do sistema
                                   CONSTRAINT fk_historico_chamado FOREIGN KEY (id_chamado)
                                       REFERENCES chamado (id_chamado) ON DELETE RESTRICT,
                                   CONSTRAINT fk_historico_usuario FOREIGN KEY (id_usuario)
                                       REFERENCES usuario (id_usuario) ON DELETE RESTRICT
);

-- Anexos dos chamados (metadados e caminho físico no diretório local)
CREATE TABLE anexo_chamado (
                               id_anexo        BIGSERIAL PRIMARY KEY,
                               id_chamado      BIGINT       NOT NULL,
                               id_usuario      BIGINT       NOT NULL,
                               nome_original   VARCHAR(255) NOT NULL,
                               nome_armazenado VARCHAR(255) NOT NULL UNIQUE,
                               content_type    VARCHAR(120) NOT NULL,
                               tamanho_bytes   BIGINT       NOT NULL,
                               data_upload     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_anexo_chamado FOREIGN KEY (id_chamado)
                                   REFERENCES chamado (id_chamado) ON DELETE CASCADE,
                               CONSTRAINT fk_anexo_usuario FOREIGN KEY (id_usuario)
                                   REFERENCES usuario (id_usuario) ON DELETE RESTRICT,
                               CONSTRAINT chk_anexo_tamanho CHECK (tamanho_bytes > 0)
);

-- Índices
CREATE INDEX idx_usuario_departamento   ON usuario (id_departamento);
CREATE INDEX idx_chamado_status         ON chamado (status);
CREATE INDEX idx_chamado_prioridade     ON chamado (prioridade);
CREATE INDEX idx_chamado_categoria      ON chamado (id_categoria);
CREATE INDEX idx_chamado_solicitante    ON chamado (id_solicitante);
CREATE INDEX idx_chamado_responsavel    ON chamado (id_responsavel);
CREATE INDEX idx_chamado_data_abertura  ON chamado (data_abertura);
CREATE INDEX idx_chamado_titulo_trgm    ON chamado USING gin (titulo gin_trgm_ops);
CREATE INDEX idx_chamado_descricao_trgm ON chamado USING gin (descricao gin_trgm_ops);
CREATE INDEX idx_historico_chamado      ON historico_chamado (id_chamado);
CREATE INDEX idx_anexo_chamado          ON anexo_chamado (id_chamado);
CREATE INDEX idx_anexo_usuario          ON anexo_chamado (id_usuario);