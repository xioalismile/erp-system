# ===== Stage 1: Maven Build =====
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制pom.xml下载依赖(利用Docker缓存层)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装时区数据
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 复制打包好的JAR
COPY --from=builder /build/target/smart-erp-1.0.0.jar app.jar

# 创建文件存储和日志目录
RUN mkdir -p /app/files /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
