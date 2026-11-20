package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.dto.IndicadorDepartamento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class RotinasBancoService {

    public static final int DIAS_INATIVIDADE = 15; // RF08

    private final JdbcTemplate jdbc;

    public RotinasBancoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<IndicadorDepartamento> indicadores() {
        return indicadores(null, null);
    }

    @Transactional(readOnly = true)
    public List<IndicadorDepartamento> indicadores(LocalDate inicio, LocalDate fim) {
        return jdbc.query("""
                SELECT departamento, nome_departamento, total_chamados, qtd_abertos,
                       qtd_em_analise, qtd_aguardando, qtd_concluidos, qtd_cancelados,
                       media_dias_conclusao, taxa_resolutividade
                FROM fn_indicadores(?, ?)
                """,
                (rs, i) -> new IndicadorDepartamento(
                        rs.getString("departamento"),
                        rs.getString("nome_departamento"),
                        rs.getLong("total_chamados"),
                        rs.getLong("qtd_abertos"),
                        rs.getLong("qtd_em_analise"),
                        rs.getLong("qtd_aguardando"),
                        rs.getLong("qtd_concluidos"),
                        rs.getLong("qtd_cancelados"),
                        rs.getBigDecimal("media_dias_conclusao"),
                        rs.getBigDecimal("taxa_resolutividade")),
                inicio, fim);
    }

    @Transactional(readOnly = true)
    public Integer diasResolucao(Long idChamado) {
        return jdbc.queryForObject("SELECT fn_dias_resolucao_chamado(?)", Integer.class, idChamado);
    }

    @Transactional
    public int encerrarInativos() {
        Integer qtd = jdbc.queryForObject("""
                SELECT COUNT(*) FROM chamado
                WHERE status = 'AGUARDANDO_SOLICITANTE'
                  AND data_atualizacao <= CURRENT_DATE - CAST(? AS INT)
                """, Integer.class, DIAS_INATIVIDADE);
        jdbc.update("CALL sp_encerrar_chamados_inativos(?)", DIAS_INATIVIDADE);
        return qtd == null ? 0 : qtd;
    }
}