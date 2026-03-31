# =========================
# 1) Build 階段
# 用途：在容器內完成編譯與打包，產生可執行 jar。
# =========================
# 使用含 Maven + JDK 21 的映像，避免主機環境差異影響建置。
FROM maven:3.9.9-eclipse-temurin-21 AS build
# 設定容器工作目錄，後續指令都在 /app 執行。
WORKDIR /app

# 先複製 pom.xml：
# 可利用 Docker layer cache，當依賴未變時不需重抓套件。
COPY pom.xml ./
# 先下載依賴，讓後續 package 更快且更穩定。
RUN mvn -q -DskipTests dependency:go-offline

# 再複製原始碼，避免每次改 code 都重抓依賴。
COPY src src
# 建置 jar（這一步先略過測試，與現有服務建置策略一致）。
RUN mvn -q -DskipTests package

# =========================
# 2) Runtime 階段
# 用途：用更小映像執行 jar，減少部署體積與攻擊面。
# =========================
# 使用僅含 JRE 的映像，專責執行、不負責編譯。
FROM eclipse-temurin:21-jre-jammy
# runtime 工作目錄。
WORKDIR /app

# 只複製建置產物，不把 Maven 與原始碼帶入 runtime 映像。
COPY --from=build /app/target/tire-service-*.jar app.jar
# 對外服務埠（Spring Boot 預設 8080）。
EXPOSE 8080

# 預留 JVM 參數注入點，方便部署時調整記憶體等設定。
ENV JAVA_OPTS=""

# 以 sh -c 啟動，讓 $JAVA_OPTS 在執行時可被展開。
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
