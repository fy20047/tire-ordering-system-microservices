# =========================
# 1) Build 階段
# 目的：在容器內編譯專案並產生 jar
# =========================
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先複製 pom，利用 Docker layer cache 加速相依下載。
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# 再複製原始碼並打包（此階段略過測試，與現有專案策略一致）。
COPY src src
RUN mvn -q -DskipTests package

# =========================
# 2) Runtime 階段
# 目的：使用較小的 JRE 映像執行 jar
# =========================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 只複製 build 產出的 jar，避免把編譯工具帶入執行環境。
COPY --from=build /app/target/api-gateway-*.jar app.jar
EXPOSE 8080

# 允許部署時透過 JAVA_OPTS 注入 JVM 參數（如記憶體設定）。
ENV JAVA_OPTS=""

# 啟動應用程式。
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
