# Etapa 1: Build (opcional se você já tem o .jar)
# FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
# WORKDIR /app
# COPY . .
# RUN mvn clean package -DskipTests

# Etapa 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Diretório de trabalho dentro do container
WORKDIR /app

# Copiar o jar gerado para dentro do container
# (se você usou a etapa de build, copie de /app/target)
COPY target/*.jar app.jar

# Porta que sua aplicação irá expor (se configurada)
EXPOSE 8080

# Comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
