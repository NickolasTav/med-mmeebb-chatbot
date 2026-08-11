# ==========================================
# Multi-stage Dockerfile para Spring Boot 3
# Projeto TCC: Chatbot MMEEBB para Medicina
# ==========================================

# Estágio 1: Build da Aplicação
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

# Copia arquivos de configuração Maven e wrapper
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Garante permissão de execução do wrapper e baixa dependências offline
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copia o código-fonte da aplicação
COPY src/ src/

# Compila e empacota o JAR sem rodar testes na imagem (testes rodam no CI)
RUN ./mvnw clean package -DskipTests -B

# Extrai camadas do Spring Boot para otimizar cache de imagens Docker
RUN java -Djarmode=layertools -jar target/*.jar extract

# ==========================================
# Estágio 2: Imagem Final de Execução (JRE Enxuta)
# ==========================================
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Cria usuário sem privilégios de root por segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Instala curl para suporte a Healthchecks de container
RUN apk add --no-cache curl

# Copia as camadas extraídas do estágio de build
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# Define permissões para o usuário não-root
RUN chown -R appuser:appgroup /app
USER appuser

# Configuração de portas e variáveis padrão
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Healthcheck do container usando o Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Ponto de entrada utilizando o JarLauncher do Spring Boot 3
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
