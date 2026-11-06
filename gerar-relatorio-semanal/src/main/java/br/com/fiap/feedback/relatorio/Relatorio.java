package br.com.fiap.feedback.relatorio;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.Datas;
import br.com.fiap.feedback.commons.Urgencia;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relatório consolidado de uma semana (7 dias a partir de {@code inicio}).
 * Dias e níveis de urgência sem avaliações aparecem com zero; {@code mediaNotas}
 * é null quando a semana não tem avaliações (relatório vazio, porém válido).
 */
public record Relatorio(
        String semana,
        LocalDate inicio,
        LocalDate fim,
        Instant geradoEm,
        int totalAvaliacoes,
        BigDecimal mediaNotas,
        Map<LocalDate, Long> quantidadePorDia,
        Map<Urgencia, Long> quantidadePorUrgencia,
        List<Item> avaliacoes) {

    /** Linha do relatório: sem alunoId (minimização de dados - LGPD). */
    public record Item(String id, String descricao, Urgencia urgencia, int nota, Instant dataEnvio, String dataEnvioBrasilia) {}

    public static Relatorio consolidar(LocalDate inicio, List<Avaliacao> avaliacoes, Instant geradoEm) {
        LocalDate fim = inicio.plusDays(6);

        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) porDia.put(d, 0L);
        Map<Urgencia, Long> porUrgencia = new EnumMap<>(Urgencia.class);
        for (Urgencia u : Urgencia.values()) porUrgencia.put(u, 0L);

        int soma = 0;
        for (Avaliacao a : avaliacoes) {
            porDia.merge(Datas.diaEmBrasilia(a.dataEnvio()), 1L, Long::sum);
            porUrgencia.merge(a.urgencia(), 1L, Long::sum);
            soma += a.nota();
        }
        BigDecimal media = avaliacoes.isEmpty() ? null
                : BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(avaliacoes.size()), 2, RoundingMode.HALF_UP);

        List<Item> itens = avaliacoes.stream()
                .sorted(Comparator.comparing(Avaliacao::dataEnvio))
                .map(a -> new Item(a.id(), a.descricao(), a.urgencia(), a.nota(), a.dataEnvio(),
                        Datas.formatarBrasilia(a.dataEnvio())))
                .toList();

        return new Relatorio(rotulo(inicio), inicio, fim, geradoEm, avaliacoes.size(), media, porDia, porUrgencia, itens);
    }

    /** Semana ISO-8601 do primeiro dia, ex.: 2026-W44. */
    static String rotulo(LocalDate inicio) {
        return "%d-W%02d".formatted(inicio.get(IsoFields.WEEK_BASED_YEAR), inicio.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    public String mediaFormatada() {
        return mediaNotas == null ? "sem avaliações" : mediaNotas.toPlainString();
    }

    /** CSV (RFC 4180) em seções: resumo, por dia, por urgência e a lista de avaliações. */
    public String paraCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("semana,inicio,fim,total_avaliacoes,media_notas\r\n")
                .append(semana).append(',').append(inicio).append(',').append(fim).append(',')
                .append(totalAvaliacoes).append(',').append(mediaNotas == null ? "" : mediaNotas.toPlainString())
                .append("\r\n\r\n");
        csv.append("dia,quantidade\r\n");
        quantidadePorDia.forEach((dia, n) -> csv.append(dia).append(',').append(n).append("\r\n"));
        csv.append("\r\nurgencia,quantidade\r\n");
        quantidadePorUrgencia.forEach((u, n) -> csv.append(u).append(',').append(n).append("\r\n"));
        csv.append("\r\nid,data_envio_brasilia,urgencia,nota,descricao\r\n");
        for (Item i : avaliacoes) {
            csv.append(i.id()).append(',').append(campo(i.dataEnvioBrasilia())).append(',').append(i.urgencia())
                    .append(',').append(i.nota()).append(',').append(campo(i.descricao())).append("\r\n");
        }
        return csv.toString();
    }

    private static String campo(String valor) {
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
