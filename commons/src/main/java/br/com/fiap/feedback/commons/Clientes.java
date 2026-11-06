package br.com.fiap.feedback.commons;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Clientes AWS SDK v2 apontados (endpointOverride) para os equivalentes self-hosted:
 * DynamoDB -> ScyllaDB Alternator, S3 -> MinIO. Trocar de nuvem = trocar variáveis de ambiente.
 */
public final class Clientes {

    private Clientes() {}

    private static Region regiao() {
        return Region.of(Config.texto("AWS_REGION", "us-east-1"));
    }

    private static StaticCredentialsProvider credenciais(String secretAcesso, String secretChave) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(Config.segredo(secretAcesso), Config.segredo(secretChave)));
    }

    public static DynamoDbClient dynamo() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(Config.obrigatorio("ALTERNATOR_ENDPOINT")))
                .region(regiao())
                .credentialsProvider(credenciais("alternator-access-key", "alternator-secret-key"))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    public static S3Client s3() {
        return S3Client.builder()
                .endpointOverride(URI.create(Config.obrigatorio("S3_ENDPOINT")))
                .region(regiao())
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .credentialsProvider(credenciais("minio-access-key", "minio-secret-key"))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    /** Cria a tabela (on-demand) se ainda não existir. {@code chaveOrdenacao} pode ser null. */
    public static void garantirTabela(DynamoDbClient db, String tabela, String chaveParticao, String chaveOrdenacao) {
        try {
            db.describeTable(r -> r.tableName(tabela));
            return;
        } catch (ResourceNotFoundException naoExiste) {
            // segue para criação
        }
        List<KeySchemaElement> chaves = new ArrayList<>();
        List<AttributeDefinition> atributos = new ArrayList<>();
        chaves.add(KeySchemaElement.builder().attributeName(chaveParticao).keyType(KeyType.HASH).build());
        atributos.add(AttributeDefinition.builder().attributeName(chaveParticao).attributeType(ScalarAttributeType.S).build());
        if (chaveOrdenacao != null) {
            chaves.add(KeySchemaElement.builder().attributeName(chaveOrdenacao).keyType(KeyType.RANGE).build());
            atributos.add(AttributeDefinition.builder().attributeName(chaveOrdenacao).attributeType(ScalarAttributeType.S).build());
        }
        try {
            db.createTable(r -> r.tableName(tabela).keySchema(chaves).attributeDefinitions(atributos)
                    .billingMode(BillingMode.PAY_PER_REQUEST));
            Log.info("tabela_criada", "tabela", tabela);
        } catch (ResourceInUseException corrida) {
            // outra instância criou ao mesmo tempo
        }
    }
}
