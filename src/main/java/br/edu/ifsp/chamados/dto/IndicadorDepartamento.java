package br.edu.ifsp.chamados.dto;

import java.math.BigDecimal;

public record IndicadorDepartamento(
        String sigla,
        String nome,
        long total,
        long abertos,
        long emAnalise,
        long aguardando,
        long concluidos,
        long cancelados,
        BigDecimal mediaDias,
        BigDecimal taxaResolucao
) {}