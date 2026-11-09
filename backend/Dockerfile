# 多阶段构建，所有 Java 服务共用：
#   docker build --build-arg MODULE=scan-service -t depwatch/scan-service .
#
# 阶段 1：先只拷 pom 下载依赖（利用 Docker 层缓存，改代码不重新下依赖）
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY project-service/pom.xml project-service/
COPY scan-service/pom.xml scan-service/
COPY alert-service/pom.xml alert-service/
RUN mvn -q -B dependency:go-offline -DexcludeReactor=false || true

# 阶段 2：编译目标模块及其依赖的模块（-am）
FROM deps AS build
ARG MODULE
COPY . .
RUN mvn -q -B -pl ${MODULE} -am package -DskipTests

# 阶段 3：解开 Boot 分层 jar
FROM eclipse-temurin:21-jre AS extract
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# 阶段 4：运行镜像（非 root 用户）
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
