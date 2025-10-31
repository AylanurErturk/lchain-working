FROM ethereum/solc:0.4.24 AS builder
RUN mkdir /app
COPY target/lightchain-container-*-jar-with-dependencies.jar /app/
COPY src/main/resources/log4j2.properties /app/
COPY simulation.config /app/
COPY contracts/*.sol /app/
WORKDIR /app
RUN solc -o . --bin testcon.sol

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app .

ENV JAVA_OPTS="\
 -Dlog4j2.debug=true \
 -Dlog4j.configurationFile=file:/app/log4j2.properties"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -cp /app/lightchain-container-0.0.1-SNAPSHOT-jar-with-dependencies.jar simulation.SimulationDriver"]
