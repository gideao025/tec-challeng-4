package br.com.fiap.feedback.notificar;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.CloudEvent;
import br.com.fiap.feedback.commons.Datas;
import br.com.fiap.feedback.commons.EnviadorEmail;
import br.com.fiap.feedback.commons.Json;
import br.com.fiap.feedback.commons.Log;
import br.com.fiap.feedback.commons.Urgencia;
import java.util.List;

/** Caso de uso: alertar os administradores sobre avaliações CRITICA, uma única vez por avaliação. */
public class NotificarCritico {

    public enum Resultado { NOTIFICADO, IGNORADO, DUPLICADO }

    private final Deduplicador deduplicador;
    private final EnviadorEmail email;
    private final List<String> administradores;

    public NotificarCritico(Deduplicador deduplicador, EnviadorEmail email, List<String> administradores) {
        this.deduplicador = deduplicador;
        this.email = email;
        this.administradores = administradores;
    }

    public Resultado processar(String corpo) {
        CloudEvent evento = Json.ler(corpo, CloudEvent.class);
        Log.correlacao(evento.correlationid());
        Avaliacao a = evento.data();
        if (!CloudEvent.AVALIACAO_CRIADA.equals(evento.type()) || a == null || a.urgencia() != Urgencia.CRITICA) {
            Log.info("notificacao_ignorada", "id", a == null ? null : a.id(), "urgencia", a == null ? null : a.urgencia());
            return Resultado.IGNORADO;
        }
        if (!deduplicador.registrarSeNovo(a.id())) {
            Log.info("notificacao_duplicada", "id", a.id());
            return Resultado.DUPLICADO;
        }
        try {
            email.enviar(administradores, assunto(a), corpo(a));
        } catch (RuntimeException e) {
            deduplicador.liberar(a.id());
            throw e;
        }
        Log.info("notificacao_enviada", "id", a.id(), "destinatarios", administradores.size());
        return Resultado.NOTIFICADO;
    }

    static String assunto(Avaliacao a) {
        return "[ALERTA] Avaliação CRÍTICA recebida - nota " + a.nota();
    }

    static String corpo(Avaliacao a) {
        return """
                Uma avaliação de aula foi classificada como CRÍTICA e requer atenção.

                Descrição:      %s
                Urgência:       %s
                Data de envio:  %s
                Nota:           %d
                ID:             %s

                --
                Plataforma de Feedback de Aulas (alerta automático; não responda este e-mail).
                """.formatted(a.descricao(), a.urgencia(), Datas.formatarBrasilia(a.dataEnvio()), a.nota(), a.id());
    }
}
