package br.com.fiap.feedback.notificar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.CloudEvent;
import br.com.fiap.feedback.commons.EnviadorEmail;
import br.com.fiap.feedback.commons.Json;
import br.com.fiap.feedback.commons.Urgencia;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class NotificarCriticoTest {

    private static final List<String> ADMINS = List.of("coordenacao@fiap.local");

    private final Deduplicador deduplicador = mock(Deduplicador.class);
    private final EnviadorEmail email = mock(EnviadorEmail.class);
    private final NotificarCritico casoDeUso = new NotificarCritico(deduplicador, email, ADMINS);

    private static String evento(String id, Urgencia urgencia) {
        // 13:15:30Z = 10:15:30 em Brasília (UTC-3)
        var a = new Avaliacao(id, null, "Professor não apareceu", 1, urgencia, Instant.parse("2026-11-04T13:15:30Z"));
        return Json.escrever(CloudEvent.avaliacaoCriada(a, "corr-1"));
    }

    @Test
    void notificaCriticaComDescricaoUrgenciaEDataEmBrasilia() {
        when(deduplicador.registrarSeNovo("a-1")).thenReturn(true);

        assertEquals(NotificarCritico.Resultado.NOTIFICADO, casoDeUso.processar(evento("a-1", Urgencia.CRITICA)));

        var corpo = ArgumentCaptor.forClass(String.class);
        verify(email).enviar(eq(ADMINS), anyString(), corpo.capture());
        String texto = corpo.getValue();
        assertTrue(texto.contains("Descrição:      Professor não apareceu"), texto);
        assertTrue(texto.contains("Urgência:       CRITICA"), texto);
        assertTrue(texto.contains("Data de envio:  04/11/2026 10:15:30 (horário de Brasília)"), texto);
    }

    @ParameterizedTest
    @EnumSource(value = Urgencia.class, names = {"MODERADA", "BAIXA"})
    void naoNotificaUrgenciasNaoCriticas(Urgencia urgencia) {
        assertEquals(NotificarCritico.Resultado.IGNORADO, casoDeUso.processar(evento("a-2", urgencia)));
        verifyNoInteractions(email, deduplicador);
    }

    @Test
    void ehIdempotentePeloIdDaAvaliacao() {
        when(deduplicador.registrarSeNovo("a-3")).thenReturn(true, false, false);

        casoDeUso.processar(evento("a-3", Urgencia.CRITICA));
        assertEquals(NotificarCritico.Resultado.DUPLICADO, casoDeUso.processar(evento("a-3", Urgencia.CRITICA)));
        casoDeUso.processar(evento("a-3", Urgencia.CRITICA));

        verify(email, times(1)).enviar(any(), anyString(), anyString());
    }

    @Test
    void falhaNoEnvioLiberaParaNovaTentativa() {
        when(deduplicador.registrarSeNovo("a-4")).thenReturn(true);
        doThrow(new IllegalStateException("SMTP fora")).when(email).enviar(any(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> casoDeUso.processar(evento("a-4", Urgencia.CRITICA)));
        verify(deduplicador).liberar("a-4");
    }

    @Test
    void eventoMalformadoEhRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> casoDeUso.processar("{nao-json"));
        verify(email, never()).enviar(any(), anyString(), anyString());
    }
}
