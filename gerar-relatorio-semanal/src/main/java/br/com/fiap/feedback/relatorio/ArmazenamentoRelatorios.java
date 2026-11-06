package br.com.fiap.feedback.relatorio;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** Grava os arquivos do relatório no MinIO (API S3) com o usuário de serviço de política mínima. */
public class ArmazenamentoRelatorios {

    private final S3Client s3;
    private final String bucket;

    public ArmazenamentoRelatorios(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public String bucket() {
        return bucket;
    }

    public void gravar(String chave, String conteudo, String contentType) {
        s3.putObject(r -> r.bucket(bucket).key(chave).contentType(contentType), RequestBody.fromString(conteudo));
    }
}
