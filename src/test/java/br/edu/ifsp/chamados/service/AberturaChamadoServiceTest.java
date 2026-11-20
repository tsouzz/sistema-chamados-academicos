package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.exception.RegraNegocioException;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.model.Papel;
import br.edu.ifsp.chamados.model.Usuario;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AberturaChamadoServiceTest {

    @Mock
    private ChamadoService chamadoService;

    @Mock
    private AnexoService anexoService;

    private AberturaChamadoService aberturaChamadoService;
    private UsuarioAutenticado usuarioAuth;
    private ChamadoCadastroDTO dto;

    @BeforeEach
    void setUp() {
        aberturaChamadoService = new AberturaChamadoService(chamadoService, anexoService);

        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Aluno Teste");
        usuario.setEmail("aluno@ifsp.edu.br");
        usuario.setSenhaHash("hash");
        usuario.setPapel(Papel.ALUNO);
        usuarioAuth = new UsuarioAutenticado(usuario);

        dto = new ChamadoCadastroDTO();
        dto.setTitulo("Projetor sem imagem");
        dto.setDescricao("Sala B102 sem sinal HDMI.");
        dto.setCategoriaId(1L);
    }

    @Test
    @DisplayName("Não deve cadastrar chamado quando houver anexo inválido")
    void naoDeveCadastrarChamadoQuandoAnexoForInvalido() {
        MockMultipartFile exe = new MockMultipartFile(
                "arquivos",
                "script.exe",
                "application/octet-stream",
                "MZ".getBytes()
        );
        MultipartFile[] arquivos = new MultipartFile[]{exe};

        doThrow(new RegraNegocioException("Formato de arquivo não permitido"))
                .when(anexoService).validarArquivos(arquivos);

        assertThrows(RegraNegocioException.class,
                () -> aberturaChamadoService.abrirComAnexos(dto, arquivos, usuarioAuth));

        verify(chamadoService, never()).cadastrar(any(), any());
        verify(anexoService, never()).salvarAnexos(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Deve validar anexos, cadastrar chamado e salvar anexos na ordem correta")
    void deveAbrirChamadoComAnexosValidos() {
        MockMultipartFile pdf = new MockMultipartFile(
                "arquivos",
                "foto.pdf",
                "application/pdf",
                "conteudo".getBytes()
        );
        MultipartFile[] arquivos = new MultipartFile[]{pdf};

        Chamado chamadoSalvo = new Chamado();
        chamadoSalvo.setId(42L);
        when(chamadoService.cadastrar(dto, usuarioAuth)).thenReturn(chamadoSalvo);

        Chamado resultado = aberturaChamadoService.abrirComAnexos(dto, arquivos, usuarioAuth);

        assertEquals(42L, resultado.getId());
        InOrder inOrder = inOrder(anexoService, chamadoService);
        inOrder.verify(anexoService).validarArquivos(arquivos);
        inOrder.verify(chamadoService).cadastrar(dto, usuarioAuth);
        inOrder.verify(anexoService).salvarAnexos(42L, arquivos, usuarioAuth);
    }
}
