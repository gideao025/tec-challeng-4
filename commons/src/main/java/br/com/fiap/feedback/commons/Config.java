package br.com.fiap.feedback.commons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Configuração por variáveis de ambiente e secrets do OpenFaaS. Nada hardcoded. */
public final class Config {

    private static final Path SECRETS = Path.of("/var/openfaas/secrets");

    private Config() {}

    public static String texto(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null || v.isBlank() ? padrao : v.trim();
    }

    public static String obrigatorio(String nome) {
        String v = texto(nome, null);
        if (v == null) throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + nome);
        return v;
    }

    public static int inteiro(String nome, int padrao) {
        String v = texto(nome, null);
        try {
            return v == null ? padrao : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Variável %s deve ser inteira: %s".formatted(nome, v), e);
        }
    }

    public static List<String> lista(String nome) {
        return Arrays.stream(obrigatorio(nome).split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Lê um secret do OpenFaaS (/var/openfaas/secrets/&lt;nome&gt;). Fora do faasd (testes,
     * execução local) usa a variável de ambiente equivalente: minio-secret-key -> MINIO_SECRET_KEY.
     */
    public static String segredo(String nome) {
        Path arquivo = SECRETS.resolve(nome);
        if (Files.isReadable(arquivo)) {
            try {
                return Files.readString(arquivo).trim();
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler secret " + nome, e);
            }
        }
        return obrigatorio(nome.toUpperCase().replace('-', '_'));
    }
}
