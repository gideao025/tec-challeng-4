package br.com.fiap.feedback.commons;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Fuso de negócio (horário de Brasília) usado nas partições, e-mails e relatórios. */
public final class Datas {

    public static final ZoneId BRASILIA = ZoneId.of(Config.texto("FUSO_NEGOCIO", "America/Sao_Paulo"));

    private static final DateTimeFormatter FORMATO_BR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss '(horário de Brasília)'").withZone(BRASILIA);

    private Datas() {}

    public static String formatarBrasilia(Instant instante) {
        return FORMATO_BR.format(instante);
    }

    public static LocalDate diaEmBrasilia(Instant instante) {
        return instante.atZone(BRASILIA).toLocalDate();
    }
}
