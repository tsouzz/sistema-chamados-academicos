package br.edu.ifsp.chamados.model;

public enum Papel {
    ALUNO("Aluno"),
    PROFESSOR("Professor"),
    TECNICO("Técnico"),
    GESTOR("Gestor");

    private final String rotulo;

    Papel(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}
