package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.exception.RegraNegocioException;
import br.edu.ifsp.chamados.model.AnexoChamado;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.model.Papel;
import br.edu.ifsp.chamados.model.Usuario;
import br.edu.ifsp.chamados.repository.AnexoChamadoRepository;
import br.edu.ifsp.chamados.repository.UsuarioRepository;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnexoService {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of(
            "pdf", "png", "jpg", "jpeg", "txt", "log", "doc", "docx", "xlsx", "csv", "zip"
    );

    private final Path diretorioRaiz;
    private final AnexoChamadoRepository anexos;
    private final UsuarioRepository usuarios;
    private final ChamadoService chamadoService;

    public AnexoService(@Value("${app.upload.dir}") String uploadDir,
                        AnexoChamadoRepository anexos,
                        UsuarioRepository usuarios,
                        ChamadoService chamadoService) {
        this.diretorioRaiz = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.anexos = anexos;
        this.usuarios = usuarios;
        this.chamadoService = chamadoService;
    }

    @PostConstruct
    public void inicializarDiretorio() {
        try {
            Files.createDirectories(diretorioRaiz);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar o diretório de uploads: " + diretorioRaiz, e);
        }
    }

    @Transactional(readOnly = true)
    public List<AnexoChamado> listarPorChamado(Long chamadoId) {
        return anexos.findByChamadoIdWithUsuario(chamadoId);
    }

    public void validarArquivos(MultipartFile[] arquivos) {
        if (arquivos == null || arquivos.length == 0) {
            return;
        }
        for (MultipartFile arquivo : arquivos) {
            if (arquivo == null || arquivo.isEmpty()) {
                continue;
            }
            validarArquivo(arquivo);
        }
    }

    @Transactional
    public void salvarAnexos(Long chamadoId, MultipartFile[] arquivos, UsuarioAutenticado usuarioLogado) {
        if (arquivos == null || arquivos.length == 0) {
            return;
        }
        Chamado chamado = chamadoService.buscarComPermissao(chamadoId, usuarioLogado);
        Usuario autor = usuarios.findById(usuarioLogado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));

        validarArquivos(arquivos);

        List<Path> arquivosGravados = new ArrayList<>();
        try {
            for (MultipartFile arquivo : arquivos) {
                if (arquivo == null || arquivo.isEmpty()) {
                    continue;
                }
                salvarArquivoUnico(chamado, autor, arquivo, arquivosGravados);
            }
        } catch (RuntimeException ex) {
            removerArquivosSilenciosamente(arquivosGravados);
            throw ex;
        }
    }

    private ValidacaoArquivo validarArquivo(MultipartFile arquivo) {
        String nomeLimpo = StringUtils.cleanPath(
                arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo"
        );
        if (nomeLimpo.contains("..")) {
            throw new RegraNegocioException("Nome de arquivo inválido: " + nomeLimpo);
        }

        String extensao = extrairExtensao(nomeLimpo);
        if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
            throw new RegraNegocioException(
                    "Formato de arquivo não permitido (." + extensao + "). Envie PDF, imagens, documentos Office, TXT ou ZIP."
            );
        }
        return new ValidacaoArquivo(nomeLimpo, extensao);
    }

    private void salvarArquivoUnico(Chamado chamado, Usuario autor, MultipartFile arquivo, List<Path> arquivosGravados) {
        ValidacaoArquivo validacao = validarArquivo(arquivo);

        String nomeArmazenado = UUID.randomUUID() + "." + validacao.extensao();
        Path destino = diretorioRaiz.resolve(nomeArmazenado).normalize();

        if (!destino.startsWith(diretorioRaiz)) {
            throw new RegraNegocioException("Caminho de destino inválido.");
        }

        try (InputStream in = arquivo.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            arquivosGravados.add(destino);
        } catch (IOException e) {
            throw new RegraNegocioException("Falha ao gravar o arquivo no servidor: " + validacao.nomeLimpo());
        }

        AnexoChamado entidade = new AnexoChamado();
        entidade.setChamado(chamado);
        entidade.setEnviadoPor(autor);
        entidade.setNomeOriginal(validacao.nomeLimpo().length() > 255 ? validacao.nomeLimpo().substring(0, 255) : validacao.nomeLimpo());
        entidade.setNomeArmazenado(nomeArmazenado);
        entidade.setContentType(arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream");
        entidade.setTamanhoBytes(arquivo.getSize());
        entidade.setDataUpload(LocalDateTime.now());
        anexos.save(entidade);
    }

    private void removerArquivosSilenciosamente(List<Path> arquivosGravados) {
        for (Path caminho : arquivosGravados) {
            try {
                Files.deleteIfExists(caminho);
            } catch (IOException ignored) {
            }
        }
    }

    private record ValidacaoArquivo(String nomeLimpo, String extensao) {}

    @Transactional(readOnly = true)
    public AnexoDownload carregarParaDownload(Long chamadoId, Long anexoId, UsuarioAutenticado usuarioLogado) {
        chamadoService.buscarComPermissao(chamadoId, usuarioLogado);

        AnexoChamado anexo = anexos.findById(anexoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo não encontrado."));

        if (!anexo.getChamado().getId().equals(chamadoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O anexo não pertence ao chamado informado.");
        }

        Path caminho = diretorioRaiz.resolve(anexo.getNomeArmazenado()).normalize();
        if (!caminho.startsWith(diretorioRaiz) || !Files.exists(caminho)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo físico não encontrado no servidor.");
        }

        try {
            Resource resource = new UrlResource(caminho.toUri());
            return new AnexoDownload(anexo, resource);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao ler arquivo.");
        }
    }

    @Transactional
    public void excluir(Long chamadoId, Long anexoId, UsuarioAutenticado usuarioLogado) {
        chamadoService.buscarComPermissao(chamadoId, usuarioLogado);

        AnexoChamado anexo = anexos.findById(anexoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo não encontrado."));

        if (!anexo.getChamado().getId().equals(chamadoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O anexo não pertence ao chamado informado.");
        }

        boolean podeExcluir = usuarioLogado.getPapel() == Papel.GESTOR
                || anexo.getEnviadoPor().getId().equals(usuarioLogado.getId());

        if (!podeExcluir) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode excluir anexos enviados por você.");
        }

        Path caminho = diretorioRaiz.resolve(anexo.getNomeArmazenado()).normalize();
        anexos.delete(anexo);
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException ignored) {
        }
    }

    private String extrairExtensao(String nomeArquivo) {
        int ponto = nomeArquivo.lastIndexOf('.');
        if (ponto < 0 || ponto == nomeArquivo.length() - 1) {
            return "";
        }
        return nomeArquivo.substring(ponto + 1).toLowerCase();
    }

    public record AnexoDownload(AnexoChamado metadados, Resource resource) {}
}