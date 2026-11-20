package br.edu.ifsp.chamados.factory;

import br.edu.ifsp.chamados.dto.ChamadoCadastroDTO;
import br.edu.ifsp.chamados.model.Categoria;
import br.edu.ifsp.chamados.model.Chamado;
import br.edu.ifsp.chamados.model.Status;
import br.edu.ifsp.chamados.model.Usuario;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ChamadoFactory {

    public Chamado criar(ChamadoCadastroDTO dto, Categoria categoria, Usuario solicitante) {
        LocalDate hoje = LocalDate.now();
        Chamado c = new Chamado();
        c.setTitulo(dto.getTitulo().trim());
        c.setDescricao(dto.getDescricao().trim());
        c.setCategoria(categoria);
        c.setSolicitante(solicitante);
        c.setUltimoEditor(solicitante);
        c.setPrioridade(categoria.getPrioridadePadrao());
        c.setStatus(Status.ABERTO);
        c.setDataAbertura(hoje);
        c.setDataAtualizacao(hoje);
        return c;
    }
}