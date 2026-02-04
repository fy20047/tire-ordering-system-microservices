# =========================
# 1. Build
# =========================
# 使用含 JDK 的 Java 21（Jammy=Ubuntu 22.04）
FROM eclipse-temurin:21-jdk-jammy AS build
# 設定 container 的工作目錄為 /app
WORKDIR /app

# COPY 分開是為了快取：pom.xml 不變就不用重抓依賴
# 複製 Maven Wrapper（在容器裡不用自己裝 Maven）
COPY mvnw ./
# 複製 Maven Wrapper 需要的 .mvn 資料夾
COPY .mvn .mvn
# 先只複製 pom.xml（這樣可以用 Docker cache）
COPY pom.xml ./
# 先把依賴下載好，可以比較快
# dependency:go-offline 會把 pom.xml 需要的依賴抓下來
RUN ./mvnw -q -DskipTests dependency:go-offline

# 複製 Java code 進去
COPY src src
# 打包成 jar（略過測試）
RUN ./mvnw -q -DskipTests package

# =========================
# 2. Runtime
# =========================
# 使用更小的 JRE（前面已經編譯跟打包好，所以這邊只需要跑 jar，用 Java Runtime Environment 就好）
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 把編譯好的成品從 build 容器搬到 runtime 容器
#  jar 會放在 target/，名稱為 backend-0.0.1-SNAPSHOT.jar
COPY --from=build /app/target/backend-*.jar app.jar
EXPOSE 8080
# 允許在 docker run 時用環境變數傳 JVM 參數
ENV JAVA_OPTS=""

# sh -c 是為了讓 $JAVA_OPTS 這種環境變數在啟動時會被展開成真正的 JVM 參數
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
