package br.com.fiap.feedback.relatorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.AvaliacaoRepository;
import br.com.fiap.feedback.commons.EnviadorEmail;
import br.com.fiap.feedback.commons.Urgencia;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GerarRelatorioSemanalTest {

    // Segunda-feira 09/11/2026 08:00 em Brasília (11:00 UTC), horário do cron
    private static final Clock SEGUNDA_8H = Clock.fixed(Instant.parse("2026-11-09T11:00:00Z"), ZoneOffset.UTC);
    private static final List<String> ADMINS = List.of("coordenacao@fiap.local");

    private final AvaliacaoRepository repositorio = mock(AvaliacaoRepository.class);
    private final ArmazenamentoRelatorios armazenamento = mock(ArmazenamentoRelatorios.class);
    private final EnviadorEmail email = mock(EnviadorEmail.class);
    private final GerarRelatorioSemanal casoDeUso = new GerarRelatorioSemanal(repositorio, armazenamento, email,
            ADMINS, "http://cloudlab:9001", SEGUNDA_8H);

    @Test
    void consultaExatamenteSeteDiasDaSemanaAnterior() {
        when(repositorio.listarDoDia(any())).thenReturn(List.of());

        casoDeUso.processar(null);

        var dias = ArgumentCaptor.forClass(LocalDate.class);
        verify(repositorio, times(7)).listarDoDia(dias.capture());
        assertEquals(LocalDate.parse("2026-11-02").datesUntil(LocalDate.parse("2026-11-09")).toList(), dias.getAllValues());
    }

    @Test
    void gravaCsvEJsonNoMinioEEnviaResumoPorEmail() {
        when(armazenamento.bucket()).thenReturn("feedback-relatorios");
        when(repositorio.listarDoDia(any())).thenReturn(List.of());
        when(repositorio.listarDoDia(LocalDate.parse("2026-11-03"))).thenReturn(List.of(
                new Avaliacao("a", "al", "Sem slides", 3, Urgencia.CRITICA, Instant.parse("2026-11-03T12:00:00Z")),
                new Avaliacao("b", "al", "Boa", 8, Urgencia.BAIXA, Instant.parse("2026-11-03T13:00:00Z"))));

        var r = casoDeUso.processar(null);

        assertEquals("2026-W45", r.semana());
        var json = ArgumentCaptor.forClass(String.class);
        verify(armazenamento).gravar(eq("relatorios/2026-W45.json"), json.capture(), anyString());
        verify(armazenamento).gravar(eq("relatorios/2026-W45.csv"), anyString(), anyString());
        assertTrue(json.getValue().contains("\"quantidadePorUrgencia\":{\"CRITICA\":1,\"MODERADA\":0,\"BAIXA\":1}"), json.getValue());
        assertTrue(json.getValue().contains("\"mediaNotas\":5.50"), json.getValue());

        var corpo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq(ADMINS), eq("Relatório semanal de avaliações - 2026-W45"), corpo.capture());
        String texto = corpo.getValue();
        assertTrue(texto.contains("Total de avaliações: 2"), texto);
        assertTrue(texto.contains("Média geral das notas: 5.50"), texto);
        assertTrue(texto.contains("MODERADA  0"), texto);
        assertTrue(texto.contains("2026-11-05 0"), texto);
        assertTrue(texto.contains("http://cloudlab:9001/browser/feedback-relatorios"), texto);
        assertTrue(texto.contains("relatorios/2026-W45.csv"), texto);
    }

    @Test
    void semanaVaziaNaoFalhaEGeraArquivosEEmail() {
        when(repositorio.listarDoDia(any())).thenReturn(List.of());

        var r = casoDeUso.processar(null);

        assertEquals(0, r.totalAvaliacoes());
        verify(armazenamento, times(2)).gravar(anyString(), anyString(), anyString());
        verify(email).enviar(eq(ADMINS), anyString(), any());
    }

    @Test
    void dataDeReferenciaExplicitaDefineOPeriodo() {
        when(repositorio.listarDoDia(any())).thenReturn(List.of());

        assertEquals(LocalDate.parse("2026-10-26"), casoDeUso.processar(LocalDate.parse("2026-11-02")).inicio());
    }
}
