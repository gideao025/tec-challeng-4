package br.com.fiap.feedback.commons;

/**
 * Fonte única da regra de urgência para todas as funções.
 *
 * <pre>
 *   0 .. LIMITE_CRITICA                   -> CRITICA   (padrão 0..3)
 *   LIMITE_CRITICA+1 .. LIMITE_MODERADA   -> MODERADA  (padrão 4..6)
 *   LIMITE_MODERADA+1 .. 10               -> BAIXA     (padrão 7..10)
 * </pre>
 *
 * A urgência é calculada e persistida no momento da gravação: mudar os limites
 * não reclassifica avaliações já registradas.
 */
public final class ClassificadorUrgencia {

    public static final int NOTA_MINIMA = 0;
    public static final int NOTA_MAXIMA = 10;

    private final int limiteCritica;
    private final int limiteModerada;

    public ClassificadorUrgencia(int limiteCritica, int limiteModerada) {
        if (limiteCritica < NOTA_MINIMA || limiteCritica >= limiteModerada || limiteModerada >= NOTA_MAXIMA) {
            throw new IllegalArgumentException(
                    "Limites inválidos: exige 0 <= LIMITE_CRITICA < LIMITE_MODERADA < 10 (recebido %d e %d)"
                            .formatted(limiteCritica, limiteModerada));
        }
        this.limiteCritica = limiteCritica;
        this.limiteModerada = limiteModerada;
    }

    /** Lê LIMITE_CRITICA e LIMITE_MODERADA do ambiente (padrões 3 e 6). */
    public static ClassificadorUrgencia doAmbiente() {
        return new ClassificadorUrgencia(Config.inteiro("LIMITE_CRITICA", 3), Config.inteiro("LIMITE_MODERADA", 6));
    }

    public static boolean notaValida(int nota) {
        return nota >= NOTA_MINIMA && nota <= NOTA_MAXIMA;
    }

    public Urgencia classificar(int nota) {
        if (!notaValida(nota)) {
            throw new IllegalArgumentException("Nota deve estar entre 0 e 10: " + nota);
        }
        if (nota <= limiteCritica) return Urgencia.CRITICA;
        if (nota <= limiteModerada) return Urgencia.MODERADA;
        return Urgencia.BAIXA;
    }
}
