package br.com.fiap.feedback.receber;

import br.com.fiap.feedback.commons.CloudEvent;
import br.com.fiap.feedback.commons.Json;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Publica CloudEvents no NATS JetStream. O stream AVALIACOES retém os eventos
 * (replay/auditoria); o nats-connector do OpenFaaS assina o subject e invoca o
 * notificar-critico. Nats-Msg-Id = id do evento habilita a deduplicação do JetStream.
 * A conexão é aberta sob demanda: a função sobe mesmo com o NATS indisponível.
 */
public class NatsEventPublisher implements EventPublisher, AutoCloseable {

    static final String STREAM = "AVALIACOES";

    private final String url;
    private Connection conexao;
    private JetStream jetStream;

    public NatsEventPublisher(String url) {
        this.url = url;
    }

    private synchronized JetStream jetStream() throws IOException, InterruptedException {
        if (jetStream == null) {
            conexao = Nats.connect(new Options.Builder().server(url)
                    .connectionTimeout(Duration.ofSeconds(3)).maxReconnects(-1).build());
            garantirStream();
            jetStream = conexao.jetStream();
        }
        return jetStream;
    }

    private void garantirStream() throws IOException {
        var config = StreamConfiguration.builder()
                .name(STREAM)
                .subjects("avaliacao.>")
                .storageType(StorageType.File)
                .maxAge(Duration.ofDays(30))
                .duplicateWindow(Duration.ofMinutes(5))
                .build();
        var jsm = conexao.jetStreamManagement();
        try {
            jsm.getStreamInfo(STREAM);
        } catch (JetStreamApiException naoExiste) {
            try {
                jsm.addStream(config);
            } catch (JetStreamApiException e) {
                throw new IOException("Falha ao criar stream " + STREAM, e);
            }
        }
    }

    @Override
    public void publicar(CloudEvent evento) {
        Headers headers = new Headers()
                .add("Nats-Msg-Id", evento.id())
                .add("ce-type", evento.type())
                .add("Content-Type", "application/cloudevents+json");
        var msg = NatsMessage.builder()
                .subject(evento.type())
                .headers(headers)
                .data(Json.escrever(evento).getBytes(StandardCharsets.UTF_8))
                .build();
        try {
            jetStream().publish(msg); // aguarda o ack de persistência do JetStream
        } catch (IOException | JetStreamApiException e) {
            throw new IllegalStateException("Falha ao publicar " + evento.type(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação interrompida", e);
        }
    }

    @Override
    public synchronized void close() throws InterruptedException {
        if (conexao != null) conexao.close();
    }
}
