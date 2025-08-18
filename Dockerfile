FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY pom.xml .
COPY ten4j-core/pom.xml ten4j-core/
COPY ten4j-server/pom.xml ten4j-server/
COPY ten4j-agent/pom.xml ten4j-agent/
RUN mvn -B dependency:go-offline
COPY . .
RUN mvn clean install -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /app/ten4j-server/target/ten4j-server-1.0-SNAPSHOT.jar ./ten4j-server.jar

EXPOSE 8080
CMD java -Dbailian.dashscope.api.key=${BAILIAN_DASHSCOPE_API_KEY} --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -jar ten4j-server.jar
