package br.edu.ifsp.chamados.model;

public enum Status {
    ABERTO("Aberto", "aberto"),
    EM_ANALISE("Em análise", "analise"),
    AGUARDANDO_SOLICITANTE("Aguardando solicitante", "aguardando"),
    CONCLUIDO("Concluído", "concluido"),
    CANCELADO("Cancelado", "cancelado");

    private final String rotulo;
    private final String css;

    Status(String rotulo, String css) {
        this.rotulo = rotulo;
        this.css = css;
    }

    public String getRotulo() { return rotulo; }
    public String getCss() { return css; }
}
