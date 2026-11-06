package br.com.fiap.feedback.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Integração real com ScyllaDB Alternator (mesma imagem do docker-compose). */
class AvaliacaoRepositoryIT {

    private static GenericContainer<?> scylla;
    private static AvaliacaoRepository repositorio;

    @BeforeAll
    static void subirScylla() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker indisponível");
        scylla = new GenericContainer<>("scylladb/scylla:6.2")
                .withCommand("--smp=1", "--memory=512M", "--overprovisioned=1", "--developer-mode=1",
                        "--alternator-port=8000", "--alternator-address=0.0.0.0",
                        "--alternator-write-isolation=only_rmw_uses_lwt")
                .withExposedPorts(8000)
                .waitingFor(Wait.forHttp("/").forPort(8000).withStartupTimeout(Duration.ofMinutes(3)));
        scylla.start();

        DynamoDbClient db = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + scylla.getHost() + ":" + scylla.getMappedPort(8000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "y")))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        Clientes.garantirTabela(db, "avaliacoes", "PK", "SK");
        Clientes.garantirTabela(db, "avaliacoes", "PK", "SK"); // idempotente
        repositorio = new AvaliacaoRepository(db, "avaliacoes");
    }

    @AfterAll
    static void derrubar() {
        if (scylla != null) scylla.stop();
    }

    @Test
    void gravaEConsultaPorDiaNoFusoDeBrasilia() {
        // 02/11 23:30 em Brasília = 03/11 02:30 UTC -> deve cair na partição de 02/11
        var noite = new Avaliacao("a-3", "aluno-1", "Aula tarde da noite", 2, Urgencia.CRITICA,
                Instant.parse("2026-11-03T02:30:00Z"));
        var manha = new Avaliacao("a-1", "aluno-1", "Ótima aula", 9, Urgencia.BAIXA,
                Instant.parse("2026-11-02T12:00:00Z"));
        var tarde = new Avaliacao("a-2", "aluno-2", "Áudio ruim", 5, Urgencia.MODERADA,
                Instant.parse("2026-11-02T18:00:00Z"));
        var outroDia = new Avaliacao("a-4", "aluno-2", "Outro dia", 8, Urgencia.BAIXA,
                Instant.parse("2026-11-03T15:00:00Z"));
        List.of(noite, manha, tarde, outroDia).forEach(repositorio::salvar);

        List<Avaliacao> dia02 = repositorio.listarDoDia(LocalDate.parse("2026-11-02"));

        assertEquals(List.of(manha, tarde, noite), dia02, "ordenado pela SK (instante de envio)");
        assertEquals(List.of(outroDia), repositorio.listarDoDia(LocalDate.parse("2026-11-03")));
        assertEquals(List.of(), repositorio.listarDoDia(LocalDate.parse("2026-11-04")));
    }
}
