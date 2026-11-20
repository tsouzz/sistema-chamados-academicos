package br.edu.ifsp.chamados.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "historico_chamado")
@Getter @NoArgsConstructor
public class HistoricoChamado {

    @Id
    @Column(name = "id_historico")
    private Long id;

    @Column(name = "id_chamado", nullable = false)
    private Long idChamado;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", length = 30)
    private Status statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false, length = 30)
    private Status statusNovo;

    @Column(name = "data_modificacao", nullable = false)
    private LocalDateTime dataModificacao;

    @Column(length = 255)
    private String observacao;
}
