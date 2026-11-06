package br.com.fiap.feedback.notificar;

import java.time.Instant;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Idempotência: registra o id da avaliação na tabela {@code notificacoes} com
 * escrita condicional (attribute_not_exists). Só quem consegue registrar envia o
 * e-mail, mesmo com reentregas do NATS ou invocações concorrentes.
 */
public class Deduplicador {

    private final DynamoDbClient db;
    private final String tabela;

    public Deduplicador(DynamoDbClient db, String tabela) {
        this.db = db;
        this.tabela = tabela;
    }

    /** @return true se o id ainda não havia sido processado (e agora está reservado). */
    public boolean registrarSeNovo(String avaliacaoId) {
        try {
            db.putItem(r -> r.tableName(tabela)
                    .item(Map.of("id", AttributeValue.fromS(avaliacaoId),
                            "processadoEm", AttributeValue.fromS(Instant.now().toString())))
                    .conditionExpression("attribute_not_exists(id)"));
            return true;
        } catch (ConditionalCheckFailedException jaProcessado) {
            return false;
        }
    }

    /** Desfaz a reserva quando o envio falha, permitindo nova tentativa. */
    public void liberar(String avaliacaoId) {
        db.deleteItem(r -> r.tableName(tabela).key(Map.of("id", AttributeValue.fromS(avaliacaoId))));
    }
}
