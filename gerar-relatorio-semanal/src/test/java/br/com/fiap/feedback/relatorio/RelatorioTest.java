package br.com.fiap.feedback.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.Urgencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RelatorioTest {

    private static final LocalDate SEGUNDA = LocalDate.parse("2026-11-02");
    private static final Instant GERADO = Instant.parse("2026-11-09T11:00:00Z");

    private static Avaliacao av(String id, int nota, Urgencia u, String instanteUtc) {
        return new Avaliacao(id, "aluno", "Aula " + id, nota, u, Instant.parse(instanteUtc));
    }

    private final List<Avaliacao> semana = List.of(
            av("a", 2, Urgencia.CRITICA, "2026-11-02T13:00:00Z"),
            av("b", 9, Urgencia.BAIXA, "2026-11-02T20:00:00Z"),
            // 04/11 01:00 UTC = 03/11 22:00 em Brasília
            av("c", 8, Urgencia.BAIXA, "2026-11-04T01:00:00Z"),
            av("d", 10, Urgencia.BAIXA, "2026-11-08T15:00:00Z"));

    @Test
    void contaPorDiaIncluindoDiasSemAvaliacao() {
        var r = Relatorio.consolidar(SEGUNDA, semana, GERADO);

        assertEquals(7, r.quantidadePorDia().size());
        assertEquals(Map.of(
                LocalDate.parse("2026-11-02"), 2L, LocalDate.parse("2026-11-03"), 1L,
                LocalDate.parse("2026-11-04"), 0L, LocalDate.parse("2026-11-05"), 0L,
                LocalDate.parse("2026-11-06"), 0L, LocalDate.parse("2026-11-07"), 0L,
                LocalDate.parse("2026-11-08"), 1L), r.quantidadePorDia());
        assertEquals(LocalDate.parse("2026-11-02"), r.quantidadePorDia().keySet().iterator().next(), "ordem cronológica");
    }

    @Test
    void contaPorUrgenciaIncluindoNiveisZerados() {
        var r = Relatorio.consolidar(SEGUNDA, semana, GERADO);

        assertEquals(Map.of(Urgencia.CRITICA, 1L, Urgencia.MODERADA, 0L, Urgencia.BAIXA, 3L), r.quantidadePorUrgencia());
        assertEquals(List.of(Urgencia.values()), List.copyOf(r.quantidadePorUrgencia().keySet()));
    }

    @Test
    void calculaMediaGeralComDuasCasas() {
        assertEquals(new BigDecimal("7.25"), Relatorio.consolidar(SEGUNDA, semana, GERADO).mediaNotas()); // 29/4
        var dizima = List.of(av("x", 2, Urgencia.CRITICA, "2026-11-02T13:00:00Z"),
                av("y", 5, Urgencia.MODERADA, "2026-11-02T14:00:00Z"),
                av("z", 9, Urgencia.BAIXA, "2026-11-02T15:00:00Z"));
        assertEquals(new BigDecimal("5.33"), Relatorio.consolidar(SEGUNDA, dizima, GERADO).mediaNotas());
    }

    @Test
    void listaContemDescricaoUrgenciaEDataDeEnvio() {
        var item = Relatorio.consolidar(SEGUNDA, semana, GERADO).avaliacoes().get(0);
        assertEquals("Aula a", item.descricao());
        assertEquals(Urgencia.CRITICA, item.urgencia());
        assertEquals(Instant.parse("2026-11-02T13:00:00Z"), item.dataEnvio());
        assertEquals("02/11/2026 10:00:00 (horário de Brasília)", item.dataEnvioBrasilia());
    }

    @Test
    void semanaVaziaGeraRelatorioValidoZerado() {
        var r = Relatorio.consolidar(SEGUNDA, List.of(), GERADO);

        assertEquals("2026-W45", r.semana());
        assertEquals(LocalDate.parse("2026-11-08"), r.fim());
        assertEquals(0, r.totalAvaliacoes());
        assertNull(r.mediaNotas());
        assertEquals("sem avaliações", r.mediaFormatada());
        assertTrue(r.quantidadePorDia().values().stream().allMatch(n -> n == 0) && r.quantidadePorDia().size() == 7);
        assertTrue(r.quantidadePorUrgencia().values().stream().allMatch(n -> n == 0) && r.quantidadePorUrgencia().size() == 3);
        assertTrue(r.paraCsv().contains("2026-W45,2026-11-02,2026-11-08,0,\r\n"));
        assertTrue(r.paraCsv().endsWith("id,data_envio_brasilia,urgencia,nota,descricao\r\n"));
    }

    @Test
    void csvEscapaAspasEVirgulas() {
        var r = Relatorio.consolidar(SEGUNDA, List.of(new Avaliacao("q", "al", "Disse \"ótima\", mas não foi", 4,
                Urgencia.MODERADA, Instant.parse("2026-11-05T12:00:00Z"))), GERADO);
        assertTrue(r.paraCsv().contains(",MODERADA,4,\"Disse \"\"ótima\"\", mas não foi\"\r\n"), r.paraCsv());
    }
}
