# Imagem de uma função (of-watchdog em modo HTTP + Javalin). Mesma receita para as três:
#   docker build --build-arg MODULO=receber-avaliacao -t <registry>/receber-avaliacao:<tag> .
# Pré-requisito: ./mvnw package (gera <MODULO>/target/app.jar). `make build` faz os dois.
FROM ghcr.io/openfaas/of-watchdog:0.12.6 AS watchdog

FROM eclipse-temurin:21-jre-alpine
ARG MODULO
RUN addgroup -S app && adduser -S app -G app
COPY --from=watchdog /fwatchdog /usr/bin/fwatchdog
COPY ${MODULO}/target/app.jar /home/app/app.jar
WORKDIR /home/app
USER app

# of-watchdog recebe em :8080 e repassa ao Javalin em 127.0.0.1:8082 (processo mantido
# aquecido entre invocações). Flags de JVM reduzem o cold start do scale-from-zero.
# prefix_logs=false: stdout sai como JSON puro para o Loki.
ENV mode="http" \
    upstream_url="http://127.0.0.1:8082" \
    ready_path="/health" \
    fprocess="java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=70 -Xshare:auto -jar app.jar" \
    exec_timeout="60s" \
    read_timeout="60s" \
    write_timeout="60s" \
    healthcheck_interval="5s" \
    prefix_logs="false"

EXPOSE 8080
HEALTHCHECK --interval=5s CMD [ -e /tmp/.lock ] || exit 1
CMD ["fwatchdog"]
