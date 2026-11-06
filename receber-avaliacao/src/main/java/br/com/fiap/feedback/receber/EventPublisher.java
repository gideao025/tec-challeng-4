package br.com.fiap.feedback.receber;

import br.com.fiap.feedback.commons.CloudEvent;

/**
 * Porta de saída para eventos de domínio. O domínio não conhece NATS: na AWS esta
 * interface seria substituída por DynamoDB Streams/EventBridge sem tocar em
 * {@link ReceberAvaliacao}.
 */
public interface EventPublisher {
    void publicar(CloudEvent evento);
}
