package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.exception.RegraNegocioException;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.model.Papel;
import br.edu.ifsp.chamados.model.Usuario;
import br.edu.ifsp.chamados.repository.AnexoChamadoRepository;
import br.edu.ifsp.chamados.repository.UsuarioRepository;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnexoServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private AnexoChamadoRepository anexoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ChamadoService chamadoService;

    private AnexoService anexoService;
    private Usuario usuario;
    private UsuarioAutenticado usuarioAuth;
    private Chamado chamado;

    @BeforeEach
    void setUp() {
        anexoService = new AnexoService(tempDir.toString(), anexoRepository, usuarioRepository, chamadoService);
        anexoService.inicializarDiretorio();

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Aluno Teste");
        usuario.setEmail("aluno@ifsp.edu.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ALUNO);

        usuarioAuth = new UsuarioAutenticado(usuario);

        chamado = new Chamado();
        chamado.setId(15L);
        chamado.setSolicitante(usuario);
    }

    @Test
    @DisplayName("Deve salvar arquivo válido no diretório configurado e persistir metadados")
    void deveSalvarAnexoValido() throws Exception {
        when(chamadoService.buscarComPermissao(15L, usuarioAuth)).thenReturn(chamado);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(anexoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile pdf = new MockMultipartFile(
                "arquivos",
                "comprovante.pdf",
                "application/pdf",
                "conteudo-pdf-teste".getBytes()
        );

        anexoService.salvarAnexos(15L, new MultipartFile[]{pdf}, usuarioAuth);

        verify(anexoRepository, times(1)).save(any());
        try (var files = Files.list(tempDir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    @DisplayName("Deve rejeitar arquivos com extensão executável não permitida")
    void deveRejeitarExtensaoProibida() {
        when(chamadoService.buscarComPermissao(15L, usuarioAuth)).thenReturn(chamado);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        MockMultipartFile exe = new MockMultipartFile(
                "arquivos",
                "virus.exe",
                "application/octet-stream",
                "MZ".getBytes()
        );

        assertThrows(RegraNegocioException.class,
                () -> anexoService.salvarAnexos(15L, new MultipartFile[]{exe}, usuarioAuth));

        verify(anexoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve bloquear tentativa de Path Traversal no nome original do arquivo")
    void deveBloquearPathTraversal() {
        when(chamadoService.buscarComPermissao(15L, usuarioAuth)).thenReturn(chamado);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        MockMultipartFile malicioso = new MockMultipartFile(
                "arquivos",
                "../../etc/passwd.txt",
                "text/plain",
                "teste".getBytes()
        );

        assertThrows(RegraNegocioException.class,
                () -> anexoService.salvarAnexos(15L, new MultipartFile[]{malicioso}, usuarioAuth));

        verify(anexoRepository, never()).save(any());
    }
}