package br.edu.ifsp.chamados.controller;

import br.edu.ifsp.chamados.service.RotinasBancoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class IndicadorController {

    private final RotinasBancoService rotinas;

    public IndicadorController(RotinasBancoService rotinas) {
        this.rotinas = rotinas;
    }

    @GetMapping("/indicadores")
    public String painel(Model model) {
        model.addAttribute("indicadores", rotinas.indicadores());
        model.addAttribute("diasInatividade", RotinasBancoService.DIAS_INATIVIDADE);
        return "indicadores/painel";
    }

    @PostMapping("/indicadores/encerrar-inativos")
    public String encerrarInativos(RedirectAttributes ra) {
        int qtd = rotinas.encerrarInativos();
        ra.addFlashAttribute("sucesso", qtd == 0
                ? "Nenhum chamado inativo para encerrar."
                : qtd + " chamado(s) encerrado(s) por inatividade.");
        return "redirect:/indicadores";
    }
}
