package br.com.fiap.feedback.notificar;

import br.com.fiap.feedback.commons.Clientes;
import br.com.fiap.feedback.commons.Config;
import br.com.fiap.feedback.commons.EnviadorEmail;
import br.com.fiap.feedback.commons.Servidor;
import java.util.Map;

/**
 * Função notificar-critico: invocada pelo nats-connector a cada mensagem no subject
 * avaliacao.criada (anotação topic no stack.yml). Corpo = CloudEvent JSON.
 */
public final class Main {

    public static void main(String[] args) {
        var db = Clientes.dynamo();
        String tabela = Config.texto("TABELA_NOTIFICACOES", "notificacoes");
        Clientes.garantirTabela(db, tabela, "id", null);
        var casoDeUso = new NotificarCritico(new Deduplicador(db, tabela), EnviadorEmail.doAmbiente(),
                Config.lista("EMAIL_ADMINS"));

        Servidor.iniciar(app -> app.post("/", ctx -> {
            try {
                Servidor.json(ctx, 200, Map.of("resultado", casoDeUso.processar(ctx.body())));
            } catch (IllegalArgumentException payloadInvalido) {
                Servidor.json(ctx, 400, Map.of("erro", "EVENTO_INVALIDO", "mensagem", payloadInvalido.getMessage()));
            }
        }));
    }
}
