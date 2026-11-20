package br.edu.ifsp.chamados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChamadoCadastroDTO {

        @NotBlank(message = "Informe o título do chamado.")
        @Size(max = 120, message = "O título pode ter no máximo 120 caracteres.")
        private String titulo;

        @NotBlank(message = "Informe a descrição do problema ou solicitação.")
        @Size(max = 500, message = "A descrição pode ter no máximo 500 caracteres.")
        private String descricao;

        @NotNull(message = "Selecione uma categoria.")
        private Long categoriaId;
}