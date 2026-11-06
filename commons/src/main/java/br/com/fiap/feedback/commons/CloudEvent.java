package br.com.fiap.feedback.commons;

import java.time.Instant;
import java.util.UUID;

/**
 * CloudEvents 1.0 em modo estruturado (JSON). {@code correlationid} é uma extensão
 * que leva o X-Correlation-ID do Kong até os assinantes.
 */
public record CloudEvent(
        String specversion,
        String id,
        String source,
        String type,
        Instant time,
        String datacontenttype,
        String correlationid,
        Avaliacao data) {

    public static final String AVALIACAO_CRIADA = "avaliacao.criada";

    /** O evento não carrega o alunoId (minimização de dados - LGPD): os assinantes não precisam dele. */
    public static CloudEvent avaliacaoCriada(Avaliacao a, String correlationId) {
        var semAluno = new Avaliacao(a.id(), null, a.descricao(), a.nota(), a.urgencia(), a.dataEnvio());
        return new CloudEvent("1.0", UUID.randomUUID().toString(), "/funcoes/receber-avaliacao",
                AVALIACAO_CRIADA, Instant.now(), "application/json", correlationId, semAluno);
    }
}
