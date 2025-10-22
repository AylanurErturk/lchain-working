FROM ethereum/solc:0.4.24 as builder 
# We use a solidity container to compile our contracts 
RUN mkdir /app 
# copy your already-built fat JAR + config + contracts 
COPY target/lightchain-container-*-jar-with-dependencies.jar /app 
COPY src/main/resources/log4j.properties /app 
COPY simulation.config /app COPY contracts/*.sol /app/ 
WORKDIR /app 
# Compile the contract(s) – hardcoded example 
RUN solc -o . --bin testcon.sol 

FROM eclipse-temurin:21-jre WORKDIR /app 
COPY --from=builder /app . 
# Run the assembled JAR (use wildcard so you don’t hardcode the version) 
CMD ["sh", "-lc", "java -jar lightchain-container-*-jar-with-dependencies.jar"]
