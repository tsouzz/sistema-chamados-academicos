package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AberturaChamadoService {

    private final ChamadoService chamadoService;
    private final AnexoService anexoService;

    public AberturaChamadoService(ChamadoService chamadoService, AnexoService anexoService) {
        this.chamadoService = chamadoService;
        this.anexoService = anexoService;
    }

    @Transactional
    public Chamado abrirComAnexos(ChamadoCadastroDTO dto,
                                  MultipartFile[] arquivos,
                                  UsuarioAutenticado usuarioLogado) {
        anexoService.validarArquivos(arquivos);
        Chamado chamado = chamadoService.cadastrar(dto, usuarioLogado);
        anexoService.salvarAnexos(chamado.getId(), arquivos, usuarioLogado);
        return chamado;
    }
}
