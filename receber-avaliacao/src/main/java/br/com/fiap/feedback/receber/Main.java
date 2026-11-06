package br.com.fiap.feedback.receber;

import br.com.fiap.feedback.commons.AvaliacaoRepository;
import br.com.fiap.feedback.commons.ClassificadorUrgencia;
import br.com.fiap.feedback.commons.Clientes;
import br.com.fiap.feedback.commons.Config;
import br.com.fiap.feedback.commons.Servidor;
import java.time.Clock;

/** Função receber-avaliacao: POST /avaliacao (via Kong -> gateway faasd -> of-watchdog). */
public final class Main {

    public static void main(String[] args) {
        var casoDeUso = new ReceberAvaliacao(
                AvaliacaoRepository.doAmbiente(Clientes.dynamo()),
                new NatsEventPublisher(Config.texto("NATS_URL", "nats://nats-js:4222")),
                ClassificadorUrgencia.doAmbiente(),
                Clock.systemUTC(),
                Config.inteiro("DESCRICAO_MAX", 1000));

        Servidor.iniciar(app -> app.post("/", ctx -> {
            var r = casoDeUso.processar(ctx.body(), ctx.header("Authorization"));
            Servidor.json(ctx, r.status(), r.corpo());
        }));
    }
}
