FROM ethereum/solc:0.4.24 as builder
WORKDIR /app
COPY target/lightchain-container-*-jar-with-dependencies.jar /app/
COPY src/main/resources/log4j.properties /app/
COPY simulation.config /app/
COPY contracts/*.sol /app/
RUN mkdir -p /app/build/solc \
 && solc -o /app/build/solc --bin testcon.sol
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app /app

CMD ["sh", "-lc", "java -jar lightchain-container-*-jar-with-dependencies.jar"]
