package br.com.fiap.feedback.commons;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Servidor HTTP embarcado (Javalin) atrás do of-watchdog em modo HTTP: o watchdog
 * recebe na porta 8080 e repassa para 127.0.0.1:8082. Padroniza correlationId,
 * health check e resposta de erro interno para as três funções.
 */
public final class Servidor {

    private Servidor() {}

    public static Javalin iniciar(Consumer<Javalin> rotas) {
        Javalin app = Javalin.create(cfg -> cfg.showJavalinBanner = false);
        app.before(ctx -> {
            String id = ctx.header(Log.HEADER_CORRELACAO);
            Log.correlacao(id == null || id.isBlank() ? UUID.randomUUID().toString() : id);
            ctx.header(Log.HEADER_CORRELACAO, Log.correlacao());
        });
        app.after(ctx -> Log.correlacao(null));
        app.get("/health", ctx -> ctx.result("ok"));
        app.exception(Exception.class, (e, ctx) -> {
            Log.erro("erro_interno", e, "path", ctx.path());
            json(ctx, 500, Map.of("erro", "ERRO_INTERNO",
                    "mensagem", "Erro interno. Informe o correlationId ao suporte.",
                    "correlationId", String.valueOf(Log.correlacao())));
        });
        rotas.accept(app);
        return app.start(Config.inteiro("PORTA", 8082));
    }

    public static void json(Context ctx, int status, Object corpo) {
        ctx.status(status).contentType("application/json; charset=utf-8").result(Json.escrever(corpo));
    }
}
