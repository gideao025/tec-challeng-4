package br.com.fiap.feedback.relatorio;

import br.com.fiap.feedback.commons.Avaliacao;
import br.com.fiap.feedback.commons.AvaliacaoRepository;
import br.com.fiap.feedback.commons.Datas;
import br.com.fiap.feedback.commons.EnviadorEmail;
import br.com.fiap.feedback.commons.Json;
import br.com.fiap.feedback.commons.Log;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Caso de uso: consolidar os 7 dias anteriores à data de referência (padrão: hoje em
 * Brasília; o cron de segunda 08:00 cobre segunda a domingo da semana anterior).
 * Exatamente 7 Query no Alternator, uma por partição diária — nunca Scan.
 */
public class GerarRelatorioSemanal {

    private final AvaliacaoRepository repositorio;
    private final ArmazenamentoRelatorios armazenamento;
    private final EnviadorEmail email;
    private final List<String> administradores;
    private final String consoleMinioUrl;
    private final Clock relogio;

    public GerarRelatorioSemanal(AvaliacaoRepository repositorio, ArmazenamentoRelatorios armazenamento,
                                 EnviadorEmail email, List<String> administradores, String consoleMinioUrl, Clock relogio) {
        this.repositorio = repositorio;
        this.armazenamento = armazenamento;
        this.email = email;
        this.administradores = administradores;
        this.consoleMinioUrl = consoleMinioUrl;
        this.relogio = relogio;
    }

    public Relatorio processar(LocalDate referencia) {
        LocalDate hoje = referencia != null ? referencia : LocalDate.now(relogio.withZone(Datas.BRASILIA));
        LocalDate inicio = hoje.minusDays(7);

        List<Avaliacao> avaliacoes = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            avaliacoes.addAll(repositorio.listarDoDia(inicio.plusDays(i)));
        }
        Relatorio r = Relatorio.consolidar(inicio, avaliacoes, Instant.now(relogio));

        String base = "relatorios/" + r.semana();
        armazenamento.gravar(base + ".json", Json.escrever(r), "application/json; charset=utf-8");
        armazenamento.gravar(base + ".csv", r.paraCsv(), "text/csv; charset=utf-8");
        email.enviar(administradores, "Relatório semanal de avaliações - " + r.semana(), corpoEmail(r, base));

        Log.info("relatorio_gerado", "semana", r.semana(), "total", r.totalAvaliacoes(), "media", r.mediaNotas());
        return r;
    }

    String corpoEmail(Relatorio r, String base) {
        StringBuilder t = new StringBuilder();
        t.append("Relatório semanal de avaliações - semana %s (%s a %s)%n%n".formatted(
                r.semana(), r.inicio(), r.fim()));
        t.append("Total de avaliações: %d%n".formatted(r.totalAvaliacoes()));
        t.append("Média geral das notas: %s%n%n".formatted(r.mediaFormatada()));
        t.append("Quantidade por urgência:%n".formatted());
        r.quantidadePorUrgencia().forEach((u, n) -> t.append("  %-9s %d%n".formatted(u, n)));
        t.append("%nQuantidade por dia:%n".formatted());
        r.quantidadePorDia().forEach((d, n) -> t.append("  %s %d%n".formatted(d, n)));
        t.append("%nRelatório completo (login de administrador no MinIO):%n".formatted());
        t.append("  %s/browser/%s%n".formatted(consoleMinioUrl, armazenamento.bucket()));
        t.append("  objetos: %s.csv e %s.json%n".formatted(base, base));
        return t.toString();
    }
}
