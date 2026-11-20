package br.edu.ifsp.chamados.model;

public enum Prioridade {
    BAIXA("Baixa", "baixa"),
    MEDIA("Média", "media"),
    ALTA("Alta", "alta"),
    URGENTE("Urgente", "urgente");

    private final String rotulo;
    private final String css;

    Prioridade(String rotulo, String css) {
        this.rotulo = rotulo;
        this.css = css;
    }

    public String getRotulo() { return rotulo; }
    public String getCss() { return css; }
}
