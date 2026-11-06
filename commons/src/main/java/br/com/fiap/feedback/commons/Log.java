package br.com.fiap.feedback.commons;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Log estruturado: uma linha JSON por evento no stdout (coletado do journald pelo Alloy
 * e enviado ao Loki). Todo log carrega o correlationId da requisição corrente, gerado
 * pelo plugin correlation-id do Kong e propagado nos CloudEvents.
 *
 * <p>Uso: {@code Log.info("avaliacao_registrada", "id", id, "urgencia", urgencia)}.
 * Por LGPD, não registrar descrição nem identificador do aluno.
 */
public final class Log {

    public static final String HEADER_CORRELACAO = "X-Correlation-ID";

    private static final ThreadLocal<String> CORRELACAO = new ThreadLocal<>();

    private Log() {}

    public static void correlacao(String correlationId) {
        if (correlationId == null) CORRELACAO.remove();
        else CORRELACAO.set(correlationId);
    }

    public static String correlacao() {
        return CORRELACAO.get();
    }

    public static void info(String evento, Object... campos) {
        escrever("INFO", evento, null, campos);
    }

    public static void aviso(String evento, Object... campos) {
        escrever("WARN", evento, null, campos);
    }

    public static void erro(String evento, Throwable erro, Object... campos) {
        escrever("ERROR", evento, erro, campos);
    }

    private static void escrever(String nivel, String evento, Throwable erro, Object... campos) {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("ts", Instant.now().toString());
        linha.put("nivel", nivel);
        linha.put("evento", evento);
        linha.put("correlationId", CORRELACAO.get());
        for (int i = 0; i + 1 < campos.length; i += 2) {
            linha.put(String.valueOf(campos[i]), campos[i + 1]);
        }
        if (erro != null) {
            linha.put("erro", erro.getClass().getName());
            linha.put("mensagemErro", erro.getMessage());
        }
        System.out.println(Json.escrever(linha));
    }
}
