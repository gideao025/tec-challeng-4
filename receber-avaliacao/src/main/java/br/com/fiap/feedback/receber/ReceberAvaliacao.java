package br.com.fiap.feedback.receber;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.AvaliacaoRepository;
import br.com.fiap.feedback.commons.ClassificadorUrgencia;
import br.com.fiap.feedback.commons.CloudEvent;
import br.com.fiap.feedback.commons.Json;
import br.com.fiap.feedback.commons.Log;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: validar, classificar, persistir e publicar. Independente de HTTP
 * (Javalin só faz a ponte em {@link Main}) para ser testado sem servidor.
 */
public class ReceberAvaliacao {

    public record Resposta(int status, Object corpo) {}

    private static final Set<String> CAMPOS_PERMITIDOS = Set.of("descricao", "nota");
    static final String ROLE_ALUNO = "aluno";

    private final AvaliacaoRepository repositorio;
    private final EventPublisher publicador;
    private final ClassificadorUrgencia classificador;
    private final Clock relogio;
    private final int tamanhoMaximoDescricao;

    public ReceberAvaliacao(AvaliacaoRepository repositorio, EventPublisher publicador,
                            ClassificadorUrgencia classificador, Clock relogio, int tamanhoMaximoDescricao) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.classificador = classificador;
        this.relogio = relogio;
        this.tamanhoMaximoDescricao = tamanhoMaximoDescricao;
    }

    public Resposta processar(String corpo, String authorization) {
        // 1. Identidade: o Kong já validou assinatura/expiração; aqui só lemos as claims.
        JsonNode claims = claimsDoJwt(authorization);
        if (claims == null || !claims.path("sub").isTextual()) {
            return erro(401, "NAO_AUTENTICADO", "Token JWT ausente ou ilegível.", List.of());
        }
        if (!temRole(claims, ROLE_ALUNO)) {
            return erro(403, "SEM_PERMISSAO", "Somente usuários com a role 'aluno' podem enviar avaliações.", List.of());
        }

        // 2. Validação do contrato { "descricao": string, "nota": int 0..10 }
        JsonNode json;
        try {
            json = Json.MAPPER.readTree(corpo == null ? "" : corpo);
        } catch (Exception e) {
            json = null;
        }
        if (json == null || !json.isObject()) {
            return erro(400, "PAYLOAD_INVALIDO", "O corpo deve ser um objeto JSON.", List.of());
        }
        List<String> detalhes = new ArrayList<>();
        json.fieldNames().forEachRemaining(campo -> {
            if (!CAMPOS_PERMITIDOS.contains(campo)) detalhes.add("campo não permitido: " + campo);
        });
        JsonNode descricao = json.get("descricao");
        if (descricao == null || !descricao.isTextual() || descricao.asText().isBlank()) {
            detalhes.add("descricao: obrigatória e não pode ser vazia");
        } else if (descricao.asText().length() > tamanhoMaximoDescricao) {
            detalhes.add("descricao: máximo de %d caracteres".formatted(tamanhoMaximoDescricao));
        }
        JsonNode nota = json.get("nota");
        if (nota == null || !nota.isIntegralNumber() || !nota.canConvertToInt()) {
            detalhes.add("nota: obrigatória e deve ser um número inteiro");
        } else if (!ClassificadorUrgencia.notaValida(nota.intValue())) {
            detalhes.add("nota: deve estar entre 0 e 10");
        }
        if (!detalhes.isEmpty()) {
            Log.info("avaliacao_rejeitada", "motivos", detalhes);
            return erro(400, "PAYLOAD_INVALIDO", "Avaliação inválida.", detalhes);
        }

        // 3. Classifica, persiste (urgência gravada) e publica
        Instant agora = Instant.now(relogio).truncatedTo(ChronoUnit.MILLIS);
        var avaliacao = new Avaliacao(UUID.randomUUID().toString(), claims.get("sub").asText(),
                descricao.asText().strip(), nota.intValue(), classificador.classificar(nota.intValue()), agora);
        repositorio.salvar(avaliacao);
        Log.info("avaliacao_registrada", "id", avaliacao.id(), "nota", avaliacao.nota(),
                "urgencia", avaliacao.urgencia());

        try {
            publicador.publicar(CloudEvent.avaliacaoCriada(avaliacao, Log.correlacao()));
        } catch (RuntimeException e) {
            // ponytail: a avaliação já está gravada; sem outbox, o alerta pode se perder se o NATS
            // estiver fora. O log ERROR dispara alerta no Grafana. Outbox se a garantia for exigida.
            Log.erro("evento_nao_publicado", e, "id", avaliacao.id());
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("id", avaliacao.id());
        resposta.put("urgencia", avaliacao.urgencia());
        resposta.put("dataEnvio", avaliacao.dataEnvio());
        return new Resposta(201, resposta);
    }

    private static JsonNode claimsDoJwt(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String[] partes = authorization.substring(7).trim().split("\\.");
        if (partes.length != 3) return null;
        try {
            return Json.MAPPER.readTree(Base64.getUrlDecoder().decode(partes[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean temRole(JsonNode claims, String role) {
        for (JsonNode r : claims.path("realm_access").path("roles")) {
            if (role.equals(r.asText())) return true;
        }
        return false;
    }

    private static Resposta erro(int status, String codigo, String mensagem, List<String> detalhes) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", codigo);
        corpo.put("mensagem", mensagem);
        corpo.put("detalhes", detalhes);
        corpo.put("correlationId", Log.correlacao());
        return new Resposta(status, corpo);
    }
}
