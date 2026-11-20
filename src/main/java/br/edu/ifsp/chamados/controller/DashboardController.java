package br.edu.ifsp.chamados.controller;

import br.edu.ifsp.chamados.dto.IndicadorDepartamento;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.model.Papel;
import br.edu.ifsp.chamados.model.Status;
import br.edu.ifsp.chamados.security.UsuarioAutenticado;
import br.edu.ifsp.chamados.service.ChamadoService;
import br.edu.ifsp.chamados.service.RotinasBancoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

@Controller
public class DashboardController {

    private static final Set<Status> STATUS_FINAIS = Set.of(Status.CONCLUIDO, Status.CANCELADO);

    private final ChamadoService chamadoService;
    private final RotinasBancoService rotinas;

    public DashboardController(ChamadoService chamadoService, RotinasBancoService rotinas) {
        this.chamadoService = chamadoService;
        this.rotinas = rotinas;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UsuarioAutenticado usuarioLogado, Model model) {
        if (usuarioLogado.getPapel() == Papel.GESTOR) {
            List<IndicadorDepartamento> indicadores = rotinas.indicadores();
            long totalGeral = indicadores.stream().mapToLong(IndicadorDepartamento::total).sum();
            long abertosGeral = indicadores.stream().mapToLong(IndicadorDepartamento::abertos).sum();
            long emAndamentoGeral = indicadores.stream()
                    .mapToLong(i -> i.emAnalise() + i.aguardando()).sum();
            long concluidosGeral = indicadores.stream().mapToLong(IndicadorDepartamento::concluidos).sum();
            long canceladosGeral = indicadores.stream().mapToLong(IndicadorDepartamento::cancelados).sum();

            model.addAttribute("indicadores", indicadores);
            model.addAttribute("totalGeral", totalGeral);
            model.addAttribute("abertosGeral", abertosGeral);
            model.addAttribute("emAndamentoGeral", emAndamentoGeral);
            model.addAttribute("concluidosGeral", concluidosGeral);
            model.addAttribute("canceladosGeral", canceladosGeral);
            return "dashboard/gestor";
        }

        if (usuarioLogado.getPapel() == Papel.TECNICO) {
            List<Chamado> todos = chamadoService.listar(null, null, usuarioLogado);

            List<Chamado> meusAtribuidos = todos.stream()
                    .filter(c -> c.getResponsavel() != null && c.getResponsavel().getId().equals(usuarioLogado.getId()))
                    .filter(c -> !STATUS_FINAIS.contains(c.getStatus()))
                    .toList();

            List<Chamado> filaSetorSemResponsavel = todos.stream()
                    .filter(c -> c.getResponsavel() == null)
                    .filter(c -> !STATUS_FINAIS.contains(c.getStatus()))
                    .filter(c -> usuarioLogado.getDepartamentoId() != null
                            && c.getCategoria().getDepartamento().getId().equals(usuarioLogado.getDepartamentoId()))
                    .toList();

            long concluidosPorMim = todos.stream()
                    .filter(c -> c.getResponsavel() != null && c.getResponsavel().getId().equals(usuarioLogado.getId()))
                    .filter(c -> c.getStatus() == Status.CONCLUIDO)
                    .count();

            model.addAttribute("meusAtribuidos", meusAtribuidos);
            model.addAttribute("filaSetor", filaSetorSemResponsavel);
            model.addAttribute("concluidosPorMim", concluidosPorMim);
            return "dashboard/tecnico";
        }

        // ALUNO ou PROFESSOR
        List<Chamado> meusChamados = chamadoService.listar(null, null, usuarioLogado);
        List<Chamado> emAberto = meusChamados.stream()
                .filter(c -> !STATUS_FINAIS.contains(c.getStatus()))
                .toList();
        List<Chamado> encerrados = meusChamados.stream()
                .filter(c -> STATUS_FINAIS.contains(c.getStatus()))
                .toList();

        long aguardandoAcao = emAberto.stream()
                .filter(c -> c.getStatus() == Status.AGUARDANDO_SOLICITANTE)
                .count();

        model.addAttribute("emAberto", emAberto);
        model.addAttribute("encerrados", encerrados);
        model.addAttribute("aguardandoAcao", aguardandoAcao);
        return "dashboard/solicitante";
    }
}