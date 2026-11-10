# Multi-stage build shared by all Java services:
#   docker build --build-arg MODULE=scan-service -t depwatch/scan-service .
#
# Stage 1: copy only the POMs and download dependencies (Docker layer cache — code changes don't re-download)
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY project-service/pom.xml project-service/
COPY scan-service/pom.xml scan-service/
COPY alert-service/pom.xml alert-service/
RUN mvn -q -B dependency:go-offline -DexcludeReactor=false || true

# Stage 2: compile the target module and the modules it depends on (-am)
FROM deps AS build
ARG MODULE
COPY . .
RUN mvn -q -B -pl ${MODULE} -am package -DskipTests

# Stage 3: extract the Boot layered jar
FROM eclipse-temurin:21-jre AS extract
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 4: runtime image (non-root user)
FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 -g root app
WORKDIR /app
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./
RUN mkdir -p /data/manifests && chown -R app:root /data
USER app
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
