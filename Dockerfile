FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn --batch-mode --no-transfer-progress -DskipTests package
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/eureka-server/target/eureka-server-1.0.0.jar /app/eureka-server.jar
COPY --from=build /workspace/api-gateway/target/api-gateway-1.0.0.jar /app/api-gateway.jar
COPY --from=build /workspace/pacientes-service/target/pacientes-service-1.0.0.jar /app/pacientes-service.jar
COPY --from=build /workspace/citas-service/target/citas-service-1.0.0.jar /app/citas-service.jar
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m"
CMD ["sh","-c","exec java -jar /app/$APP_MODULE.jar"]
