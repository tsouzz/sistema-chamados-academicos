package br.edu.ifsp.chamados.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categoria")
@Getter @Setter @NoArgsConstructor
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade_padrao", nullable = false, length = 20)
    private Prioridade prioridadePadrao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_departamento", nullable = false)
    private Departamento departamento;
}
