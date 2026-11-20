package br.edu.ifsp.chamados.controller;

import br.edu.ifsp.chamados.dto.ChamadoAtualizacaoDTO;
import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.model.Prioridade;
import br.edu.ifsp.chamados.model.Status;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import br.edu.ifsp.chamados.service.AberturaChamadoService;
import br.edu.ifsp.chamados.service.AnexoService;
import br.edu.ifsp.chamados.service.ChamadoService;
import br.edu.ifsp.chamados.service.RelatorioService;
import br.edu.ifsp.chamados.service.RotinasBancoService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
public class ChamadoController {

    private final ChamadoService service;
    private final AberturaChamadoService aberturaChamadoService;
    private final RotinasBancoService rotinas;
    private final AnexoService anexoService;
    private final RelatorioService relatorioService;

    public ChamadoController(ChamadoService service,
                             AberturaChamadoService aberturaChamadoService,
                             RotinasBancoService rotinas,
                             AnexoService anexoService,
                             RelatorioService relatorioService) {
        this.service = service;
        this.aberturaChamadoService = aberturaChamadoService;
        this.rotinas = rotinas;
        this.anexoService = anexoService;
        this.relatorioService = relatorioService;
    }

    @GetMapping("/")
    public String inicio() {
        return "redirect:/dashboard";
    }

    @GetMapping("/chamados")
    public String listar(@RequestParam(required = false) Status status,
                         @RequestParam(required = false) Long categoriaId,
                         @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                         Model model) {
        model.addAttribute("chamados", service.listar(status, categoriaId, usuarioLogado));
        model.addAttribute("categorias", service.categorias());
        model.addAttribute("statusValores", Status.values());
        model.addAttribute("statusSel", status);
        model.addAttribute("categoriaSel", categoriaId);
        return "chamados/lista";
    }

    @GetMapping("/chamados/exportar/csv")
    public ResponseEntity<byte[]> exportarCsv(@RequestParam(required = false) Status status,
                                              @RequestParam(required = false) Long categoriaId,
                                              @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        List<Chamado> lista = service.listar(status, categoriaId, usuarioLogado);
        byte[] conteudo = relatorioService.gerarCsv(lista);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("chamados-" + LocalDate.now() + ".csv", StandardCharsets.UTF_8)
                .build());
        return new ResponseEntity<>(conteudo, headers, HttpStatus.OK);
    }

    @GetMapping("/chamados/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdf(@RequestParam(required = false) Status status,
                                              @RequestParam(required = false) Long categoriaId,
                                              @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        List<Chamado> lista = service.listar(status, categoriaId, usuarioLogado);
        byte[] conteudo = relatorioService.gerarPdf(lista, usuarioLogado.getNome());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("chamados-" + LocalDate.now() + ".pdf", StandardCharsets.UTF_8)
                .build());
        return new ResponseEntity<>(conteudo, headers, HttpStatus.OK);
    }

    @GetMapping("/chamados/novo")
    public String novo(Model model) {
        model.addAttribute("dto", new ChamadoCadastroDTO());
        model.addAttribute("categorias", service.categorias());
        return "chamados/novo";
    }

    @PostMapping("/chamados")
    public String salvar(@Valid @ModelAttribute("dto") ChamadoCadastroDTO dto,
                         BindingResult result,
                         @RequestParam(value = "arquivos", required = false) MultipartFile[] arquivos,
                         @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("categorias", service.categorias());
            return "chamados/novo";
        }
        Chamado c = aberturaChamadoService.abrirComAnexos(dto, arquivos, usuarioLogado);
        ra.addFlashAttribute("sucesso", "Chamado #" + c.getId() + " aberto.");
        return "redirect:/chamados/" + c.getId();
    }

    @GetMapping("/chamados/{id}")
    public String detalhe(@PathVariable Long id,
                          @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                          Model model) {
        model.addAttribute("chamado", service.buscarComPermissao(id, usuarioLogado));
        model.addAttribute("historico", service.historico(id));
        model.addAttribute("anexos", anexoService.listarPorChamado(id));
        model.addAttribute("diasDecorridos", rotinas.diasResolucao(id));
        return "chamados/detalhe";
    }

    @PostMapping("/chamados/{id}/anexos")
    public String enviarAnexos(@PathVariable Long id,
                               @RequestParam("arquivos") MultipartFile[] arquivos,
                               @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                               RedirectAttributes ra) {
        anexoService.salvarAnexos(id, arquivos, usuarioLogado);
        ra.addFlashAttribute("sucesso", "Anexo(s) enviado(s) com sucesso.");
        return "redirect:/chamados/" + id;
    }

    @GetMapping("/chamados/{id}/anexos/{anexoId}/download")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable Long id,
                                                @PathVariable Long anexoId,
                                                @AuthenticationPrincipal UsuarioAutenticado usuarioLogado) {
        AnexoService.AnexoDownload download = anexoService.carregarParaDownload(id, anexoId, usuarioLogado);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(download.metadados().getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.metadados().getNomeOriginal(), StandardCharsets.UTF_8)
                .build());

        return new ResponseEntity<>(download.resource(), headers, HttpStatus.OK);
    }

    @PostMapping("/chamados/{id}/anexos/{anexoId}/excluir")
    public String excluirAnexo(@PathVariable Long id,
                               @PathVariable Long anexoId,
                               @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                               RedirectAttributes ra) {
        anexoService.excluir(id, anexoId, usuarioLogado);
        ra.addFlashAttribute("sucesso", "Anexo removido.");
        return "redirect:/chamados/" + id;
    }

    @GetMapping("/chamados/{id}/editar")
    public String editar(@PathVariable Long id,
                         @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                         Model model) {
        Chamado c = service.buscarComPermissao(id, usuarioLogado);
        ChamadoAtualizacaoDTO dto = new ChamadoAtualizacaoDTO();
        dto.setVersao(c.getVersao());
        dto.setStatus(c.getStatus());
        dto.setPrioridade(c.getPrioridade());
        dto.setObservacaoAtendimento(c.getObservacaoAtendimento());
        dto.setResponsavelId(c.getResponsavel() == null ? null : c.getResponsavel().getId());
        model.addAttribute("chamado", c);
        model.addAttribute("dto", dto);
        carregarCombosEdicao(model);
        return "chamados/editar";
    }

    @PostMapping("/chamados/{id}/editar")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("dto") ChamadoAtualizacaoDTO dto,
                            BindingResult result,
                            @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                            Model model,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("chamado", service.buscarComPermissao(id, usuarioLogado));
            carregarCombosEdicao(model);
            return "chamados/editar";
        }
        service.atualizar(id, dto, usuarioLogado);
        ra.addFlashAttribute("sucesso", "Chamado #" + id + " atualizado.");
        return "redirect:/chamados/" + id;
    }

    @PostMapping("/chamados/{id}/excluir")
    public String excluir(@PathVariable Long id,
                          @AuthenticationPrincipal UsuarioAutenticado usuarioLogado,
                          RedirectAttributes ra) {
        service.excluir(id, usuarioLogado);
        ra.addFlashAttribute("sucesso", "Chamado #" + id + " cancelado com sucesso.");
        return "redirect:/chamados/" + id;
    }

    private void carregarCombosEdicao(Model model) {
        model.addAttribute("statusValores", Status.values());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("atendentes", service.atendentes());
    }
}