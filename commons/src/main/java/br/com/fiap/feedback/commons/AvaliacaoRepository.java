package br.com.fiap.feedback.commons;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

/**
 * Persistência da tabela {@code avaliacoes} no ScyllaDB Alternator (API DynamoDB).
 *
 * <pre>
 *   PK = "AVALIACAO#&lt;YYYY-MM-DD&gt;"   (dia no fuso de Brasília: uma partição por dia de negócio)
 *   SK = "&lt;timestampISO&gt;#&lt;uuid&gt;"  (ordena as avaliações do dia por instante de envio)
 * </pre>
 *
 * O relatório semanal lê exatamente 7 partições com Query, nunca Scan.
 */
public class AvaliacaoRepository {

    private final DynamoDbClient db;
    private final String tabela;

    public AvaliacaoRepository(DynamoDbClient db, String tabela) {
        this.db = db;
        this.tabela = tabela;
    }

    public static AvaliacaoRepository doAmbiente(DynamoDbClient db) {
        String tabela = Config.texto("TABELA_AVALIACOES", "avaliacoes");
        Clientes.garantirTabela(db, tabela, "PK", "SK");
        return new AvaliacaoRepository(db, tabela);
    }

    public static String pk(LocalDate dia) {
        return "AVALIACAO#" + dia;
    }

    public static String sk(Avaliacao a) {
        return a.dataEnvio() + "#" + a.id();
    }

    public void salvar(Avaliacao a) {
        db.putItem(PutItemRequest.builder()
                .tableName(tabela)
                .item(Map.of(
                        "PK", s(pk(Datas.diaEmBrasilia(a.dataEnvio()))),
                        "SK", s(sk(a)),
                        "id", s(a.id()),
                        "alunoId", s(a.alunoId()),
                        "descricao", s(a.descricao()),
                        "nota", AttributeValue.fromN(Integer.toString(a.nota())),
                        "urgencia", s(a.urgencia().name()),
                        "dataEnvio", s(a.dataEnvio().toString())))
                .build());
    }

    /** Uma Query na partição do dia (paginada pelo SDK se passar de 1 MB). */
    public List<Avaliacao> listarDoDia(LocalDate dia) {
        QueryRequest consulta = QueryRequest.builder()
                .tableName(tabela)
                .keyConditionExpression("PK = :pk")
                .expressionAttributeValues(Map.of(":pk", s(pk(dia))))
                .build();
        return db.queryPaginator(consulta).items().stream().map(AvaliacaoRepository::paraAvaliacao).toList();
    }

    private static Avaliacao paraAvaliacao(Map<String, AttributeValue> item) {
        return new Avaliacao(
                item.get("id").s(),
                item.get("alunoId").s(),
                item.get("descricao").s(),
                Integer.parseInt(item.get("nota").n()),
                Urgencia.valueOf(item.get("urgencia").s()),
                Instant.parse(item.get("dataEnvio").s()));
    }

    private static AttributeValue s(String valor) {
        return AttributeValue.fromS(valor);
    }
}
