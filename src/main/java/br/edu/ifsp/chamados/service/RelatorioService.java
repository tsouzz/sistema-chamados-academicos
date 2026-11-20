package br.edu.ifsp.chamados.service;

import br.edu.ifsp.chamados.model.Chamado;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RelatorioService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] gerarCsv(List<Chamado> chamados) {
        StringBuilder sb = new StringBuilder();
        // BOM UTF-8 para compatibilidade imediata com Excel em Português
        sb.append('\uFEFF');
        sb.append("ID;Título;Categoria;Setor;Status;Prioridade;Solicitante;Responsável;Abertura;Atualização\n");

        for (Chamado c : chamados) {
            sb.append(c.getId()).append(';')
                    .append(escaparCsv(c.getTitulo())).append(';')
                    .append(escaparCsv(c.getCategoria().getNome())).append(';')
                    .append(escaparCsv(c.getCategoria().getDepartamento().getSigla())).append(';')
                    .append(escaparCsv(c.getStatus().getRotulo())).append(';')
                    .append(escaparCsv(c.getPrioridade().getRotulo())).append(';')
                    .append(escaparCsv(c.getSolicitante().getNome())).append(';')
                    .append(escaparCsv(c.getResponsavel() != null ? c.getResponsavel().getNome() : "Não atribuído")).append(';')
                    .append(c.getDataAbertura().format(FMT_DATA)).append(';')
                    .append(c.getDataAtualizacao().format(FMT_DATA)).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] gerarPdf(List<Chamado> chamados, String nomeSolicitanteRelatorio) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 24);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(25, 65, 45));
        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        Font fontCell = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.BLACK);

        Paragraph titulo = new Paragraph("Relatório de Chamados Acadêmicos - IFSP", fontTitulo);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        Paragraph meta = new Paragraph(
                "Emitido por: " + nomeSolicitanteRelatorio +
                        "  |  Data: " + LocalDateTime.now().format(FMT_DATA_HORA) +
                        "  |  Total de registros: " + chamados.size(),
                fontSub
        );
        meta.setSpacingAfter(12);
        doc.add(meta);

        PdfPTable tabela = new PdfPTable(new float[]{0.8f, 3.2f, 2.0f, 1.5f, 1.3f, 2.2f, 2.2f, 1.4f});
        tabela.setWidthPercentage(100);

        String[] colunas = {"Nº", "Título", "Categoria / Setor", "Status", "Prioridade", "Solicitante", "Responsável", "Abertura"};
        Color corCabecalho = new Color(28, 92, 60);

        for (String col : colunas) {
            PdfPCell cell = new PdfPCell(new Phrase(col, fontHeader));
            cell.setBackgroundColor(corCabecalho);
            cell.setPadding(6);
            cell.setBorderColor(Color.LIGHT_GRAY);
            tabela.addCell(cell);
        }

        boolean zebra = false;
        Color corZebra = new Color(245, 248, 246);

        for (Chamado c : chamados) {
            Color bg = zebra ? corZebra : Color.WHITE;
            zebra = !zebra;

            adicionarCelula(tabela, "#" + c.getId(), fontCell, bg);
            adicionarCelula(tabela, c.getTitulo(), fontCell, bg);
            adicionarCelula(tabela, c.getCategoria().getNome() + " (" + c.getCategoria().getDepartamento().getSigla() + ")", fontCell, bg);
            adicionarCelula(tabela, c.getStatus().getRotulo(), fontCell, bg);
            adicionarCelula(tabela, c.getPrioridade().getRotulo(), fontCell, bg);
            adicionarCelula(tabela, c.getSolicitante().getNome(), fontCell, bg);
            adicionarCelula(tabela, c.getResponsavel() != null ? c.getResponsavel().getNome() : "Não atribuído", fontCell, bg);
            adicionarCelula(tabela, c.getDataAbertura().format(FMT_DATA), fontCell, bg);
        }

        if (chamados.isEmpty()) {
            PdfPCell vazio = new PdfPCell(new Phrase("Nenhum chamado encontrado para os filtros aplicados.", fontCell));
            vazio.setColspan(colunas.length);
            vazio.setPadding(10);
            vazio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabela.addCell(vazio);
        }

        doc.add(tabela);
        doc.close();
        return out.toByteArray();
    }

    private void adicionarCelula(PdfPTable tabela, String texto, Font fonte, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", fonte));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setBorderColor(new Color(220, 225, 222));
        tabela.addCell(cell);
    }

    private String escaparCsv(String valor) {
        if (valor == null) return "\"\"";
        return "\"" + valor.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}