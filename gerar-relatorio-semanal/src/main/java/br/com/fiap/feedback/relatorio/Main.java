package br.com.fiap.feedback.relatorio;

import br.com.fiap.feedback.commons.AvaliacaoRepository;
import br.com.fiap.feedback.commons.Clientes;
import br.com.fiap.feedback.commons.Config;
import br.com.fiap.feedback.commons.EnviadorEmail;
import br.com.fiap.feedback.commons.Servidor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Função gerar-relatorio-semanal: disparada pelo cron-connector (segunda 08:00 BRT).
 * Opcional para demonstração: ?data=AAAA-MM-DD define a data de referência.
 */
public final class Main {

    public static void main(String[] args) {
        var casoDeUso = new GerarRelatorioSemanal(
                AvaliacaoRepository.doAmbiente(Clientes.dynamo()),
                new ArmazenamentoRelatorios(Clientes.s3(), Config.obrigatorio("BUCKET_RELATORIOS")),
                EnviadorEmail.doAmbiente(),
                Config.lista("EMAIL_ADMINS"),
                Config.obrigatorio("MINIO_CONSOLE_URL"),
                Clock.systemUTC());

        io.javalin.http.Handler gerar = ctx -> {
            LocalDate referencia;
            try {
                String data = ctx.queryParam("data");
                referencia = data == null ? null : LocalDate.parse(data);
            } catch (DateTimeParseException e) {
                Servidor.json(ctx, 400, Map.of("erro", "DATA_INVALIDA", "mensagem", "Use ?data=AAAA-MM-DD"));
                return;
            }
            var r = casoDeUso.processar(referencia);
            Servidor.json(ctx, 200, Map.of("semana", r.semana(), "totalAvaliacoes", r.totalAvaliacoes(),
                    "mediaNotas", r.mediaFormatada(), "quantidadePorUrgencia", r.quantidadePorUrgencia()));
        };
        Servidor.iniciar(app -> app.post("/", gerar).get("/", gerar));
    }
}
