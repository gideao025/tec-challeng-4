package br.com.fiap.feedback.receber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.AvaliacaoRepository;
import br.com.fiap.feedback.commons.ClassificadorUrgencia;
import br.com.fiap.feedback.commons.CloudEvent;
import br.com.fiap.feedback.commons.Urgencia;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ReceberAvaliacaoTest {

    private static final Instant AGORA = Instant.parse("2026-11-04T13:15:30.123Z");

    private final AvaliacaoRepository repositorio = mock(AvaliacaoRepository.class);
    private final EventPublisher publicador = mock(EventPublisher.class);
    private final ReceberAvaliacao casoDeUso = new ReceberAvaliacao(repositorio, publicador,
            new ClassificadorUrgencia(3, 6), Clock.fixed(AGORA, ZoneOffset.UTC), 50);

    static String bearer(String payloadJson) {
        var b64 = Base64.getUrlEncoder().withoutPadding();
        return "Bearer " + b64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".assinatura";
    }

    private static final String ALUNO = bearer("""
            {"sub":"aluno-123","realm_access":{"roles":["aluno"]}}""");

    @Test
    void caminhoFelizClassificaPersisteEPublica() {
        var r = casoDeUso.processar("{\"descricao\":\"  Aula sem áudio  \",\"nota\":2}", ALUNO);

        assertEquals(201, r.status());
        @SuppressWarnings("unchecked") var corpo = (Map<String, Object>) r.corpo();
        assertEquals(Urgencia.CRITICA, corpo.get("urgencia"));
        assertEquals(AGORA, corpo.get("dataEnvio"));

        var salva = ArgumentCaptor.forClass(Avaliacao.class);
        verify(repositorio).salvar(salva.capture());
        assertEquals("aluno-123", salva.getValue().alunoId(), "alunoId vem do JWT");
        assertEquals("Aula sem áudio", salva.getValue().descricao());
        assertEquals(corpo.get("id"), salva.getValue().id());

        var evento = ArgumentCaptor.forClass(CloudEvent.class);
        verify(publicador).publicar(evento.capture());
        assertEquals("avaliacao.criada", evento.getValue().type());
        assertEquals("1.0", evento.getValue().specversion());
        assertEquals(Urgencia.CRITICA, evento.getValue().data().urgencia());
        assertNull(evento.getValue().data().alunoId(), "evento não carrega dado do aluno");
    }

    @ParameterizedTest
    @ValueSource(strings = {"5", "6"})
    void notaModeradaRetornaModerada(String nota) {
        var r = casoDeUso.processar("{\"descricao\":\"ok\",\"nota\":" + nota + "}", ALUNO);
        assertEquals(Urgencia.MODERADA, ((Map<?, ?>) r.corpo()).get("urgencia"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "nao-json", "[]", "{}",
        "{\"nota\":5}",
        "{\"descricao\":\"\",\"nota\":5}",
        "{\"descricao\":\"   \",\"nota\":5}",
        "{\"descricao\":123,\"nota\":5}",
        "{\"descricao\":\"ok\"}",
        "{\"descricao\":\"ok\",\"nota\":\"7\"}",
        "{\"descricao\":\"ok\",\"nota\":7.5}",
        "{\"descricao\":\"ok\",\"nota\":null}",
        "{\"descricao\":\"ok\",\"nota\":5,\"alunoId\":\"outro-aluno\"}",
        "{\"descricao\":\"ok\",\"nota\":5,\"urgencia\":\"BAIXA\"}",
        "{\"descricao\":\"este texto passa do limite de cinquenta caracteres!!\",\"nota\":5}"
    })
    void payloadInvalidoRetorna400Padronizado(String corpo) {
        var r = casoDeUso.processar(corpo, ALUNO);

        assertEquals(400, r.status());
        var erro = (Map<?, ?>) r.corpo();
        assertEquals("PAYLOAD_INVALIDO", erro.get("erro"));
        assertTrue(erro.containsKey("mensagem") && erro.containsKey("detalhes") && erro.containsKey("correlationId"));
        verifyNoInteractions(repositorio, publicador);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11, 100})
    void notaForaDoIntervaloRetorna400(int nota) {
        var r = casoDeUso.processar("{\"descricao\":\"ok\",\"nota\":" + nota + "}", ALUNO);

        assertEquals(400, r.status());
        assertEquals(List.of("nota: deve estar entre 0 e 10"), ((Map<?, ?>) r.corpo()).get("detalhes"));
        verify(repositorio, never()).salvar(any());
    }

    @Test
    void semTokenRetorna401() {
        assertEquals(401, casoDeUso.processar("{\"descricao\":\"ok\",\"nota\":5}", null).status());
        assertEquals(401, casoDeUso.processar("{\"descricao\":\"ok\",\"nota\":5}", "Bearer lixo").status());
        verifyNoInteractions(repositorio);
    }

    @Test
    void semRoleAlunoRetorna403() {
        var admin = bearer("{\"sub\":\"adm\",\"realm_access\":{\"roles\":[\"admin\"]}}");
        assertEquals(403, casoDeUso.processar("{\"descricao\":\"ok\",\"nota\":5}", admin).status());
        verifyNoInteractions(repositorio);
    }

    @Test
    void falhaAoPublicarNaoPerdeAAvaliacaoGravada() {
        doThrow(new IllegalStateException("NATS fora")).when(publicador).publicar(any());

        var r = casoDeUso.processar("{\"descricao\":\"ok\",\"nota\":1}", ALUNO);

        assertEquals(201, r.status());
        verify(repositorio).salvar(any());
    }
}
