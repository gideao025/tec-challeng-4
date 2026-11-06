package br.com.fiap.feedback.commons;

import java.time.Instant;

/**
 * Avaliação persistida na tabela {@code avaliacoes}.
 *
 * @param alunoId   "sub" do JWT do Keycloak (pseudônimo; nunca vem do corpo da requisição)
 * @param urgencia  calculada e gravada no recebimento
 * @param dataEnvio instante do recebimento, UTC
 */
public record Avaliacao(String id, String alunoId, String descricao, int nota, Urgencia urgencia, Instant dataEnvio) {}
