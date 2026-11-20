package br.edu.ifsp.chamados.dto;

import br.edu.ifsp.chamados.model.Prioridade;
import br.edu.ifsp.chamados.model.Status;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChamadoAtualizacaoDTO {

    @NotNull(message = "Versão do registro não informada. Recarregue a página e tente novamente.")
    private Long versao;

    @NotNull(message = "Selecione o status.")
    private Status status;

    @NotNull(message = "Selecione a prioridade.")
    private Prioridade prioridade;

    private Long responsavelId;

    @Size(max = 500, message = "A observação pode ter no máximo 500 caracteres.")
    private String observacaoAtendimento;
}