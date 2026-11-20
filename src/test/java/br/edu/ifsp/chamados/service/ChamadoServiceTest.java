package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.dto.ChamadoAtualizacaoDTO;
import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.exception.RegraNegocioException;
import br.edu.ifsp.chamados.factory.ChamadoFactory;
import br.edu.ifsp.chamados.model.*;
import br.edu.ifsp.chamados.repository.*;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChamadoServiceTest {

    @Mock
    private ChamadoRepository chamadoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HistoricoChamadoRepository historicoRepository;

    private ChamadoService service;

    private Usuario alunoEntity;
    private Usuario tecnicoEntity;
    private Usuario gestorEntity;
    private UsuarioAutenticado alunoAuth;
    private UsuarioAutenticado tecnicoAuth;
    private UsuarioAutenticado gestorAuth;
    private Categoria categoriaTi;

    @BeforeEach
    void setUp() {
        service = new ChamadoService(
                chamadoRepository,
                categoriaRepository,
                usuarioRepository,
                historicoRepository,
                new ChamadoFactory()
        );

        Departamento ti = new Departamento();
        ti.setId(1L);
        ti.setNome("Tecnologia da Informação");
        ti.setSigla("TI");

        categoriaTi = new Categoria();
        categoriaTi.setId(10L);
        categoriaTi.setNome("Suporte técnico");
        categoriaTi.setPrioridadePadrao(Prioridade.ALTA);
        categoriaTi.setDepartamento(ti);

        alunoEntity = criarUsuario(1L, "Aluno Teste", "aluno@ifsp.edu.br", Papel.ALUNO, null);
        tecnicoEntity = criarUsuario(2L, "Técnico TI", "tecnico@ifsp.edu.br", Papel.TECNICO, ti);
        gestorEntity = criarUsuario(3L, "Gestor Geral", "gestor@ifsp.edu.br", Papel.GESTOR, null);

        alunoAuth = new UsuarioAutenticado(alunoEntity);
        tecnicoAuth = new UsuarioAutenticado(tecnicoEntity);
        gestorAuth = new UsuarioAutenticado(gestorEntity);
    }

    @Test
    @DisplayName("Deve abrir chamado atribuindo solicitante e último editor a partir do usuário autenticado")
    void deveCadastrarChamadoComUsuarioAutenticado() {
        ChamadoCadastroDTO dto = new ChamadoCadastroDTO();
        dto.setTitulo("Monitor não liga");
        dto.setDescricao("Bancada 02 sem sinal de vídeo.");
        dto.setCategoriaId(10L);

        when(categoriaRepository.findById(10L)).thenReturn(Optional.of(categoriaTi));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(alunoEntity));
        when(chamadoRepository.save(any(Chamado.class))).thenAnswer(inv -> {
            Chamado c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        Chamado salvo = service.cadastrar(dto, alunoAuth);

        assertEquals(100L, salvo.getId());
        assertEquals(Status.ABERTO, salvo.getStatus());
        assertEquals(Prioridade.ALTA, salvo.getPrioridade());
        assertEquals(alunoEntity, salvo.getSolicitante());
        assertEquals(alunoEntity, salvo.getUltimoEditor());
        verify(chamadoRepository).save(any(Chamado.class));
    }

    @Test
    @DisplayName("Deve impedir que um aluno visualize chamado aberto por outro usuário")
    void deveBloquearVisualizacaoDeChamadoDeTerceiroParaAluno() {
        Usuario outroAluno = criarUsuario(99L, "Outro Aluno", "outro@ifsp.edu.br", Papel.ALUNO, null);
        Chamado chamadoAlheio = new Chamado();
        chamadoAlheio.setId(50L);
        chamadoAlheio.setSolicitante(outroAluno);

        when(chamadoRepository.findById(50L)).thenReturn(Optional.of(chamadoAlheio));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.buscarComPermissao(50L, alunoAuth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("Deve exigir observação de atendimento ao concluir ou cancelar um chamado")
    void deveExigirObservacaoAoConcluirChamado() {
        Chamado existente = criarChamado(10L, 0L, alunoEntity);
        when(chamadoRepository.findById(10L)).thenReturn(Optional.of(existente));

        ChamadoAtualizacaoDTO dto = new ChamadoAtualizacaoDTO();
        dto.setVersao(0L);
        dto.setStatus(Status.CONCLUIDO);
        dto.setPrioridade(Prioridade.MEDIA);
        dto.setObservacaoAtendimento("   ");

        assertThrows(RegraNegocioException.class,
                () -> service.atualizar(10L, dto, tecnicoAuth));

        verify(chamadoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deve lançar OptimisticLockingFailureException quando a versão enviada divergir da atual")
    void deveDetectarConflitoDeConcorrenciaPorVersao() {
        Chamado existente = criarChamado(10L, 2L, alunoEntity);
        when(chamadoRepository.findById(10L)).thenReturn(Optional.of(existente));

        ChamadoAtualizacaoDTO dto = new ChamadoAtualizacaoDTO();
        dto.setVersao(1L); // Versão desatualizada
        dto.setStatus(Status.EM_ANALISE);
        dto.setPrioridade(Prioridade.ALTA);

        assertThrows(OptimisticLockingFailureException.class,
                () -> service.atualizar(10L, dto, tecnicoAuth));

        verify(chamadoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deve atualizar chamado com sucesso e registrar técnico como último editor")
    void deveAtualizarChamadoComSucesso() {
        Chamado existente = criarChamado(10L, 1L, alunoEntity);
        when(chamadoRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(tecnicoEntity));
        when(chamadoRepository.saveAndFlush(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        ChamadoAtualizacaoDTO dto = new ChamadoAtualizacaoDTO();
        dto.setVersao(1L);
        dto.setStatus(Status.CONCLUIDO);
        dto.setPrioridade(Prioridade.ALTA);
        dto.setResponsavelId(2L);
        dto.setObservacaoAtendimento("Cabo de força substituído.");

        Chamado atualizado = service.atualizar(10L, dto, tecnicoAuth);

        assertEquals(Status.CONCLUIDO, atualizado.getStatus());
        assertEquals("Cabo de força substituído.", atualizado.getObservacaoAtendimento());
        assertEquals(tecnicoEntity, atualizado.getResponsavel());
        assertEquals(tecnicoEntity, atualizado.getUltimoEditor());
    }

    @Test
    @DisplayName("Apenas gestor pode cancelar chamado via exclusão lógica preservando histórico")
    void devePermitirExclusaoApenasParaGestor() {
        Chamado existente = criarChamado(10L, 0L, alunoEntity);
        when(chamadoRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(gestorEntity));
        when(chamadoRepository.saveAndFlush(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseStatusException exTecnico = assertThrows(ResponseStatusException.class,
                () -> service.excluir(10L, tecnicoAuth));
        assertEquals(HttpStatus.FORBIDDEN, exTecnico.getStatusCode());

        assertDoesNotThrow(() -> service.excluir(10L, gestorAuth));
        assertEquals(Status.CANCELADO, existente.getStatus());
        assertEquals(gestorEntity, existente.getUltimoEditor());
        verify(chamadoRepository).saveAndFlush(existente);
        verify(chamadoRepository, never()).delete(any(Chamado.class));
    }

    private Usuario criarUsuario(Long id, String nome, String email, Papel papel, Departamento depto) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setEmail(email);
        u.setSenhaHash("$2a$10$hashficticio");
        u.setPapel(papel);
        u.setDepartamento(depto);
        u.setDataCadastro(LocalDate.now());
        return u;
    }

    private Chamado criarChamado(Long id, Long versao, Usuario solicitante) {
        Chamado c = new Chamado();
        c.setId(id);
        c.setTitulo("Chamado teste");
        c.setDescricao("Descrição teste");
        c.setStatus(Status.ABERTO);
        c.setPrioridade(Prioridade.MEDIA);
        c.setDataAbertura(LocalDate.now());
        c.setDataAtualizacao(LocalDate.now());
        c.setVersao(versao);
        c.setSolicitante(solicitante);
        c.setCategoria(categoriaTi);
        return c;
    }
}