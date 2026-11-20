package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.dto.ChamadoAtualizacaoDTO;
import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.exception.RegraNegocioException;
import br.edu.ifsp.chamados.factory.ChamadoFactory;
import br.edu.ifsp.chamados.model.*;
import br.edu.ifsp.chamados.repository.*;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class ChamadoService {

    private final ChamadoRepository chamados;
    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final HistoricoChamadoRepository historicos;
    private final ChamadoFactory factory;

    public ChamadoService(ChamadoRepository chamados, CategoriaRepository categorias,
                          UsuarioRepository usuarios, HistoricoChamadoRepository historicos,
                          ChamadoFactory factory) {
        this.chamados = chamados;
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.historicos = historicos;
        this.factory = factory;
    }

    @Transactional(readOnly = true)
    public List<Chamado> listar(Status status, Long categoriaId, UsuarioAutenticado usuarioLogado) {
        Specification<Chamado> spec = (root, query, cb) -> cb.conjunction();

        if (usuarioLogado != null && !usuarioLogado.isAtendente()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("solicitante").get("id"), usuarioLogado.getId()));
        }

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoriaId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoria").get("id"), categoriaId));
        }
        return chamados.findAll(spec, Sort.by(Sort.Direction.DESC, "dataAbertura", "id"));
    }

    @Transactional(readOnly = true)
    public Chamado buscar(Long id) {
        return chamados.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chamado #" + id + " não encontrado."));
    }

    @Transactional(readOnly = true)
    public Chamado buscarComPermissao(Long id, UsuarioAutenticado usuarioLogado) {
        Chamado c = buscar(id);
        if (!usuarioLogado.isAtendente() && !c.getSolicitante().getId().equals(usuarioLogado.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para visualizar este chamado.");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<HistoricoChamado> historico(Long idChamado) {
        return historicos.findByIdChamadoOrderByDataModificacaoDescIdDesc(idChamado);
    }

    @Transactional
    public Chamado cadastrar(ChamadoCadastroDTO dto, UsuarioAutenticado usuarioLogado) {
        Categoria categoria = categorias.findById(dto.getCategoriaId())
                .orElseThrow(() -> new RegraNegocioException("A categoria selecionada não é válida."));
        Usuario solicitante = usuarios.findById(usuarioLogado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado."));
        return chamados.save(factory.criar(dto, categoria, solicitante));
    }

    @Transactional
    public Chamado atualizar(Long id, ChamadoAtualizacaoDTO dto, UsuarioAutenticado usuarioLogado) {
        if (!usuarioLogado.isAtendente()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas técnicos ou gestores podem atualizar chamados.");
        }
        Chamado c = buscar(id);

        if (!Objects.equals(c.getVersao(), dto.getVersao())) {
            throw new OptimisticLockingFailureException(
                    "Conflito de versão no chamado #" + id + ": esperado " + dto.getVersao() + ", atual " + c.getVersao());
        }

        if ((dto.getStatus() == Status.CONCLUIDO || dto.getStatus() == Status.CANCELADO)
                && (dto.getObservacaoAtendimento() == null || dto.getObservacaoAtendimento().isBlank())) {
            throw new RegraNegocioException("Informe uma observação de atendimento ao concluir ou cancelar um chamado.");
        }

        Usuario editor = usuarios.findById(usuarioLogado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado."));

        c.setStatus(dto.getStatus());
        c.setPrioridade(dto.getPrioridade());
        String obs = dto.getObservacaoAtendimento();
        c.setObservacaoAtendimento(obs == null || obs.isBlank() ? null : obs.trim());
        if (dto.getResponsavelId() == null) {
            c.setResponsavel(null);
        } else {
            c.setResponsavel(usuarios.findById(dto.getResponsavelId())
                    .orElseThrow(() -> new RegraNegocioException("O responsável selecionado não foi encontrado.")));
        }
        c.setUltimoEditor(editor);
        c.setDataAtualizacao(LocalDate.now());
        return chamados.saveAndFlush(c);
    }

    @Transactional
    public void excluir(Long id, UsuarioAutenticado usuarioLogado) {
        if (usuarioLogado.getPapel() != Papel.GESTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas gestores podem cancelar chamados.");
        }

        Chamado c = buscar(id);
        if (c.getStatus() == Status.CANCELADO) {
            throw new RegraNegocioException("O chamado #" + id + " já está cancelado.");
        }

        Usuario gestor = usuarios.findById(usuarioLogado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário autenticado não encontrado."));

        c.setStatus(Status.CANCELADO);
        c.setUltimoEditor(gestor);
        c.setDataAtualizacao(LocalDate.now());

        String notaCancelamento = "[Cancelado administrativamente pelo gestor]";
        String obsAtual = c.getObservacaoAtendimento();
        if (obsAtual == null || obsAtual.isBlank()) {
            c.setObservacaoAtendimento(notaCancelamento);
        } else if (!obsAtual.contains(notaCancelamento)) {
            String combinada = obsAtual.trim() + " " + notaCancelamento;
            c.setObservacaoAtendimento(combinada.length() > 500 ? combinada.substring(0, 500) : combinada);
        }

        chamados.saveAndFlush(c);
    }

    @Transactional(readOnly = true)
    public List<Categoria> categorias() {
        return categorias.findAllByOrderByNome();
    }

    @Transactional(readOnly = true)
    public List<Usuario> atendentes() {
        return usuarios.findByPapelInOrderByNome(List.of(Papel.TECNICO.name(), Papel.GESTOR.name()));
    }
}