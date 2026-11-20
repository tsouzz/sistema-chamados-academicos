package br.edu.ifsp.chamados.integration;

import br.edu.ifsp.chamados.dto.ChamadoAtualizacaoDTO;
import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.dto.IndicadorDepartamento;
import br.edu.ifsp.chamados.model.*;
import br.edu.ifsp.chamados.repository.CategoriaRepository;
import br.edu.ifsp.chamados.repository.ChamadoRepository;
import br.edu.ifsp.chamados.repository.HistoricoChamadoRepository;
import br.edu.ifsp.chamados.repository.UsuarioRepository;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import br.edu.ifsp.chamados.service.ChamadoService;
import br.edu.ifsp.chamados.service.RotinasBancoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class BancoPostgreSqlIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("chamados_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private ChamadoService chamadoService;

    @Autowired
    private RotinasBancoService rotinasBancoService;

    @Autowired
    private ChamadoRepository chamadoRepository;

    @Autowired
    private HistoricoChamadoRepository historicoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Triggers de conclusão e auditoria devem preencher data_conclusao e gravar histórico automaticamente")
    void deveAcionarTriggersDeConclusaoEAuditoriaNoPostgreSql() {
        Usuario aluno = usuarioRepository.findByEmailIgnoreCase("ewerton.monteiro@aluno.ifsp.edu.br").orElseThrow();
        Usuario tecnico = usuarioRepository.findByEmailIgnoreCase("dennis.olher@ifsp.edu.br").orElseThrow();
        Categoria categoria = categoriaRepository.findAllByOrderByNome().getFirst();

        UsuarioAutenticado alunoAuth = new UsuarioAutenticado(aluno);
        UsuarioAutenticado tecnicoAuth = new UsuarioAutenticado(tecnico);

        // 1. Abertura do chamado -> trigger deve inserir 1º registro no historico_chamado
        ChamadoCadastroDTO cadastro = new ChamadoCadastroDTO();
        cadastro.setTitulo("Teste integração trigger auditoria");
        cadastro.setDescricao("Verificando gravação automática de histórico no PostgreSQL.");
        cadastro.setCategoriaId(categoria.getId());

        Chamado criado = chamadoService.cadastrar(cadastro, alunoAuth);
        assertNotNull(criado.getId());

        List<HistoricoChamado> historicoInicial = chamadoService.historico(criado.getId());
        assertEquals(1, historicoInicial.size());
        assertNull(historicoInicial.getFirst().getStatusAnterior());
        assertEquals(Status.ABERTO, historicoInicial.getFirst().getStatusNovo());

        // 2. Conclusão do chamado -> trigger deve preencher data_conclusao e gravar 2º registro no histórico
        ChamadoAtualizacaoDTO conclusao = new ChamadoAtualizacaoDTO();
        conclusao.setVersao(criado.getVersao());
        conclusao.setStatus(Status.CONCLUIDO);
        conclusao.setPrioridade(criado.getPrioridade());
        conclusao.setResponsavelId(tecnico.getId());
        conclusao.setObservacaoAtendimento("Problema resolvido no laboratório.");

        Chamado concluido = chamadoService.atualizar(criado.getId(), conclusao, tecnicoAuth);
        assertEquals(Status.CONCLUIDO, concluido.getStatus());

        LocalDate dataConclusaoBanco = jdbcTemplate.queryForObject(
                "SELECT data_conclusao FROM chamado WHERE id_chamado = ?",
                LocalDate.class,
                criado.getId()
        );
        assertEquals(LocalDate.now(), dataConclusaoBanco);

        List<HistoricoChamado> historicoPosConclusao = chamadoService.historico(criado.getId());
        assertEquals(2, historicoPosConclusao.size());
        assertEquals(Status.ABERTO, historicoPosConclusao.getFirst().getStatusAnterior());
        assertEquals(Status.CONCLUIDO, historicoPosConclusao.getFirst().getStatusNovo());

        // 3. Reabertura do chamado -> trigger deve limpar data_conclusao para NULL
        ChamadoAtualizacaoDTO reabertura = new ChamadoAtualizacaoDTO();
        reabertura.setVersao(concluido.getVersao());
        reabertura.setStatus(Status.EM_ANALISE);
        reabertura.setPrioridade(concluido.getPrioridade());
        reabertura.setResponsavelId(tecnico.getId());
        reabertura.setObservacaoAtendimento("Solicitante informou recorrência.");

        chamadoService.atualizar(criado.getId(), reabertura, tecnicoAuth);

        LocalDate dataConclusaoAposReabertura = jdbcTemplate.queryForObject(
                "SELECT data_conclusao FROM chamado WHERE id_chamado = ?",
                LocalDate.class,
                criado.getId()
        );
        assertNull(dataConclusaoAposReabertura);
    }

    @Test
    @DisplayName("Trigger trg_historico_imutavel deve bloquear UPDATE e DELETE em historico_chamado via RAISE EXCEPTION")
    void deveImpedirAlteracaoOuExclusaoDeHistorico() {
        Long idHistorico = jdbcTemplate.queryForObject(
                "SELECT id_historico FROM historico_chamado LIMIT 1",
                Long.class
        );
        assertNotNull(idHistorico);

        DataAccessException exUpdate = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("UPDATE historico_chamado SET observacao = 'adulterado' WHERE id_historico = ?", idHistorico)
        );
        assertTrue(exUpdate.getMessage().contains("historico_chamado é imutável"));

        DataAccessException exDelete = assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("DELETE FROM historico_chamado WHERE id_historico = ?", idHistorico)
        );
        assertTrue(exDelete.getMessage().contains("historico_chamado é imutável"));
    }

    @Test
    @DisplayName("Constraint chk_usuario_tecnico_setor deve impedir técnico sem departamento no PostgreSQL")
    void deveValidarConstraintTecnicoSemSetor() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO usuario (nome, email, senha_hash, papel, id_departamento)
                        VALUES ('Técnico Inválido', 'invalido@ifsp.edu.br', '$2a$10$hash', 'TECNICO', NULL)
                        """)
        );
    }

    @Test
    @DisplayName("Stored procedure sp_encerrar_chamados_inativos deve cancelar chamados aguardando solicitante há >= 15 dias")
    void deveExecutarProcedureEncerramentoInativos() {
        // No V100 existe o chamado 'Dúvida sobre acesso ao Wi-Fi' com data_atualizacao = CURRENT_DATE - 18
        int encerrados = rotinasBancoService.encerrarInativos();
        assertTrue(encerrados >= 1);

        String statusChamadoAntigo = jdbcTemplate.queryForObject(
                "SELECT status FROM chamado WHERE titulo = 'Dúvida sobre acesso ao Wi-Fi'",
                String.class
        );
        assertEquals("CANCELADO", statusChamadoAntigo);

        // O chamado 'Acesso ao laboratório fora do horário' tem data_atualizacao = CURRENT_DATE - 5 e deve permanecer aguardando
        String statusChamadoRecente = jdbcTemplate.queryForObject(
                "SELECT status FROM chamado WHERE titulo = 'Acesso ao laboratório fora do horário'",
                String.class
        );
        assertEquals("AGUARDANDO_SOLICITANTE", statusChamadoRecente);
    }

    @Test
    @DisplayName("Função fn_indicadores e fn_dias_resolucao_chamado devem retornar métricas consistentes")
    void deveConsultarFuncoesAnaliticasDoBanco() {
        List<IndicadorDepartamento> indicadores = rotinasBancoService.indicadores();
        assertFalse(indicadores.isEmpty());
        assertTrue(indicadores.stream().anyMatch(i -> "TI".equals(i.sigla())));

        Long idChamado = jdbcTemplate.queryForObject("SELECT id_chamado FROM chamado LIMIT 1", Long.class);
        Integer dias = rotinasBancoService.diasResolucao(idChamado);
        assertNotNull(dias);
        assertTrue(dias >= 0);
    }

    @Test
    @DisplayName("Optimistic Locking deve impedir sobrescrita simultânea no banco real")
    void deveBloquearAtualizacaoConcorrenteNoBanco() {
        Usuario tecnico = usuarioRepository.findByEmailIgnoreCase("dennis.olher@ifsp.edu.br").orElseThrow();
        UsuarioAutenticado tecnicoAuth = new UsuarioAutenticado(tecnico);

        Chamado chamado = chamadoRepository.findAll().getFirst();
        Long versaoInicial = chamado.getVersao();

        ChamadoAtualizacaoDTO edicao1 = new ChamadoAtualizacaoDTO();
        edicao1.setVersao(versaoInicial);
        edicao1.setStatus(Status.EM_ANALISE);
        edicao1.setPrioridade(Prioridade.URGENTE);
        edicao1.setResponsavelId(tecnico.getId());
        edicao1.setObservacaoAtendimento("Primeiro técnico assumiu.");

        Chamado aposEdicao1 = chamadoService.atualizar(chamado.getId(), edicao1, tecnicoAuth);
        assertEquals(versaoInicial + 1, aposEdicao1.getVersao());

        ChamadoAtualizacaoDTO edicao2Desatualizada = new ChamadoAtualizacaoDTO();
        edicao2Desatualizada.setVersao(versaoInicial); // Ainda usa a versão antiga
        edicao2Desatualizada.setStatus(Status.ABERTO);
        edicao2Desatualizada.setPrioridade(Prioridade.BAIXA);

        assertThrows(OptimisticLockingFailureException.class,
                () -> chamadoService.atualizar(chamado.getId(), edicao2Desatualizada, tecnicoAuth));
    }
}