# 修改紀錄

> 目的：記錄依照 `README.md` 第 12 節執行清單所做的每一步變更，避免遺漏與方便回溯。

## 2026-03-31 - Step 1：建立 API 契約文件（Phase 0）

### 對應清單項目
- `README.md` §12 項目 1：建 API 契約文件（以現況為準）

### 本次修改檔案
- `docs/api-contract-baseline.md`（新增）
- `MODIFICATION_HISTORY.md`（新增，本檔）

### 變更內容
1. 新增 `docs/api-contract-baseline.md`，凍結目前單體後端 API 契約，內容包含：
   - 共通規則（授權、cookie、時間格式、錯誤格式）
   - Enum 契約（`InstallationOption`、`OrderStatus`）
   - 全部現有 API 路徑、方法、請求/回應結構與主要狀態碼
   - Phase 1 的契約凍結原則
2. 新增本修改紀錄檔，作為後續每一步改動的歷史追蹤入口。

### 契約來源程式碼（本次盤點依據）
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/*`
- `backend/src/main/java/com/fy20047/tireordering/backend/dto/*`
- `backend/src/main/java/com/fy20047/tireordering/backend/config/SecurityConfig.java`
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/GlobalExceptionHandler.java`
- `backend/src/main/resources/application.yaml`

### 驗證結果
- 已確認文件覆蓋以下現有 API 群組：
  - Admin Auth：`/api/admin/login`、`/api/admin/refresh`、`/api/admin/logout`
  - Public Tires：`/api/tires`、`/api/tires/{id}`
  - Public Orders：`/api/orders`
  - Admin Tires：`/api/admin/tires`、`/api/admin/tires/{id}`、`/api/admin/tires/{id}/active`
  - Admin Orders：`/api/admin/orders`、`/api/admin/orders/{id}/status`
  - Health：`/health`、`/api/health`

### 待辦
- 等你確認 Step 1 內容後，再進入 Step 2（新增 Gateway 專案並先切入口，不改業務實作）。

## 2026-03-31 - Step 2A：新增 Gateway 專案骨架（先不接入口）

### 對應清單項目
- `README.md` §12 項目 2：新增 Gateway 專案，先改入口，但不改業務實作

### 本次修改檔案
- `api-gateway/pom.xml`（新增）
- `api-gateway/Dockerfile`（新增）
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiGatewayApplication.java`（新增）
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java`（新增）
- `api-gateway/src/main/resources/application.yaml`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `api-gateway` Spring Boot 專案（Java 21、WebFlux、Actuator）。
2. 新增 `ApiProxyController`，先提供 Phase 1 所需的基礎能力：
   - 接收 `/api/**`
   - 原樣轉發到 `BACKEND_BASE_URL`（預設 `http://backend:8080`）
   - 保留回應狀態碼、回應 body 與必要 headers（含 `Set-Cookie`）
3. 新增 `application.yaml` 設定 gateway 目標後端位址與基本管理端點。
4. 新增 `Dockerfile`，可在容器中打包並執行 gateway。

### 說明
- 這一小步只完成「Gateway 專案本體」；尚未修改 `docker-compose`、`frontend/nginx.conf`、`k8s ingress`。
- 下一小步會把入口實際切到 gateway（仍維持業務在舊 backend）。

### 驗證結果
- 已使用 Maven Wrapper 執行編譯檢查：
  - `.\backend\mvnw.cmd -q -DskipTests -f .\api-gateway\pom.xml package`
  - 結果：成功。

## 2026-03-31 - Step 2A-1：Gateway 可維護性調整（改 MVC + 中文註解）

### 對應需求
- 針對「WebFlux 維護成本」疑慮，改為 Spring MVC 實作。
- 根據官方說明，若主要還是用 JPA、JDBC 這類 blocking persistence API，對一般架構而言 Spring MVC 通常更合適。
- 針對可讀性需求，為新增的 gateway 程式與 Dockerfile 補上中文註解（檔頭 + 段落）。

### Spring MVC vs Spring WebFlux 以及使用讀判斷標準
- MVC：每個請求進來，交給一個 thread 處理，遇到 DB/遠端呼叫可接受等待。
- WebFlux：盡量不要讓 thread 卡住，讓少量 thread 可以處理更多 I/O 型工作。
- 適合考慮 WebFlux :服務要同時呼叫很多下游 API、做 response aggregation、串流、SSE、WebSocket、或大量外部 I/O 等待。這類情況 non-blocking 模型比較能發揮效果。
- 不一定要改 WebFlux：如果瓶頸其實是 SQL 太慢、資料庫鎖競爭、JPA 查詢設計不好、交易邏輯太重、CPU 計算太多，那改 WebFlux 通常不是第一優先。

### 實作方向
- 在 monolith 拆分為 microservices 的第一階段，選擇先採用 Spring MVC，而非 WebFlux。
- 原因是目前核心流程以 CRUD 與資料庫交易為主，資料層仍使用 JPA/MariaDB（阻塞式 I/O），若僅在 API 層改為 WebFlux，無法形成端到端非阻塞，效益有限。
- 拆分初期的主要目標是服務邊界穩定、入口一致、低風險遷移與可維運性，因此優先採用除錯與監控成本較低的 MVC。
- 後續若監控數據顯示 Gateway 出現高併發 I/O 瓶頸（例如高fan-out、長連線、thread pool 飽和），再針對特定服務評估導入 WebFlux 或 Reactive stack。

### 本次修改檔案
- `api-gateway/pom.xml`（更新）
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiGatewayApplication.java`（更新）
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java`（重寫）
- `api-gateway/src/main/resources/application.yaml`（更新）
- `api-gateway/Dockerfile`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `pom.xml` 由 `spring-boot-starter-webflux` 改為 `spring-boot-starter-web`。
2. `ApiProxyController` 改為 Servlet/MVC 風格：
   - 使用 Java `HttpClient` 轉發 `/api/**` 到舊 backend
   - 保留原狀態碼、body 與必要 headers
3. 在 `pom.xml`、`ApiGatewayApplication`、`ApiProxyController`、`application.yaml`、`Dockerfile` 補上中文說明註解，包含檔案用途與段落用途。

### 說明
- 此變更不改 API 契約、不改業務邏輯，只是把 Gateway 實作方式調整為團隊較熟悉、較直觀的 MVC 寫法。

### 驗證結果
- 已重新執行編譯檢查：
  - `.\backend\mvnw.cmd -q -DskipTests -f .\api-gateway\pom.xml package`
  - 結果：成功。

## 2026-03-31 - Step 2B：本機入口切換到 Gateway（docker-compose + frontend）

### 對應清單項目
- `README.md` §12 項目 2：新增 Gateway 專案，先改入口，但不改業務實作

### 本次修改檔案
- `infra/docker-compose.yml`（更新）
- `frontend/nginx.conf`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `infra/docker-compose.yml`
   - 新增 `api-gateway` 服務（build `../api-gateway`，對外 `8080:8080`）
   - 設定 `BACKEND_BASE_URL=http://backend:8080`
   - `frontend` 的 `depends_on` 從 `backend` 改為 `api-gateway`
   - `backend` 移除對外 `8080` port，改成只在 compose 內部網路供 gateway 呼叫
2. `frontend/nginx.conf`
   - `/api/` 由 `proxy_pass http://backend:8080` 改為 `proxy_pass http://api-gateway:8080`

### 說明
- 這一步完成「入口切換」，但業務邏輯仍在舊 backend，符合 Phase 1 目標。
- 目前本機流向：`Browser -> frontend(Nginx) -> api-gateway -> backend`。

### 驗證結果
- 已執行 `docker compose -f infra/docker-compose.yml config`，配置語法可解析。
- 命令輸出中的 `.env` 變數警告屬於環境值未注入提示，不影響 compose 結構正確性。

## 2026-03-31 - Step 3A：新增 K8s Gateway 基礎資源（先不改 Ingress）

### 對應清單項目
- `README.md` §12 項目 3：調整 Ingress 讓所有 `/api` 先走 Gateway（本步先完成前置資源）

### 本次修改檔案
- `k8s/base/gateway-deployment.yaml`（新增）
- `k8s/base/gateway-service.yaml`（新增）
- `k8s/base/kustomization.yaml`（更新）
- `k8s/overlays/minikube/kustomization.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `k8s/base/gateway-deployment.yaml`
   - 建立 `api-gateway` Deployment（replicas=1、containerPort=8080）
   - 設定環境變數 `BACKEND_BASE_URL=http://backend:8080`（Phase 1 先回轉舊 backend）
   - 加入 readiness/liveness probe
2. 新增 `k8s/base/gateway-service.yaml`
   - 建立 `api-gateway` ClusterIP Service（port 8080）
3. 更新 `k8s/base/kustomization.yaml`
   - 納入 `gateway-service.yaml` 與 `gateway-deployment.yaml`
4. 更新 `k8s/overlays/minikube/kustomization.yaml`
   - 新增 `ghcr.io/fy20047/tire-ordering-system/api-gateway` image tag 覆蓋

### 說明
- 此步驟只補齊 Gateway 在 K8s 的可部署資源，尚未切換 Ingress 路由。
- 下一步（3B）才會把 `/api` Ingress 指向 `api-gateway`。

### 驗證結果
- 已執行 `kubectl kustomize k8s/base`，可成功輸出 manifest。
- 已執行 `kubectl kustomize k8s/overlays/minikube`，可成功輸出 manifest。

## 2026-03-31 - Step 3B：調整 Ingress 路由（`/api` -> Gateway）

### 對應清單項目
- `README.md` §12 項目 3：調整 Ingress 讓所有 `/api` 先走 Gateway

### 本次修改檔案
- `k8s/overlays/minikube/ingress.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `k8s/overlays/minikube/ingress.yaml`
   - 新增 `/api` 路徑規則，導向 `api-gateway:8080`
   - 保留 `/` 路徑規則，導向 `frontend:80`
   - 加入中文註解說明路由意圖

### 說明
- 這一步完成了 K8s 層的入口切換：API 請求在 Ingress 層就先進 Gateway。
- 目前符合 Phase 1 路徑：`External -> Ingress(/api) -> api-gateway -> backend`。

### 驗證結果
- 已執行 `kubectl kustomize k8s/overlays/minikube`，可成功輸出 manifest。
- Ingress 輸出已確認同時包含：
  - `/api` -> `api-gateway:8080`
  - `/` -> `frontend:80`

## 2026-03-31 - Step 4：建立 Smoke Integration 測試腳本與挑選說明

### 對應清單項目
- `README.md` §12 項目 4：驗證前端流程不變（登入、查輪胎、建單、後台功能）

### 本次修改檔案
- `scripts/smoke/run-smoke-gateway.ps1`（新增）
- `docs/smoke-integration-selection.md`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `scripts/smoke/run-smoke-gateway.ps1`
   - 提供可重複執行的 smoke integration 測試（10 個案例）
   - 覆蓋 login/refresh/logout、公開 API、後台授權 API、失敗路徑（401/403）
   - 內含中文註解：檔案用途與各段落用途
2. 新增 `docs/smoke-integration-selection.md`
   - 說明為何選這 10 個案例（風險覆蓋法）
   - 說明 smoke 與全量回歸的差異
   - 提供執行方式與通過判定標準

### 說明
- 本步驟目標是「先驗證入口改造後核心流程未回歸」，不是全 API 覆蓋。
- 全量 API 測試建議放在後續 SIT/回歸流程，避免 smoke 失去快速回饋特性。

### 驗證結果
- 已使用 PowerShell Parser 檢查 `scripts/smoke/run-smoke-gateway.ps1` 語法可解析。
- 實際 API 執行驗證需在服務啟動且測試帳密可用時執行。

## 2026-03-31 - Step 4A：補充說明與清理臨時檔

### 對應需求
- 補強文件：明確標註 smoke 文件屬於 Phase 1 Step 4。
- 清理過程產生的臨時檔。

### 本次修改檔案
- `docs/smoke-integration-selection.md`（更新）
- `scripts/smoke/_tmp_parse.ps1`（刪除）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 在 `docs/smoke-integration-selection.md` 新增「適用範圍」段落，明確標示：
   - 所屬階段：Phase 1
   - 所屬步驟：Step 4
2. 刪除 `scripts/smoke/_tmp_parse.ps1`（僅本地語法排查時產生的臨時檔）。

## 2026-03-31 - Step 4B：實跑 Smoke 並修正 Gateway 轉發細節

### 對應清單項目
- `README.md` §12 項目 4：驗證前端流程不變（登入、查輪胎、建單、後台功能）

### 本次修改檔案
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 修正 Gateway request header 排除清單
   - 新增排除 `Expect` header（`HttpHeaders.EXPECT`）
   - 原因：Java `HttpClient` 對 `Expect` 為限制型 header，直接轉發會拋出 `IllegalArgumentException`
2. 使用 `infra/.env` 啟動本機 compose 後，實際執行 smoke 腳本：
   - `powershell -ExecutionPolicy Bypass -File .\scripts\smoke\run-smoke-gateway.ps1 -BaseUrl "http://localhost:8080" -AdminUsername "<local>" -AdminPassword "<local>"`

### 驗證結果
- 10 個 smoke 案例全部通過：
  - login / refresh / logout
  - public tires / create order
  - admin list / admin patch status
  - refresh after logout 401
  - admin without token 403
  - refresh without cookie 401

## 2026-03-31 - Step 4C：新增 Gateway 根路徑說明頁與登入導引

### 本次修改目標
- 解決 `http://localhost:8080` 顯示 Whitelabel Error Page 的可用性問題。
- 提供清楚入口，讓使用者知道 API 應從哪裡測、登入頁應去哪裡開啟。

### 實際變更檔案
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/GatewayInfoController.java`（新增）
- `api-gateway/src/main/resources/application.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `GatewayInfoController`
   - `GET /`：回傳簡單 HTML 說明頁，包含：
     - `/api/health` 連結（API 健康檢查）
     - `/login` 連結（登入頁轉址入口）
     - `http://localhost:5173/` 前端首頁提示
   - `GET /login`：302 轉址到前端登入頁。
2. 新增可配置屬性
   - `gateway.frontend-login-url`，預設 `http://localhost:5173/admin/login`。
   - 可透過環境變數 `FRONTEND_LOGIN_URL` 覆蓋，方便本機與部署環境切換。

### 設計理由
- Gateway 在 Phase 1 仍是 API 導向服務，保留這個定位不改。
- 同時補上一個最小導引頁，避免 root path 對使用者不友善。
- 透過 `/login` 做「導引轉址」而不是在 Gateway 直接承接前端頁面，責任邊界清楚。

## 2026-03-31 - Step 4D：README 第 12 節收斂 Phase 1 與拆解 Phase 2

### 本次修改目標
- 在同一個「執行清單」區塊，把 Phase 1 已完成內容明確收斂，避免進度認知落差。
- 將 Phase 2（Auth 抽離 + RS256）拆成可執行細項與完成判準，作為下一階段主清單。

### 實際變更檔案
- `README.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 更新 `README.md` 第 12 節
   - 新增「Phase 1 小總結（已完成）」：列出 Gateway 導入、Ingress 調整、smoke 驗證與 root 導引頁成果。
   - 新增「Phase 2 細項（抽 Auth + RS256）」：分為 6 個執行步驟（service 建立、金鑰導入、路由調整、backend 收斂、smoke、部署文件同步）。
   - 新增「Phase 2 完成判準」：確認 Auth 單一出口、RS256 全面化、回歸驗證通過。

### 設計理由
- 執行清單要同時承擔「回顧」與「下一步」兩個用途，才能降低切換階段時的溝通成本。
- 先定義完成判準，有助於後續每一步驗證時對齊目標，不會只停留在「有改到程式」。

## 2026-03-31 - Step 5A-1：建立 auth-service 專案骨架

### 對應清單項目
- `README.md` §12 Phase 2 細項 1：建立 `auth-service` 專案骨架（Spring MVC）

### 本次修改檔案
- `auth-service/pom.xml`（新增）
- `auth-service/Dockerfile`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/AuthServiceApplication.java`（新增）
- `auth-service/src/main/resources/application.yaml`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `auth-service` Maven 專案
   - 建立 `pom.xml`，納入 Auth 拆分所需的基礎依賴：
     - Spring MVC / Security / Validation
     - JPA + MariaDB
     - JWT（jjwt）
     - Actuator（health/info）
2. 新增 `AuthServiceApplication` 啟動入口
   - 讓 `auth-service` 可以獨立啟動，作為後續 Auth API 搬移目標。
3. 新增 `auth-service` 專屬 `application.yaml`
   - 建立 `spring.application.name=auth-service`
   - 先沿用現有 `security.jwt.*` 欄位（下一步再切 RS256）
   - 暴露 `management` 的 `health,info` 端點
4. 新增 `auth-service` Dockerfile
   - 兩階段建置（build/runtime），可直接產出獨立容器映像。

### 設計理由
- 先建立可獨立建置與啟動的 service 邊界，再搬移 Auth 邏輯，能降低一次性大改風險。
- 這一步不改 gateway/backend 既有行為，便於逐步驗證與回滾。

### 驗證結果
- 本步僅完成骨架建立，尚未接入 `login/refresh/logout` API，下一步會搬移 Auth 流程。

## 2026-03-31 - Step 5A-1A：補齊新增檔案中文註解與交付格式

### 對應需求
- 依使用者要求，新增程式碼需包含：
  - 檔案最上方用途說明
  - 每個段落開頭的中文註解
  - Dockerfile 同樣需可讀且解釋「為何這樣做」
- 每次改動回報需補上：
  - 變更內容
  - 小總結
  - commit 指令（使用 `(英文分類標籤)中文描述` 格式）

### 本次修改檔案
- `auth-service/pom.xml`（更新）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/AuthServiceApplication.java`（更新）
- `auth-service/src/main/resources/application.yaml`（更新）
- `auth-service/Dockerfile`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `auth-service/pom.xml`
   - 補上各段落中文註解：專案識別、Java 版本、依賴分類、打包插件用途。
2. `AuthServiceApplication.java`
   - 補上檔案用途說明與 `main` 段落用途說明。
3. `application.yaml`
   - 補上檔案用途與各區塊註解：`spring.application`、`datasource`、`jpa`、`security.jwt`、`management`。
4. `Dockerfile`
   - 補上 build/runtime 每段用途與每個關鍵指令的原因說明（快取、減少映像體積、保留 JVM 參數注入）。

### 小總結
- 本次不改功能，只補可讀性與可維護性註解，讓後續每一步變更更容易審核與交接。

## 2026-03-31 - Step 5A-2A：建立 auth-service 的 Auth 基礎層

### 對應清單項目
- `README.md` §12 Phase 2 細項 1：建立 `auth-service` 專案骨架後，先搬移登入/刷新/登出的共用基礎元件。

### 本次修改檔案
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/AuthServiceApplication.java`（更新）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/config/JwtProperties.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/config/CorsConfig.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/dto/AdminLoginRequest.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/dto/AdminLoginResponse.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/dto/ErrorResponse.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/entity/Admin.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/entity/RefreshToken.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/repository/AdminRepository.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/repository/RefreshTokenRepository.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/controller/GlobalExceptionHandler.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 啟用 JWT 設定綁定
   - 在 `AuthServiceApplication` 加上 `@EnableConfigurationProperties(JwtProperties.class)`。
2. 建立登入流程共用設定層
   - 新增 `JwtProperties`：集中管理 `security.jwt.*`。
   - 新增 `CorsConfig`：放行 `/api/**` 並允許 cookie 請求。
3. 建立 Auth API 共用資料結構
   - 新增 `AdminLoginRequest`、`AdminLoginResponse`、`ErrorResponse`。
4. 建立 Auth 所需資料模型與查詢介面
   - 新增 `Admin`、`RefreshToken` Entity。
   - 新增 `AdminRepository`、`RefreshTokenRepository`。
5. 建立統一錯誤處理
   - 新增 `GlobalExceptionHandler`，固定 400/409/500 回應格式。

### 分段原因說明
- `login/refresh/logout` 三支 API 會共享同一套 token 資料表、cookie 規則與錯誤格式，因此最終切流會「同步切換」。
- 但實作先拆成 `基礎層 -> API 邏輯 -> Gateway 路由切換`，是為了降低單次改動面，讓每一步都可獨立編譯與審核。
- 這樣可避免在同一步同時修改資料模型、業務邏輯、路由造成除錯困難，也符合「小步快跑但關聯項目一起改」。

### 驗證結果
- 已執行 `.\mvnw.cmd -q -DskipTests -f ..\auth-service\pom.xml package`，編譯成功。

### 小總結
- 本步先把 Auth 共用底座建好，下一步會接上 `login/refresh/logout` 的 service/controller 邏輯。

## 2026-03-31 - Step 5A-2B：實作 auth-service 的 login/refresh/logout 邏輯

### 對應清單項目
- `README.md` §12 Phase 2 細項 1：搬移登入/刷新/登出與 refresh token 核心流程到 `auth-service`。

### 本次修改檔案
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/controller/AdminAuthController.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/service/AdminService.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/security/JwtService.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/security/RefreshTokenService.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/config/SecurityConfig.java`（新增）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/config/AdminSeedRunner.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `AdminAuthController`
   - 實作 `/api/admin/login`：帳密驗證成功後回傳 access token，並寫入 refresh cookie。
   - 實作 `/api/admin/refresh`：以 refresh cookie 驗證後，旋轉 refresh token 並回傳新 access token。
   - 實作 `/api/admin/logout`：撤銷 refresh token 並清除 cookie。
2. 新增 `AdminService`
   - 封裝登入流程（查 admin、比對密碼、簽發 access/refresh token）。
3. 新增 `JwtService`
   - 提供 access token 簽發與解析能力（目前先沿用 HS256，RS256 在後續 Step 5B）。
4. 新增 `RefreshTokenService`
   - 提供 refresh token 建立/驗證/撤銷（資料庫僅保存 token hash）。
5. 新增 `SecurityConfig`
   - 放行 Auth API 與健康檢查端點，並提供 `PasswordEncoder`。
6. 新增 `AdminSeedRunner`
   - 服務啟動時可依 `ADMIN_USERNAME/ADMIN_PASSWORD` 自動補初始管理員。

### 分段原因說明（延續）
- 本步只完成 `auth-service` 內部 Auth 行為落地，尚未切換 gateway 路由。
- 這樣可以先驗證「新服務本身可編譯、可運行」，再進行跨服務流量切換，降低定位難度。
- 下一步會先補測試（Step 5A-3），再做路由切換與 backend 收斂。

### 驗證結果
- 本步新增邏輯後，已完成編譯驗證（見下方命令）。

### 小總結
- `auth-service` 已具備完整 `login/refresh/logout` 能力，現階段仍與既有流量隔離，便於下一步穩定切換。

## 2026-03-31 - Step 5A-3：補 auth-service 基本測試並驗證

### 對應清單項目
- `README.md` §12 Phase 2 細項 5（先建立最小回歸能力）：在切路由前先確認 Auth 核心邏輯可測且可執行。

### 本次修改檔案
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/AuthServiceApplicationTests.java`（新增）
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/security/JwtServiceTest.java`（新增）
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/service/AdminServiceTest.java`（新增）
- `auth-service/src/test/resources/application-test.yaml`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `AuthServiceApplicationTests`
   - 驗證 `auth-service` 在 `test` profile 下可啟動 Spring Context。
2. 新增 `JwtServiceTest`
   - 驗證可簽發並解析合法 token（subject/role）。
   - 驗證非法 token 會拋出例外。
3. 新增 `AdminServiceTest`
   - 驗證帳號不存在不發 token。
   - 驗證密碼錯誤不發 token。
   - 驗證登入成功時同時回傳 access/refresh token。
4. 新增 `application-test.yaml`
   - 使用 H2 記憶體資料庫隔離測試環境。
   - 提供 `security.jwt.*` 測試值，確保安全相關 Bean 可正常初始化。

### 分段原因說明（延續）
- 在 `Step 5A-2B` 完成 Auth 行為後，先補最小測試再切 gateway 路由，可先排除服務內部問題。
- 這可避免路由切換後才發現單元邏輯或設定錯誤，降低跨服務除錯成本。

### 驗證結果
- 已執行 `.\mvnw.cmd -q -f ..\auth-service\pom.xml test`，測試通過。
- 本步完成後，`auth-service` 已具備「可編譯 + 可測試」基礎，下一步可進入路由切換準備。

### 小總結
- Auth 核心功能已由測試覆蓋基本成功/失敗路徑，後續切流風險可控。

## 2026-03-31 - Step 5B-1：auth-service 先行切換 RS256

### 對應清單項目
- `README.md` §12 Phase 2 細項 2：導入 RS256（先在 Auth Service 完成簽章/驗章切換）。

### 本次修改檔案
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/config/JwtProperties.java`（更新）
- `auth-service/src/main/java/com/fy20047/tireordering/authservice/security/JwtService.java`（更新）
- `auth-service/src/main/resources/application.yaml`（更新）
- `auth-service/src/test/resources/application-test.yaml`（更新）
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/security/JwtServiceTest.java`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `JwtProperties` 改為 RS256 欄位
   - 由 `secret` 改為 `privateKey` + `publicKey`。
2. `JwtService` 改為 RS256
   - 簽章改用 `PrivateKey`。
   - 驗章改用 `PublicKey`。
   - 新增 PEM 解析流程（去標頭/尾、Base64 decode、RSA KeyFactory）。
   - PEM 的角色：把 RSA 金鑰物件序列化成可保存/傳遞的文字格式，程式啟動時再把 PEM 字串 parse 回 PrivateKey/PublicKey 物件。
   - HMAC 可以直接用字串，但 RSA 不行，RSA 必須先有真正金鑰，PEM 只是常見載體。
3. `application.yaml` 改為 RS256 環境變數
   - 新增 `security.jwt.private-key: ${JWT_PRIVATE_KEY}`
   - 新增 `security.jwt.public-key: ${JWT_PUBLIC_KEY}`
4. 測試設定與測試碼同步切換
   - `application-test.yaml` 改為測試用 RS256 key pair。
   - `JwtServiceTest` 改用 RS256 key pair 建立 `JwtProperties`。

### 分段原因說明
- 先只改 `auth-service` 的 RS256，是為了把「簽章服務改造」與「跨服務路由切換」拆開驗證。
- 這樣可先確認新 token 產生邏輯與測試都正常，再進一步改 gateway/backend 的驗章與路由。
- 可降低一次改太多層（Auth + Gateway + Backend）帶來的定位難度。

### 驗證結果
- 已執行 `.\mvnw.cmd -q -f ..\auth-service\pom.xml test`，測試通過。

### 小總結
- Auth Service 已從 HS256 切換為 RS256；下一步會把驗章端（gateway/backend）同步調整，完成整體閉環。

## 2026-03-31 - Step 5B-2：同步 backend 驗章設定至 RS256（gateway 無需程式變更）

### 對應清單項目
- `README.md` §12 Phase 2 細項 2：將驗章端同步切到 RS256。

### 本次修改檔案
- `backend/src/main/java/com/fy20047/tireordering/backend/config/JwtProperties.java`（更新）
- `backend/src/main/java/com/fy20047/tireordering/backend/security/JwtService.java`（更新）
- `backend/src/main/resources/application.yaml`（更新）
- `backend/src/test/resources/application-test.yaml`（更新）
- `backend/src/test/java/com/fy20047/tireordering/backend/security/JwtServiceTest.java`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `JwtProperties` 改為 RS256 欄位
   - 由 `secret` 改成 `privateKey` + `publicKey`。
2. `backend` 的 `JwtService` 改為 RS256
   - 簽章改用 `PrivateKey`。
   - 驗章改用 `PublicKey`。
   - 新增 PEM 解析流程（去頭尾、Base64 decode、RSA KeyFactory）。
3. `backend` 設定改為 RS256 環境變數
   - `security.jwt.private-key: ${JWT_PRIVATE_KEY}`
   - `security.jwt.public-key: ${JWT_PUBLIC_KEY}`
4. `backend` 測試設定與測試碼同步切換 RS256
   - `application-test.yaml` 改為測試用 key pair。
   - `JwtServiceTest` 改用 RS256 key pair 建立 `JwtProperties`。

### 分段原因說明（延續）
- 這一步聚焦在 `backend` 驗章端，確保它可驗證由 `auth-service`（RS256）簽發的 token。
- `gateway` 目前僅做 HTTP 轉發，尚未實作 JWT 驗章程式，因此本步無 gateway 程式碼調整。
- 路由切換（`/api/admin/login|refresh|logout` 指到 `auth-service`）會在下一階段 Step 5C 進行。

### 驗證結果
- 已執行 `backend` 全量測試：`.\mvnw.cmd -q test`，測試通過。

### 小總結
- 驗章端已完成 RS256 同步，接下來可安全進行 gateway 路由切換與 backend Auth 入口收斂。

## 2026-03-31 - Step 5C：切換 Gateway Auth 路由並收斂 backend Auth 入口

### 對應清單項目
- `README.md` §12 Phase 2 細項 3：`/api/admin/login|refresh|logout` 導向 `auth-service`。
- `README.md` §12 Phase 2 細項 4：backend 停用重複 Auth 入口。

### 本次修改檔案
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java`（更新）
- `api-gateway/src/main/resources/application.yaml`（更新）
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/AdminAuthController.java`（更新）
- `backend/src/main/resources/application.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. Gateway 新增分流策略（path-based routing）
   - `/api/admin/login`、`/api/admin/refresh`、`/api/admin/logout` 轉發到 `auth-service`。
   - 其餘 `/api/**` 維持轉發到 `backend`。
2. Gateway 新增設定值
   - `gateway.auth-base-url`（預設 `http://auth-service:8080`）。
   - 保留既有 `gateway.backend-base-url` 與 `gateway.frontend-login-url`。
3. backend 舊 Auth 入口預設停用
   - `AdminAuthController` 加上 `@ConditionalOnProperty(feature.backend-auth-endpoints-enabled=true)`。
   - `backend application.yaml` 新增 `feature.backend-auth-endpoints-enabled`，預設 `false`。

### 分段原因說明（延續）
- 本步先完成「流量路由」與「重複入口停用」兩件最直接影響邊界的改造。
- `docker-compose/k8s/.env` 的同步屬於部署層，放在 Step 5D 一次收斂，避免本步混入過多部署檔。
- 這可保持每一步都可被獨立驗證：本步只驗證程式路由與入口邏輯正確。

### 驗證結果
- 已執行 Gateway 編譯：
  - `.\mvnw.cmd -q -f ..\api-gateway\pom.xml -DskipTests package`
- 已執行 backend 全量測試：
  - `.\mvnw.cmd -q test`
- 以上皆通過。

### 小總結
- 對外 Auth 入口已由 Gateway 導向 `auth-service`，backend 重複 Auth 入口預設停用，服務邊界已落地。

## 2026-03-31 - Step 5D-1A：同步 Docker Compose/.env/Setup Guide（部署層第一段）

### 對應清單項目
- `README.md` §12 Phase 2 細項 6：更新部署與文件（`docker-compose`、`.env.example`、操作指引）。

### 本次修改檔案
- `infra/docker-compose.yml`（更新）
- `infra/docker-compose.prod.yml`（更新）
- `infra/.env.example`（更新）
- `SETUP_GUIDE.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `infra/docker-compose.yml`
   - `backend` 環境變數改為 `JWT_PRIVATE_KEY/JWT_PUBLIC_KEY`，並補上 refresh cookie 相關設定與 `BACKEND_AUTH_ENDPOINTS_ENABLED`。
   - 新增 `auth-service` 服務，承接 Auth API。
   - `api-gateway` 新增 `AUTH_BASE_URL=http://auth-service:8080`，讓 `/api/admin/login|refresh|logout` 可正確轉發。
2. `infra/docker-compose.prod.yml`
   - 同步 `backend` RS256 與 feature flag 環境變數。
   - 新增 `auth-service` 與 `api-gateway`（GHCR image），並讓 `frontend` 依賴 `api-gateway`。
3. `infra/.env.example`
   - 由 `JWT_SECRET` 改為 `JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY` 範本。
   - 新增 refresh cookie 相關設定與 `BACKEND_AUTH_ENDPOINTS_ENABLED=false` 預設值。
4. `SETUP_GUIDE.md`
   - Setup 說明改為 RS256 key（不再提 `JWT_SECRET`）。
   - 連線資訊改為 `http://localhost:8080/api/health`（經 Gateway）。
   - Kubernetes `app-secret` 建立指令改為 `JWT_PRIVATE_KEY/JWT_PUBLIC_KEY` + `BACKEND_AUTH_ENDPOINTS_ENABLED`。
   - 部署資源描述補上 `API Gateway`，rollout 範例補 `api-gateway`。

### 分段原因說明
- Step 5D 涉及 compose、k8s、文件、驗證腳本，改動面很廣。
- 先完成 `5D-1A`（compose + env + setup guide）可先確保「本機/部署啟動參數」與 Step 5C 路由邏輯一致，再進入 `5D-1B` 的 K8s 與 smoke 收尾。
- 這樣能把「啟動配置問題」與「K8s 路由/密鑰問題」分開排查，降低除錯耦合。

### 驗證結果
- 已執行：
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env.example config`
  - `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.example config`
- 兩者皆可成功解析，確認 compose 結構與新環境變數對應正確。

### 小總結
- Step 5D 第一段已完成：本機與部署入口都已具備 `backend + auth-service + api-gateway` 的必要設定，且 RS256 參數來源一致。

## 2026-03-31 - Step 5D-1A-L：同步本機 `infra/.env`（Local only）

### 對應需求
- 使用者要求：雖不 push，也要同步修改本機 `infra/.env`。

### 本次修改檔案
- `infra/.env`（本機檔，更新，不入版控）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 將 `JWT_SECRET` 移除，改為 `JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY`。
2. 補齊 JWT 相關參數：
   - `JWT_EXPIRATION_SECONDS`
   - `JWT_REFRESH_EXPIRATION_SECONDS`
   - `JWT_REFRESH_COOKIE_NAME`
   - `JWT_REFRESH_COOKIE_SECURE`
   - `JWT_REFRESH_COOKIE_SAME_SITE`
3. 補上 `BACKEND_AUTH_ENDPOINTS_ENABLED=false`，讓本機行為與 Step 5C/5D 設定一致。

### 分段原因說明
- `infra/.env.example` 屬於版控模板，`infra/.env` 屬於你的本機實際執行值。
- 兩者需同步，否則會出現「模板是 RS256、實際執行仍是 HS256」的落差，造成本機啟動或測試結果偏差。

### 備註
- `infra/.env` 受 `.gitignore` 規則保護，預期不會被 commit/push。

## 2026-03-31 - Step 5D-1B：同步 K8s 基礎資源（auth-service + gateway env + overlay 對齊）

### 對應清單項目
- `README.md` §12 Phase 2 細項 6：更新部署與文件（K8s 部署資源同步）。

### 本次修改檔案
- `k8s/base/auth-deployment.yaml`（新增）
- `k8s/base/auth-service.yaml`（新增）
- `k8s/base/gateway-deployment.yaml`（更新）
- `k8s/base/kustomization.yaml`（更新）
- `k8s/overlays/minikube/auth-configmap-env.yaml`（新增）
- `k8s/overlays/minikube/app-config.yaml`（更新）
- `k8s/overlays/minikube/backend-configmap-env.yaml`（更新）
- `k8s/overlays/minikube/kustomization.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `auth-service` K8s 基礎資源
   - 新增 `auth-deployment.yaml`：定義 image、probe、`app-secret`/`db-secret` 來源。
   - 新增 `auth-service.yaml`：提供 cluster 內 `auth-service:8080` 服務名稱。
2. 更新 Gateway base deployment
   - 新增 `AUTH_BASE_URL=http://auth-service:8080`。
   - 保留 `BACKEND_BASE_URL=http://backend:8080`，維持 tire/order API 先走 backend。
3. 更新 base kustomization
   - 納入 `auth-service.yaml`、`auth-deployment.yaml`。
4. 更新 minikube overlay
   - 新增 `auth-configmap-env.yaml`，讓 auth-service 的 DB/JWT 非機密參數由 `app-config` 注入。
   - `app-config.yaml` 新增 `BACKEND_AUTH_ENDPOINTS_ENABLED=false`。
   - `backend-configmap-env.yaml` 新增 `BACKEND_AUTH_ENDPOINTS_ENABLED` 注入。
   - `kustomization.yaml` 新增 `auth-service` image 與 `auth-configmap-env.yaml` patch。

### 分段原因說明
- Step 5D-1A 先完成 compose/.env；Step 5D-1B 再專注 K8s，避免部署層一次混改太多面向。
- K8s 本步先收斂「服務拓樸與路由參數」：補 auth-service 資源、補 gateway auth 路由參數、補 overlay 注入規則。
- `app-sealedsecret.yaml` 的密文重封屬於 cluster-key 相依作業（需用目標叢集重新 kubeseal），因此放在下一小步以操作指引方式明確執行，避免提交無法解密的假密文。

### 驗證結果
- 已執行：
  - `kubectl kustomize k8s/base`
  - `kubectl kustomize k8s/overlays/minikube`
- 兩者皆可成功輸出，確認新增 `auth-service` 與更新後的 env/route 設定可被 kustomize 正確組裝。

### 小總結
- K8s 佈署拓樸已對齊 Phase 2：Gateway 可分流到 `auth-service`，且 minikube overlay 已補齊 auth-service 與 backend 的必要參數注入結構。

## 2026-03-31 - Step 5D-1C：重封 `app-sealedsecret`（JWT_SECRET -> RS256 key pair）

### 對應清單項目
- `README.md` §12 Phase 2 細項 6：更新部署與文件（K8s secret/SealedSecret 同步）。

### 本次修改檔案
- `k8s/overlays/minikube/app-sealedsecret.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 啟動 minikube 並確認 sealed-secrets controller 可用。
2. 安裝 `kubeseal`（本機工具，`tools/kubeseal/kubeseal.exe`，不入版控）。
3. 以本機 `infra/.env` 值重建 `app-secret` 明文（dry-run）：
   - `ADMIN_USERNAME`
   - `ADMIN_PASSWORD`
   - `JWT_PRIVATE_KEY`
   - `JWT_PUBLIC_KEY`
   - `BACKEND_AUTH_ENDPOINTS_ENABLED`
4. 使用 `kubeseal` 重新封裝，覆蓋 `k8s/overlays/minikube/app-sealedsecret.yaml`。
5. 套用後驗證 `app-secret`（叢集內）已不含 `JWT_SECRET`，改為 RS256 欄位。

### 分段原因說明
- SealedSecret 密文與叢集 controller 金鑰綁定，不能只靠文字替換 `JWT_SECRET` 為 `JWT_PRIVATE_KEY`。
- 必須用「目標叢集」重新 seal 一次，才能確保 controller 能成功 unseal。
- 因此 Step 5D-1C 獨立成一小步，專注處理密鑰封裝與驗證，避免與 Deployment/Route 變更混在同一步。

### 驗證結果
- 已執行：
  - `kubectl apply -f k8s/overlays/minikube/app-sealedsecret.yaml`
  - `kubectl -n tire-ordering get secret app-secret -o json`（檢查 data keys）
- 驗證結果：`app-secret` keys 為
  - `ADMIN_USERNAME`
  - `ADMIN_PASSWORD`
  - `JWT_PRIVATE_KEY`
  - `JWT_PUBLIC_KEY`
  - `BACKEND_AUTH_ENDPOINTS_ENABLED`
- 已確認不再包含 `JWT_SECRET`。

### 小總結
- K8s secret 體系已完成 RS256 轉換，後續 overlay 套用時可由 SealedSecret 直接還原正確的 JWT key pair。

## 2026-03-31 - Security Hotfix S1：Private Key 外洩緊急處置（一步到位）

### 事件背景
- 收到 GitGuardian 告警：`Generic Private Key exposed on GitHub`（2026-03-31 07:12:11 UTC）。
- 影響範圍包含測試檔案中的固定 RSA private key；且本機/叢集當時沿用同組 key，必須立即輪替。

### 本次修改檔案
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/support/TestRsaKeyPairFactory.java`（新增）
- `backend/src/test/java/com/fy20047/tireordering/backend/support/TestRsaKeyPairFactory.java`（新增）
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/security/JwtServiceTest.java`（更新）
- `backend/src/test/java/com/fy20047/tireordering/backend/security/JwtServiceTest.java`（更新）
- `auth-service/src/test/java/com/fy20047/tireordering/authservice/AuthServiceApplicationTests.java`（更新）
- `backend/src/test/java/com/fy20047/tireordering/backend/BackendApplicationTests.java`（更新）
- `auth-service/src/test/resources/application-test.yaml`（更新）
- `backend/src/test/resources/application-test.yaml`（更新）
- `infra/.env`（本機檔，更新，不入版控）
- `k8s/overlays/minikube/app-sealedsecret.yaml`（更新）
- `infra/.env.example`（更新）
- `SETUP_GUIDE.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 移除 repository 中固定私鑰
   - 新增兩個測試工具 `TestRsaKeyPairFactory`（auth-service/backend 各一份），改為測試執行時動態產生 RSA key pair。
   - `JwtServiceTest` 不再保存硬編碼 PEM 私鑰，改用動態產生 key。
2. 調整 Spring Context 測試注入方式
   - `AuthServiceApplicationTests`、`BackendApplicationTests` 新增 `@DynamicPropertySource`，在 context 啟動前注入動態 JWT key。
   - `application-test.yaml` 改為測試注入占位，避免保存任何固定 private key。
3. 立即輪替執行環境金鑰
   - 產生全新 RSA key pair，更新本機 `infra/.env` 的 `JWT_PRIVATE_KEY/JWT_PUBLIC_KEY`（local only）。
   - 依新 `.env` 值重新封裝 `app-sealedsecret.yaml` 並套用至 minikube。
4. 降低後續誤報與誤用風險
   - `infra/.env.example`、`SETUP_GUIDE.md` 改為通用占位字串，不再放 `BEGIN ... KEY` 形式範例。

### 驗證結果
- 關鍵字掃描：
  - `git grep "PRIVATE KEY PEM marker"` → 無結果
  - `git grep "PUBLIC KEY PEM marker"` → 無結果
- 測試驗證：
  - `.\backend\mvnw.cmd -q -f .\auth-service\pom.xml test` 通過
  - `.\backend\mvnw.cmd -q -f .\backend\pom.xml test` 通過
- SealedSecret 驗證：
  - `kubectl apply -f k8s/overlays/minikube/app-sealedsecret.yaml` 成功
  - `app-secret` keys 已為 `JWT_PRIVATE_KEY/JWT_PUBLIC_KEY`（不含 `JWT_SECRET`）

### 分段原因說明
- 這次屬於資安事件，需同時完成三件事才算止血：
  - 移除 repo HEAD 明文私鑰
  - 輪替實際使用中的 JWT key
  - 驗證服務測試與 secret 封裝流程仍可運作
- 若只刪測試檔內容但不輪替 key，已外洩金鑰仍可被利用；若只輪替 key 但不改測試，後續仍會再次外洩。

### 小總結
- 已完成「移除明文私鑰 + 輪替 JWT key + 重封 SealedSecret + 測試驗證」的一次性熱修；目前 HEAD 不再含 private key 明文。
## 2026-03-31 - Step 5D-2：K8s 部署驗證與 Phase 2 Smoke 收尾

### 對應目標
- `README.md` 第 12 節 Phase 2 細項 5：完成 login/refresh/logout + admin API + 失敗情境 smoke。
- `README.md` 第 12 節 Phase 2 細項 6：完成 minikube 部署面同步與實機驗證。

### 相關變更檔案
- `k8s/overlays/minikube/backend-resources.yaml`
- `k8s/overlays/minikube/hpa-backend.yaml`
- `k8s/overlays/minikube/hpa-frontend.yaml`
- `MODIFICATION_HISTORY.md`

### 分段原因（5D-2A / 5D-2B）
1. 先做 5D-2A（部署穩定化）
   - 既有 cluster 中 `backend` 沿用 `imagePullPolicy=Always`，會反覆拉到舊版 GHCR 映像，導致 RS256 驗章流程無法對齊。
   - HPA `minReplicas=2` 搭配 ResourceQuota（`limits.cpu=2500m`）時，rollout 期間容易因暫時副本擠壓 quota，造成 `auth-service` 排程失敗。
2. 再做 5D-2B（smoke 驗證）
   - 先把部署層風險收斂，再做 API smoke，避免把「資源/排程問題」與「功能問題」混在一起排查。

### 實作內容
1. `backend-resources.yaml`
   - 新增 `imagePullPolicy: IfNotPresent`，避免 minikube 本機驗證時被遠端舊映像覆蓋。
2. `hpa-backend.yaml`、`hpa-frontend.yaml`
   - 將 `minReplicas` 由 `2` 下修為 `1`，保留自動擴縮能力，同時降低 Auth 拆出後的 quota 壓力。
3. 本機驗證流程（執行層）
   - `kubectl apply -k k8s/overlays/minikube`
   - 使用 minikube docker daemon 建立 `backend:local-rs256`，並以 `kubectl set image` 套用到 `backend` deployment（僅本機驗證用途，未改 repository 內 image tag）。
   - 以 `scripts/smoke/run-smoke-gateway.ps1` 對 gateway 跑完整 smoke。

### 驗證結果
- smoke 全數通過：
  - login / refresh / logout
  - public tires / create order
  - admin list orders / patch order status
  - logout 後 refresh 失敗
  - 無 token / 無 cookie 失敗情境
- 結論：Phase 2 的 Auth 入口切分 + RS256 驗章鏈路在 minikube 實測可用。

## 2026-03-31 - Step 5D-3：補 Phase 2 最終技術總結（README）

### 對應目標
- 依使用者要求，在 Phase 2 末段補一段「技術面完成內容」總結，讓交付範圍可快速回顧。

### 相關變更檔案
- `README.md`
- `MODIFICATION_HISTORY.md`

### 實作內容
1. 在 `README.md` 的「Phase 2 完成判準」後新增「Phase 2 技術總結（已完成）」段落。
2. 摘要整理 6 個技術面向：
   - 服務切分與 Gateway 分流
   - RS256 簽章/驗章角色分工
   - backend Auth 邊界收斂
   - 部署與密鑰配置同步
   - compile/test/smoke 驗證覆蓋
   - 密鑰外洩熱修與風險控制

### 驗證結果
- 文件結構已更新，Phase 2 區塊可直接看到完成判準與最終技術摘要。
- 本步僅文件補充，未改動程式邏輯與執行參數。

## 2026-03-31 - Step 6A：建立 Phase 3 執行清單（README §12）

### 對應目標
- 使用者要求：依 `README.md` 的 Phase 3 規劃開始執行，並先把計畫細拆寫入第 12 節執行清單。

### 本次修改檔案
- `README.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 在 `README.md` 第 12 節新增「Phase 3 細項（抽 Tire Service）」8 個可執行步驟：
   - `tire-service` 骨架建立
   - Tire 核心領域搬移
   - 公開 Tire API 搬移
   - 後台 Tire API 搬移
   - Gateway 路由分流
   - backend 重複入口停用
   - 部署與文件同步
   - Phase 3 smoke 驗證
2. 新增「Phase 3 完成判準」3 條：
   - Tire API 對外單一出口為 `tire-service`（經 Gateway）
   - 查詢與後台管理不再依賴 backend Tire Controller
   - Phase 3 smoke 全數通過且前端路徑不變

### 分段原因說明
- 先補「執行清單」再改程式碼，可讓每個後續 commit 都能對齊明確步驟與驗證點。
- 這一步只動文件，降低一次改太多檔案的風險，符合逐步推進要求。

### 驗證結果
- 已檢查 `README.md` 第 12 節，Phase 3 區塊與完成判準皆已落在執行清單中。
- 本步未修改任何服務邏輯或部署參數。

### 小總結
- Phase 3 的路線圖已正式落在執行清單，接下來可按步驟進入 `tire-service` 骨架實作。

## 2026-03-31 - Step 6B：建立 tire-service 專案骨架（Phase 3 第一步）

### 對應清單項目
- `README.md` §12 Phase 3 細項 1：建立 `tire-service` 專案骨架（Spring MVC + JPA + Security 驗章），先確保可獨立 build/run。

### 本次修改檔案
- `tire-service/pom.xml`（新增）
- `tire-service/Dockerfile`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/TireServiceApplication.java`（新增）
- `tire-service/src/main/resources/application.yaml`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `tire-service` Maven 專案骨架
   - 建立 `pom.xml`，納入 Phase 3 後續搬移所需基礎依賴：
     - Spring MVC、Data JPA、Validation、Security、Actuator
     - JWT（jjwt-api/impl/jackson）
     - MariaDB Driver、Lombok、測試依賴（Spring Test/H2）
2. 新增 `TireServiceApplication` 啟動入口
   - 讓 `tire-service` 可獨立啟動，作為後續搬移 Tire Controller/Service/Repository 的承接點。
3. 新增 `application.yaml` 基礎設定
   - 服務名稱：`tire-service`
   - datasource：沿用既有 MariaDB 參數格式（先拆邊界，不先改資料庫策略）
   - JPA：`ddl-auto: update`
   - security.jwt：預留 RS256 `private-key/public-key` 與 token 期限參數
   - management：開放 `health,info`
4. 新增 `Dockerfile`
   - 使用兩階段建置（build/runtime），減少 runtime 映像體積
   - 保留 `JAVA_OPTS` 注入點，方便部署時調整 JVM 參數

### 註解規範對齊
- 本步新增檔案皆已補上中文註解：
  - 檔案最上方用途說明
  - 每段區塊開頭用途說明
  - `Dockerfile` 各段落含「為何這樣做」的說明

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -f ..\tire-service\pom.xml -DskipTests package`
- 結果：成功。

### 小總結
- Phase 3 已正式進入程式碼階段，`tire-service` 骨架可獨立 build；下一步可開始搬移 Tire 領域核心類別。

## 2026-03-31 - Step 6C：搬移 Tire 核心領域類別到 tire-service

### 對應清單項目
- `README.md` §12 Phase 3 細項 2：搬移 Tire 領域核心（`Tire`、`TireRepository`、`TireService`），維持既有資料表與查詢行為。

### 本次修改檔案
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/entity/Tire.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/repository/TireRepository.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/service/TireService.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 `Tire` Entity
   - 對應資料表 `tires`
   - 保留欄位定義：`brand/series/origin/size/price/is_active/created_at/updated_at`
   - 保留 `@PrePersist/@PreUpdate` 時間戳記邏輯
2. 新增 `TireRepository`
   - 保留 `findActiveTires()` 查詢（僅上架 + 固定排序）
   - 保留 `search(...)` 條件查詢（brand/series/size/active，空值可略過）
3. 新增 `TireService`
   - 保留公開查詢：`getActiveTires()`、`getAllTires()`
   - 保留後台維護：`searchTires()`、`createTire()`、`updateTire()`、`updateActiveStatus()`
   - 保留 `getTireById()` 找不到時拋出 `IllegalArgumentException("Tire not found")`
   - 保留查詢輸入正規化 `normalize()` 行為

### 註解規範對齊
- 本步新增的 3 份程式檔案皆補齊中文註解：
  - 檔案最上方用途說明
  - 各段（欄位、查詢、方法）開頭用途說明

### 分段原因說明
- 先只搬移「核心領域層」，暫不搬 Controller 與 DTO，可把風險控制在資料模型與服務邏輯範圍。
- 這樣下一步搬 API 時，僅需處理 controller/response mapping 與安全放行，不會同時混入資料層變動。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -f ..\tire-service\pom.xml -DskipTests package`
- 結果：成功。

### 小總結
- `tire-service` 已具備獨立的 Tire 核心領域能力；下一步可搬移公開 Tire API（`/api/tires`、`/api/tires/{id}`）。

## 2026-03-31 - Step 6D：搬移公開 Tire API 到 tire-service

### 對應清單項目
- `README.md` §12 Phase 3 細項 3：搬移公開輪胎 API（`/api/tires`、`/api/tires/{id}`），含 DTO 與例外處理。

### 本次修改檔案
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/TireResponse.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/TireListResponse.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/ErrorResponse.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/controller/TireController.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/controller/GlobalExceptionHandler.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增公開查詢 DTO
   - `TireResponse`：單筆輪胎資料回應
   - `TireListResponse`：列表包裝回應（`items`）
2. 新增統一錯誤 DTO
   - `ErrorResponse`：`message + details` 格式，對齊現有前端錯誤處理模式
3. 新增 `TireController`
   - `GET /api/tires`：支援 `active` 參數（預設 `true`）
   - `GET /api/tires/{id}`：單筆輪胎查詢
   - 內含 Entity -> DTO 轉換方法，保持回應欄位一致
4. 新增 `GlobalExceptionHandler`
   - 統一處理驗證錯誤、型別錯誤、業務錯誤與未預期例外
   - 狀態碼維持 `400/409/500` 對應規則

### 註解規範對齊
- 本步新增的所有 Java 檔案皆含中文註解：
  - 檔案用途說明
  - 各段 API/方法用途說明

### 分段原因說明
- 先只搬公開 Tire API，不把後台管理 API 混進同一步，可讓驗證聚焦在「前台查詢流程」。
- 後台 API（admin tires）下一步再搬移，避免一次改動太多 DTO 與驗證規則。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -f ..\tire-service\pom.xml -DskipTests package`
- 結果：成功。

### 小總結
- `tire-service` 已可獨立承接公開輪胎查詢 API；下一步可搬移後台輪胎管理 API。

## 2026-03-31 - Step 6E：搬移後台 Tire 管理 API 到 tire-service

### 對應清單項目
- `README.md` §12 Phase 3 細項 4：搬移後台輪胎 API（`/api/admin/tires`、`/api/admin/tires/{id}`、`/api/admin/tires/{id}/active`），含 DTO 與驗證規則。

### 本次修改檔案
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/AdminTireRequest.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/AdminTireResponse.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/AdminTireListResponse.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/dto/UpdateTireStatusRequest.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/controller/AdminTireController.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增後台管理 DTO
   - `AdminTireRequest`：新增/編輯請求，保留 `@NotBlank/@Size/@Min/@NotNull` 驗證
   - `AdminTireResponse`：單筆管理回應（含 `createdAt/updatedAt`）
   - `AdminTireListResponse`：列表包裝回應（`items`）
   - `UpdateTireStatusRequest`：上下架切換請求（僅 `isActive`）
2. 新增 `AdminTireController`
   - `GET /api/admin/tires`：支援品牌/系列/尺寸/上下架條件查詢
   - `POST /api/admin/tires`：新增輪胎（回傳 `201 Created`）
   - `PUT /api/admin/tires/{id}`：更新輪胎
   - `PATCH /api/admin/tires/{id}/active`：切換上下架
   - 保留 DTO/Entity 轉換與字串 trim 行為，對齊既有 backend 邏輯

### 註解規範對齊
- 本步新增檔案皆有中文註解：
  - 檔案最上方用途說明
  - API 與方法段落用途說明

### 分段原因說明
- 先把後台 API 也搬進 `tire-service`，但暫不切換 Gateway，能先完成「服務內功能對齊」。
- 下一步再處理路由分流與 backend 重複入口收斂，可把功能問題與路由問題分開排查。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -f ..\tire-service\pom.xml -DskipTests package`
- 結果：成功。

### 小總結
- `tire-service` 已具備公開 + 後台完整 Tire API；下一步可進入 Gateway 分流調整與 backend 邊界收斂。

## 2026-03-31 - Step 6F：Gateway 路由分流到 tire-service

### 對應清單項目
- `README.md` §12 Phase 3 細項 5：調整 Gateway 分流，將 `/api/tires/**` 與 `/api/admin/tires/**` 導向 `tire-service`。

### 本次修改檔案
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java`（更新）
- `api-gateway/src/main/resources/application.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 擴充 Gateway 目標服務設定
   - 新增 `gateway.tire-base-url`（環境變數 `TIRE_BASE_URL`，預設 `http://tire-service:8080`）
2. 調整 `ApiProxyController` 分流規則
   - 保持 Auth 入口優先：
     - `/api/admin/login`
     - `/api/admin/refresh`
     - `/api/admin/logout`
   - 新增 Tire 路由分流：
     - `/api/tires`
     - `/api/tires/**`
     - `/api/admin/tires`
     - `/api/admin/tires/**`
   - 其餘路徑維持導向 `backend`
3. 補強路徑比對工具方法
   - 新增 `normalizePath(...)`，統一處理結尾斜線
   - 新增 `matchesPathPrefix(...)`，避免 `/api/admin/**` 類過寬規則誤吃 Auth 路徑

### 分段原因說明
- 這一步只處理 Gateway 程式分流邏輯，不混入 compose/k8s 佈署調整，保持改動面可控。
- 先在程式層完成正確分流規則，再於下一步收斂 backend 重複入口與部署同步，便於逐段驗證與回滾。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -f ..\api-gateway\pom.xml -DskipTests package`
- 結果：成功。

### 注意事項
- 本步尚未同步 `docker-compose` / `k8s` 的 `tire-service` 連線設定與部署清單。
- 本步也尚未在 `backend` 停用重複 Tire API 入口（會在後續步驟處理）。

### 小總結
- Gateway 已具備 Tire 路徑分流能力，且不會誤導 Auth 入口；下一步可進行 backend Tire 入口收斂。

## 2026-03-31 - Step 6G：收斂 backend 重複 Tire API 入口

### 對應清單項目
- `README.md` §12 Phase 3 細項 6：調整 backend 邊界，停用重複 Tire API 入口，保留 order 與其相依資料模型。

### 本次修改檔案
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/TireController.java`（更新）
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/AdminTireController.java`（更新）
- `backend/src/main/resources/application.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 停用 backend 公開 Tire 入口（預設）
   - `TireController` 新增 `@ConditionalOnProperty`
   - 條件鍵值：`feature.backend-tire-endpoints-enabled=true` 才啟用
2. 停用 backend 後台 Tire 入口（預設）
   - `AdminTireController` 新增同樣條件註解
   - 讓 `/api/admin/tires/**` 預設不再由 backend 對外提供
3. 新增可回切設定
   - `application.yaml` 新增：
     - `feature.backend-tire-endpoints-enabled: ${BACKEND_TIRE_ENDPOINTS_ENABLED:false}`
   - 預設 `false`（符合 Phase 3 邊界收斂目標）
   - 需要緊急回切時，可透過 `BACKEND_TIRE_ENDPOINTS_ENABLED=true` 臨時恢復 backend Tire 入口

### 分段原因說明
- 先用 feature flag 收斂重複入口，而不是直接刪除 controller，可保留回滾彈性。
- 此步只調整 backend 對外入口，不動 order 相關 entity/service/repository，避免提早牽動 Phase 4。

### 驗證結果
- 已執行 backend 測試：
  - `.\mvnw.cmd -q test`
- 結果：成功。

### 小總結
- backend 的重複 Tire API 入口已可預設關閉，服務邊界開始收斂到 `tire-service`；下一步可同步部署層設定與 smoke 驗證。

## 2026-03-31 - Step 6H：同步部署層（compose/k8s/env）並完成 Phase 3 Smoke 驗證

### 對應清單項目
- `README.md` §12 Phase 3 細項 7：更新部署與文件（`docker-compose`、`k8s`、`.env.example`）。
- `README.md` §12 Phase 3 細項 8：補 Phase 3 smoke（公開查詢、後台 CRUD/上下架、授權失敗路徑、Gateway 路由正確性）。

### 本次修改檔案
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/TireServiceApplication.java`（更新）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/config/JwtProperties.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/config/SecurityConfig.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/security/JwtService.java`（新增）
- `tire-service/src/main/java/com/fy20047/tireordering/tireservice/security/JwtAuthenticationFilter.java`（新增）
- `infra/docker-compose.yml`（更新）
- `infra/docker-compose.prod.yml`（更新）
- `infra/.env.example`（更新）
- `infra/.env`（本機檔，更新，不入版控）
- `k8s/base/tire-deployment.yaml`（新增）
- `k8s/base/tire-service.yaml`（新增）
- `k8s/base/kustomization.yaml`（更新）
- `k8s/base/gateway-deployment.yaml`（更新）
- `k8s/overlays/minikube/tire-configmap-env.yaml`（新增）
- `k8s/overlays/minikube/app-config.yaml`（更新）
- `k8s/overlays/minikube/backend-configmap-env.yaml`（更新）
- `k8s/overlays/minikube/kustomization.yaml`（更新）
- `scripts/smoke/run-smoke-gateway.ps1`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 補齊 `tire-service` 安全層（作為分流後可用前置）
   - 新增 JWT 設定綁定、JWT 驗章服務與 JWT 過濾器。
   - 新增 `SecurityConfig`：
     - 放行 `/api/tires/**` 與健康檢查端點。
     - 限制 `/api/admin/**` 需 `ROLE_ADMIN`。
   - 在啟動類別啟用 `@EnableConfigurationProperties(JwtProperties.class)`。
2. 同步 Docker Compose（dev/prod）
   - 新增 `tire-service` 服務與必要環境變數。
   - Gateway 新增 `TIRE_BASE_URL=http://tire-service:8080`。
   - backend 新增 `BACKEND_TIRE_ENDPOINTS_ENABLED`（預設 `false`）。
   - `infra/.env.example` 與本機 `infra/.env` 同步新增 `BACKEND_TIRE_ENDPOINTS_ENABLED=false`。
3. 同步 Kubernetes（base + minikube overlay）
   - base 新增 `tire-service` Deployment/Service。
   - Gateway Deployment 新增 `TIRE_BASE_URL`。
   - overlay 新增 `tire-configmap-env.yaml`，以 `app-config` 注入 tire-service 非機密參數。
   - `app-config.yaml` 新增 `BACKEND_TIRE_ENDPOINTS_ENABLED`，並在 `backend-configmap-env.yaml` 注入對應 env。
   - `kustomization.yaml` 新增 `tire-service` image 與 patch。
4. 更新 Phase 3 Smoke 腳本
   - 在既有 smoke 基礎上新增 `tire-service` 路徑驗證：
     - `GET /api/admin/tires`（帶 token 成功）
     - `GET /api/admin/tires`（無 token 403）
     - `POST /api/admin/tires` 新增輪胎
     - `PUT /api/admin/tires/{id}` 更新輪胎
     - `PATCH /api/admin/tires/{id}/active` 上下架切換
   - 保留既有 login/refresh/logout、公開輪胎查詢、建單、後台訂單流程驗證。

### 註解規範對齊
- 本步新增程式檔案均補齊中文註解：
  - 檔案用途說明
  - 各段方法/規則用途說明
- `k8s` 新增資源檔亦補上中文用途註解。

### 驗證結果
- 部署設定語法驗證：
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env.example config`
  - `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.example config`
  - `kubectl kustomize k8s/base`
  - `kubectl kustomize k8s/overlays/minikube`
- 本機實跑驗證：
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\smoke\run-smoke-gateway.ps1 -BaseUrl "http://localhost:8080" -AdminUsername "<local>" -AdminPassword "<local>"`
- 結果：Smoke 全部通過（包含新增的 Tire 管理路徑案例）。

### 小總結
- Phase 3 的「程式分流 + 部署配置 + 端到端 smoke」已完成一輪閉環驗證，Gateway 轉發到 `tire-service` 的公開與後台輪胎路徑可正常運作。

## 2026-04-01 - Step 6I：Phase 3 結案文件收尾 + Phase 4 規劃

### 對應需求
- 使用者要求：
  1. 說明 `auth` 與 `tire` 拆分差異，以及 `order` 拆分與 JWT 角色關係。
  2. 補齊 Phase 3 結案文件。
  3. 規劃 Phase 4 執行內容。

### 本次修改檔案
- `README.md`（更新）
- `SETUP_GUIDE.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `README.md` 第 12 節補齊 Phase 3 結案內容
   - 新增「Phase 3 技術總結（已完成）」：
     - 服務切分、JWT 驗章角色、backend 邊界收斂、部署同步、smoke 驗證覆蓋
2. `README.md` 第 12 節新增 Phase 4 規劃
   - 新增「Phase 4 細項（抽 Order Service + Snapshot）」9 個執行步驟
   - 新增「Phase 4 完成判準」3 條（單一出口、snapshot 解耦、smoke 通過）
3. `SETUP_GUIDE.md` 同步 Phase 3 結案操作資訊
   - 新增「目前服務拓樸（Phase 3 結案）」路由流向說明
   - 新增「重要環境變數（Phase 3）」鍵值整理
   - 新增「Phase 3 Smoke 驗證」執行指令與驗證重點
   - Kubernetes 部署與 rollout 範例補上 `auth-service`、`tire-service`

### 分段原因說明
- Phase 3 功能已完成且 smoke 通過，需補齊文件才能正式收斂階段成果。
- Phase 4 是下一個高風險拆分（Order + Snapshot），先在 README 第 12 節細拆執行步驟，可避免一次大改造成驗證盲區。

### 驗證結果
- 已檢查：
  - `README.md` 第 12 節已出現 Phase 3 結案總結與 Phase 4 細項/判準
  - `SETUP_GUIDE.md` 已補齊 Phase 3 路由拓樸、環境變數與 smoke 指令
- 本步為文件更新，未改動執行邏輯與部署參數。

### 小總結
- Phase 3 已完成文件層結案，Phase 4 已有可執行的分步規劃，可直接進入下一階段實作。

## 2026-04-01 - Step 6J：補充 Snapshot 概念與實作說明（Phase 4 文件加強）

### 對應需求
- 使用者要求：將 Snapshot 重要概念寫入文件，並說明實作方式。

### 本次修改檔案
- `README.md`（更新）
- `SETUP_GUIDE.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `README.md`
   - 在 Phase 4 區塊新增「Snapshot 概念與實作方式」：
     - 為何需要 snapshot
     - 服務邊界歸屬（snapshot 屬於 order-service）
     - 建議資料模型欄位
     - 建單流程實作順序
     - Phase 4 smoke 驗證重點
2. `SETUP_GUIDE.md`
   - 新增「Phase 4 Snapshot 設計說明（開工前必讀）」：
     - 概念、角色分工、實作重點與回歸驗證方式

### 分段原因說明
- Snapshot 是 Phase 4 的關鍵資料一致性策略，若未先在文件明確定義，實作時容易回到「即時 join 主檔」導致歷史污染。
- 先把概念與驗證標準寫清楚，可降低後續 API/DB 改造爭議與回歸風險。

### 驗證結果
- 已確認 `README.md` 與 `SETUP_GUIDE.md` 皆可直接看到 Snapshot 的「原因 + 做法 + 驗證」三段說明。
- 本步為文件更新，未更動程式邏輯。

### 小總結
- Snapshot 的設計原則與實作路徑已文件化，Phase 4 可依此直接落地開發與驗證。

## 2026-04-01 - Step 7A：建立 order-service 專案骨架（Phase 4 起手式）

### 對應清單項目
- `README.md` §12 Phase 4 細項 1：建立 `order-service` 專案骨架（Spring MVC + JPA + Security 驗章），先確保可獨立 build/run。

### 本次修改檔案
- `order-service/pom.xml`（新增）
- `order-service/Dockerfile`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/OrderServiceApplication.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/config/JwtProperties.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/config/SecurityConfig.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/security/JwtService.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/security/JwtAuthenticationFilter.java`（新增）
- `order-service/src/main/resources/application.yaml`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 建立 `order-service` Maven 專案骨架
   - 新增 `pom.xml`，依賴包含 `web`、`data-jpa`、`validation`、`security`、`actuator`、`jjwt`、`mariadb`、`test`。
   - Java 版本與現有服務對齊為 `21`，維持一致建置基線。
2. 建立 Spring Boot 啟動與設定檔
   - 新增 `OrderServiceApplication` 作為啟動入口，並啟用 `JwtProperties`。
   - 新增 `application.yaml`，先放服務名稱、資料庫、JPA、JWT 與 actuator 基礎設定。
3. 建立 JWT 驗章安全骨架
   - 新增 `JwtProperties`（綁定 `security.jwt`）。
   - 新增 `JwtService`（RS256 public key 驗章解析）。
   - 新增 `JwtAuthenticationFilter`（解析 Bearer token 並建立 SecurityContext）。
   - 新增 `SecurityConfig`（先定義 `/api/orders/**` 放行、`/api/admin/**` 需 `ROLE_ADMIN`）。
4. 建立 `order-service` Dockerfile
   - 採兩階段建置（build/runtime），與既有服務一致，便於後續納入 compose/k8s。

### 註解規範對齊
- 本步所有新增檔案皆補上中文註解：
  - 檔案最上方用途說明
  - 各段程式/設定區塊的用途說明
  - Dockerfile 各階段與關鍵指令目的說明

### 分段原因說明
- 先只完成「可獨立建置執行的 order-service 空骨架」，不混入 DTO/Entity/API 搬移，避免一次改動面過大難以回歸。
- 後續 Step 7B 再開始搬移 Order 領域核心（`Order`、`OrderRepository`、`OrderService`）與必要 DTO/例外，保持步驟可驗證、可回滾。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -DskipTests -f ..\order-service\pom.xml package`（於 `backend` 目錄執行）
- 結果：成功。

### 小總結
- `order-service` 已具備可獨立 build 的基礎骨架與 JWT 安全底座，Phase 4 可進入下一步的 Order 領域搬移。

## 2026-04-01 - Step 7B：搬移 Order 領域核心（過渡版）

### 對應清單項目
- `README.md` §12 Phase 4 細項 2：搬移 Order 領域核心：`Order`、`OrderRepository`、`OrderService` 與必要 DTO/例外處理。

### 本次修改檔案
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/enums/InstallationOption.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/enums/OrderStatus.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/entity/Tire.java`（新增，過渡用）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/entity/Order.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/repository/OrderRepository.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/repository/TireRepository.java`（新增，過渡用）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/service/OrderService.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 搬移訂單領域型別與狀態列舉
   - 新增 `InstallationOption`、`OrderStatus` 到 `order-service`，避免持續依賴 `backend` enum。
2. 搬移訂單實體與資料存取
   - 新增 `Order`（對應 `tire_orders`）。
   - 新增 `OrderRepository`（保留「新到舊」與「依狀態」查詢方法）。
3. 搬移訂單業務服務
   - 新增 `OrderService`（建單、查單、改狀態、輸入驗證與資料清洗）。
   - 保留 `CreateOrderCommand` 作為服務層輸入模型，先不綁 API DTO。
4. 新增過渡期輪胎相依
   - 新增 `Tire` entity 與 `TireRepository`，僅為承接目前 `Order -> Tire` 關聯。
   - 檔案註解已標明此為過渡設計，後續 Step 7D（Snapshot）會移除直接關聯。

### 註解規範對齊
- 本步所有新增檔案皆補上中文註解：
  - 檔案最上方用途說明
  - 每段欄位/方法/規則開頭用途說明

### 分段原因說明
- 本步只做「領域層搬移」，不混入 Controller/API 路由與 Gateway 分流，讓每次變動維持單一主題。
- 先用過渡版保持與既有資料表相容，避免在同一步驟同時改「服務拆分 + snapshot 資料模型」造成高風險回歸。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -DskipTests -f ..\order-service\pom.xml package`（於 `backend` 目錄執行）
- 結果：成功。

### 小總結
- `order-service` 已具備可獨立編譯的 Order 領域核心（entity/repository/service），下一步可進入訂單 API 搬移。

## 2026-04-01 - Step 7C：搬移訂單 API（`/api/orders` + `/api/admin/orders`）

### 對應清單項目
- `README.md` §12 Phase 4 細項 3：搬移訂單 API：`/api/orders`、`/api/admin/orders`、`/api/admin/orders/{id}/status`。

### 本次修改檔案
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/CreateOrderRequest.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/CreateOrderResponse.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/AdminOrderResponse.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/AdminOrderListResponse.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/UpdateOrderStatusRequest.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/ErrorResponse.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/controller/OrderController.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/controller/AdminOrderController.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/controller/GlobalExceptionHandler.java`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 搬移前台建單 API
   - 新增 `OrderController`：`POST /api/orders`。
   - 新增 `CreateOrderRequest`、`CreateOrderResponse`，維持既有回應語意（201 + 訂單提示訊息）。
2. 搬移後台訂單管理 API
   - 新增 `AdminOrderController`：
     - `GET /api/admin/orders`
     - `PATCH /api/admin/orders/{id}/status`
   - 新增 `AdminOrderResponse`、`AdminOrderListResponse`、`UpdateOrderStatusRequest`。
3. 搬移全域例外處理
   - 新增 `GlobalExceptionHandler`，對齊既有錯誤回應格式與狀態碼（400/409/500）。
   - 新增 `ErrorResponse` 作為統一錯誤 DTO。

### 註解規範對齊
- 本步所有新增檔案皆補上中文註解：
  - 檔案最上方用途說明
  - 主要方法與欄位段落的用途說明

### 分段原因說明
- 本步僅完成 API 搬移，不動 Gateway 分流與 backend 下線，避免一次跨越「功能搬移 + 路由切換」兩種風險。
- 本步也尚未進入 Snapshot 改造；維持過渡版 `Order -> Tire` 關聯，確保行為可對照既有流程。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -DskipTests -f ..\order-service\pom.xml package`（於 `backend` 目錄執行）
- 結果：成功。

### 小總結
- `order-service` 已具備訂單 API 對外能力（前台建單 + 後台查改單）。
- 下一步 Step 7D 才會進入 Snapshot 模型改造（屆時會明確標註已進入 Snapshot 階段）。

## 2026-04-01 - Step 7D：Snapshot 模型改造（正式進入 Snapshot）

### 對應清單項目
- `README.md` §12 Phase 4 細項 4：實作 Snapshot 模型：訂單改存 `tireId + tireSnapshot`，移除對 Tire Entity 直接關聯。

### 本次修改檔案
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/entity/Order.java`（更新）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/service/OrderService.java`（更新）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/controller/AdminOrderController.java`（更新）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/dto/AdminOrderResponse.java`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `Order` 實體改為 Snapshot 模型
   - 移除 `@ManyToOne Tire tire` 直接關聯。
   - 改為保存：
     - `tireId`（對應 `tire_id`）
     - `tireSnapshotBrand`（`tire_snapshot_brand`）
     - `tireSnapshotSeries`（`tire_snapshot_series`）
     - `tireSnapshotOrigin`（`tire_snapshot_origin`）
     - `tireSnapshotSize`（`tire_snapshot_size`）
     - `tireSnapshotPrice`（`tire_snapshot_price`）
2. 建單流程改為寫入 snapshot
   - `OrderService#createOrder` 在取得輪胎資料後，不再設定 `order.tire(...)`。
   - 改為把輪胎資訊拷貝進訂單 snapshot 欄位後再儲存。
   - 新增價格合法性檢查（價格不可為 null 或負值），避免寫入無效 snapshot。
3. 後台回應改讀 snapshot
   - `AdminOrderController#toResponse` 改為使用 `order` 的 snapshot 欄位映射 DTO。
   - `AdminOrderResponse` 註解同步改為「來源是訂單 snapshot」。

### 註解規範對齊
- 本步修改過的程式段落均補上中文說明，標示：
  - Snapshot 欄位目的
  - 建單寫入 snapshot 的原因
  - 回應層改讀 snapshot 的意圖

### 分段原因說明
- 本步先完成「資料模型 + 寫入 + 回應」三個核心點，確保訂單已不依賴 `Order -> Tire` 直接關聯。
- 呼叫 `tire-service` 驗證商品可下單（Phase 4 細項 5）仍在下一步，避免一次混入跨服務呼叫與部署變更造成驗證範圍過大。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -DskipTests -f ..\order-service\pom.xml package`（於 `backend` 目錄執行）
- 結果：成功。

### 小總結
- Snapshot 模型已在 `order-service` 落地：新訂單會保存下單當下輪胎資訊，後台查單改讀訂單 snapshot，不再依賴 JPA 直接關聯輪胎主檔。

## 2026-04-01 - Step 7E：建單改為呼叫 tire-service 驗證商品可下單

### 對應清單項目
- `README.md` §12 Phase 4 細項 5：建單流程改造：建單時呼叫 `tire-service` 驗證商品可下單，並寫入 snapshot。

### 本次修改檔案
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/client/TireServiceClient.java`（新增）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/service/OrderService.java`（更新）
- `order-service/src/main/resources/application.yaml`（更新）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/entity/Tire.java`（刪除）
- `order-service/src/main/java/com/fy20047/tireordering/orderservice/repository/TireRepository.java`（刪除）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增跨服務客戶端 `TireServiceClient`
   - 透過 HTTP `GET /api/tires/{id}` 呼叫 `tire-service`。
   - 解析成功回應為 `TireProduct`（id/brand/series/origin/size/price/isActive）。
   - 對 4xx / 5xx / 網路異常做一致化例外轉換，供 `OrderService` 決策。
2. `OrderService` 改用跨服務驗證
   - 移除 `TireRepository` 注入，改注入 `TireServiceClient`。
   - 建單流程改為先查 `tire-service`，再做可下單檢查（上架、價格合法）。
   - 驗證通過後將回傳資料寫入訂單 snapshot 欄位。
3. 移除本地 Tire 資料依賴
   - 刪除 `order-service` 內的 `Tire` entity 與 `TireRepository`。
   - 讓 `order-service` 不再直接讀取輪胎資料表。
4. 新增整合設定
   - `application.yaml` 新增 `integration.tire-service.base-url`（預設可由 `TIRE_BASE_URL` 覆蓋）。

### 註解規範對齊
- 新增與修改程式段落均補上中文註解，包含：
  - 檔案用途
  - 跨服務呼叫段落用途
  - 錯誤處理與 URL 正規化用途

### 分段原因說明
- Step 7D 已完成 snapshot 欄位模型，Step 7E 專注完成「資料來源」切換到 `tire-service`，避免同一步驟再混入 Gateway/部署層路由切換。
- 先讓 `order-service` 在程式層完成去耦，再於後續步驟做流量分流與 backend 入口收斂。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -DskipTests -f ..\order-service\pom.xml package`（於 `backend` 目錄執行）
- 結果：成功。

### 小總結
- 建單流程已改為透過 `tire-service` 驗證可下單並寫入 snapshot，`order-service` 不再直接依賴輪胎資料表。

## 2026-04-01 - Step 7F：Gateway 分流訂單路徑到 order-service

### 對應清單項目
- `README.md` §12 Phase 4 細項 6：調整 Gateway 分流：`/api/orders/**` 與 `/api/admin/orders/**` 導向 `order-service`。

### 本次修改檔案
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java`（更新）
- `api-gateway/src/main/resources/application.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 Gateway 目標服務設定
   - `application.yaml` 新增 `gateway.order-base-url`（環境變數 `ORDER_BASE_URL`，預設 `http://order-service:8080`）。
2. 調整 `ApiProxyController` 分流規則
   - 新增 `orderBaseUrl` 注入與欄位。
   - 新增 `isOrderPath(...)` 路徑判斷：
     - `/api/orders`
     - `/api/orders/**`
     - `/api/admin/orders`
     - `/api/admin/orders/**`
   - `resolveTargetBaseUrl(...)` 新增 order 分流判斷，讓訂單 API 導向 `order-service`。
3. 註解同步更新
   - Controller 檔頭與路由判斷註解補上 Order 分流用途說明。

### 分段原因說明
- 本步只處理 Gateway 程式分流，不混入 compose/k8s/backend 邊界調整，保持改動面可控。
- 先讓 Gateway 具備正確路由能力，下一步再收斂 backend 重複 Order 入口與部署設定。

### 驗證結果
- 已執行編譯打包檢查：
  - `.\mvnw.cmd -q -DskipTests -f ..\api-gateway\pom.xml package`（於 `backend` 目錄執行）
- 結果：成功。

### 小總結
- Gateway 已具備將訂單路徑分流到 `order-service` 的能力；下一步可進行 backend Order 入口收斂與部署同步。

## 2026-04-01 - Step 7H-1：部署接入（Compose）加入 order-service

### 對應清單項目
- `README.md` §12 Phase 4 細項 8（部署更新）的一部分：先同步 `docker-compose`，讓 Gateway 的訂單分流有實際目標服務。

### 本次修改檔案
- `infra/docker-compose.yml`（更新）
- `infra/docker-compose.prod.yml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `docker-compose.yml`（本機）
   - 新增 `order-service` 服務（build `../order-service`）。
   - 注入 `DB_*`、`JWT_*`、`TIRE_BASE_URL=http://tire-service:8080`。
   - `depends_on` 新增對 `mariadb` 與 `tire-service` 依賴。
   - `api-gateway` 新增 `ORDER_BASE_URL=http://order-service:8080`。
   - `api-gateway.depends_on` 新增 `order-service`。
2. `docker-compose.prod.yml`（prod）
   - 新增 `order-service` 服務（image `ghcr.io/fy20047/tire-ordering-system/order-service:latest`）。
   - 注入與本機一致的核心環境參數（含 `TIRE_BASE_URL`）。
   - `api-gateway` 新增 `ORDER_BASE_URL=http://order-service:8080`。
   - `api-gateway.depends_on` 新增 `order-service`。

### 分段原因說明
- 這一小步只做 Compose 部署接入，不混入 backend 邊界收斂與 k8s 變更，避免一次改動面過大。
- 先完成本機/prod compose 拓樸對齊，後續再接續 backend Order 入口收斂（Step 7G）。

### 驗證結果
- 已執行語法解析：
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env.example config`
  - `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.example config`
- 結果：兩份 compose 均可成功解析。

### 小總結
- Compose 部署已具備 `order-service`，Gateway 訂單路徑分流 now 有對應上游可轉發。

## 2026-04-01 - Step 7G：收斂 backend 重複 Order API 入口

### 對應清單項目
- `README.md` §12 Phase 4 細項 7：調整 backend 邊界：停用重複 Order API 入口，保留最小相依作為過渡。

### 本次修改檔案
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/OrderController.java`（更新）
- `backend/src/main/java/com/fy20047/tireordering/backend/controller/AdminOrderController.java`（更新）
- `backend/src/main/resources/application.yaml`（更新）
- `infra/docker-compose.yml`（更新）
- `infra/docker-compose.prod.yml`（更新）
- `infra/.env.example`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 停用 backend 前台 Order 入口（預設）
   - `OrderController` 新增 `@ConditionalOnProperty`
   - 條件鍵值：`feature.backend-order-endpoints-enabled=true` 才啟用
2. 停用 backend 後台 Order 入口（預設）
   - `AdminOrderController` 新增同樣條件註解
   - 讓 `/api/admin/orders/**` 預設不再由 backend 對外提供
3. 新增可回切設定
   - `application.yaml` 新增：
     - `feature.backend-order-endpoints-enabled: ${BACKEND_ORDER_ENDPOINTS_ENABLED:false}`
   - 預設 `false`（符合 Phase 4 邊界收斂目標）
4. 同步 Compose 與範例環境變數
   - `infra/docker-compose.yml` / `infra/docker-compose.prod.yml` 的 backend 環境新增：
     - `BACKEND_ORDER_ENDPOINTS_ENABLED: ${BACKEND_ORDER_ENDPOINTS_ENABLED:-false}`
   - `infra/.env.example` 新增：
     - `BACKEND_ORDER_ENDPOINTS_ENABLED=false`

### 分段原因說明
- 先用 feature flag 收斂重複入口，而不是直接刪除 controller，可保留緊急回切彈性。
- 這一步只處理 backend 邊界與旗標接線，不混入 k8s 與文件更新，避免驗證範圍過大。

### 驗證結果
- 已執行 backend 測試：
  - `.\mvnw.cmd -q test`（於 `backend` 目錄執行）
- 已執行 compose 語法解析：
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env.example config`
  - `docker compose -f infra/docker-compose.prod.yml --env-file infra/.env.example config`
- 結果：全部成功。

### 小總結
- backend 的重複 Order API 入口已可預設關閉，Order 對外流量可收斂到 `order-service`；下一步可進行 k8s 與文件/驗證收尾。

## 2026-04-01 - Step 7H-2：K8s 接入 order-service 與 gateway 訂單分流

### 對應清單項目
- `README.md` §12 Phase 4 細項 8（部署更新）的一部分：同步 `k8s` 讓 order-service 可在叢集運行並承接 Gateway 訂單路徑。

### 本次修改檔案
- `k8s/base/order-deployment.yaml`（新增）
- `k8s/base/order-service.yaml`（新增）
- `k8s/base/kustomization.yaml`（更新）
- `k8s/base/gateway-deployment.yaml`（更新）
- `k8s/overlays/minikube/app-config.yaml`（更新）
- `k8s/overlays/minikube/backend-configmap-env.yaml`（更新）
- `k8s/overlays/minikube/order-configmap-env.yaml`（新增）
- `k8s/overlays/minikube/kustomization.yaml`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 order-service 基礎資源（base）
   - 新增 `order-service` Deployment（port 8080、readiness/liveness probe）。
   - 新增 `order-service` ClusterIP Service（port 8080）。
   - `base/kustomization.yaml` 納入上述兩個新資源。
2. 更新 Gateway base 分流參數
   - `gateway-deployment.yaml` 新增 `ORDER_BASE_URL=http://order-service:8080`。
   - 同步註解說明 Gateway 現在分流 Auth/Tire/Order。
3. 更新 minikube overlay 環境接線
   - `app-config.yaml` 新增：
     - `TIRE_BASE_URL=http://tire-service:8080`
     - `BACKEND_ORDER_ENDPOINTS_ENABLED=false`
   - `backend-configmap-env.yaml` 新增 `BACKEND_ORDER_ENDPOINTS_ENABLED` 注入。
   - 新增 `order-configmap-env.yaml`：
     - 注入 `DB_HOST`、`DB_PORT`、`DB_NAME`、`JWT_EXPIRATION_SECONDS`、`TIRE_BASE_URL`。
   - `overlays/minikube/kustomization.yaml` 新增：
     - `order-service` image tag 覆蓋
     - `order-configmap-env.yaml` patch

### 註解規範對齊
- 本步新增的 K8s 檔案（`order-deployment.yaml`、`order-service.yaml`、`order-configmap-env.yaml`）皆補上中文註解：
  - 檔案用途
  - 主要區塊用途（env/probe/selector）

### 分段原因說明
- 本步只做 K8s 部署接線，不混入 README/SETUP 與 smoke 腳本，保持風險邊界單純。
- 先讓叢集拓樸對齊 Step 7F/7G 的程式分流，再進行文件與驗證收尾。

### 驗證結果
- 已執行：
  - `kubectl kustomize k8s/base`
  - `kubectl kustomize k8s/overlays/minikube`
- 結果：均可成功輸出 manifest，且確認包含：
  - `Service/order-service`
  - `Deployment/order-service`
  - `Deployment/api-gateway` 內 `ORDER_BASE_URL`
  - `backend` 的 `BACKEND_ORDER_ENDPOINTS_ENABLED` 注入

### 小總結
- K8s 已完成 `order-service` 接入與 Gateway 訂單分流接線，叢集部署拓樸與目前 Phase 4 程式邊界一致。

## 2026-04-01 - Step 7H-3：文件收尾（README / SETUP_GUIDE）

### 對應清單項目
- `README.md` §12 Phase 4 細項 8（部署與文件更新）的一部分：補齊 README 與 SETUP 的 Phase 4 現況說明。

### 本次修改檔案
- `README.md`（更新）
- `SETUP_GUIDE.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `README.md` 補齊 Phase 4 目前狀態
   - 新增「Phase 4 技術總結（進行中，已完成 1~8）」。
   - 新增「Phase 4 目前拓樸（實際狀態）」ASCII 架構圖，明確標示 Gateway 分流與 `order-service -> tire-service` 呼叫關係。
   - 更新 `15.5 Kubernetes` 檔案索引，納入 `order-service` base 資源與 overlay env patch。
2. `SETUP_GUIDE.md` 同步 Phase 4 操作資訊
   - 將服務拓樸由 Phase 3 更新為 Phase 4（含 Order 路徑與 snapshot 流程）。
   - 重要環境變數新增 `ORDER_BASE_URL`、`TIRE_BASE_URL`、`BACKEND_ORDER_ENDPOINTS_ENABLED`。
   - Smoke 區塊改為 Phase 4 目前驗證重點，並補充完整 snapshot 驗證目標。
   - Kubernetes 部署說明補上 `order-service`。
   - rollout restart 清單補上 `order-service`。
   - app-secret 建立說明補充：`BACKEND_*_ENDPOINTS_ENABLED` 改由 `app-config` 管理，不需放 app-secret。

### 分段原因說明
- 在程式與 K8s 接線完成後，先同步文件可避免團隊操作與實際拓樸不一致。
- 此步僅做文件修正，不混入 smoke 腳本改版，保持變更目的單純。

### 驗證結果
- 本步為文件更新，未涉及程式編譯或部署參數變更驗證。

### 小總結
- README 與 SETUP 已對齊目前 Phase 4 真實狀態，可直接作為後續 smoke 驗證與部署操作依據。

## 2026-04-01 - Step 7I：Phase 4 Smoke 腳本升級（含 Snapshot 驗證）

### 對應清單項目
- `README.md` §12 Phase 4 細項 9：執行/補齊 Phase 4 smoke，覆蓋建單成功/失敗與 snapshot 在輪胎改版後的穩定性。

### 本次修改檔案
- `scripts/smoke/run-smoke-gateway.ps1`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. Smoke 腳本目標調整為 Phase 4 驗收
   - 檔頭用途說明從 Phase 3 路徑驗證，升級為 Phase 4 驗收重點（Order Service + Snapshot）。
2. 新增 snapshot 驗證輔助函式
   - `Get-OrderFromAdminList`：從後台列表精準抓指定 `orderId`，避免誤比對。
   - `Assert-OrderSnapshot`：比對 `tireId/brand/series/origin/size/price` 六個快照欄位。
3. 重排 smoke 流程（步驟 1~20）
   - 保留 auth/security 基礎檢查（login、refresh、logout、401/403）。
   - 用腳本自建輪胎作為快照基準資料，避免依賴既有資料順序。
   - 建立訂單 #1（輪胎版本 A）後，檢查快照為版本 A。
   - 更新同輪胎為版本 B，再建訂單 #2，驗證：
     - 訂單 #1 快照仍為版本 A（舊單不變）。
     - 訂單 #2 快照為版本 B（新單吃新資料）。
   - 補齊失敗案例：
     - 不存在輪胎建單 -> `400`
     - 停用輪胎建單 -> `409`

### 分段原因說明
- 這一步只處理 smoke 腳本，未混入 service/k8s 文件改動，讓驗收腳本可獨立回滾與審閱。

### 驗證結果
- PowerShell 語法解析：
  - `[System.Management.Automation.Language.Parser]::ParseFile(...)` -> `PARSE_OK`
- 本機實跑：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\smoke\run-smoke-gateway.ps1 -BaseUrl "http://localhost:8080" -AdminUsername "admin" -AdminPassword "admin123"`
  - 結果：`Login` 步驟連線失敗（`Unable to connect to the remote server`），原因是本機 `localhost:8080` 服務未啟動。

### 小總結
- Phase 4 smoke 腳本已補齊 snapshot 穩定性與建單失敗案例；待本機服務啟動後可直接做端到端驗收。

## 2026-04-02 - Step 7J：實際執行 Phase 4 Smoke 並補文件指令

### 對應清單項目
- `README.md` §12 Phase 4 細項 9：smoke 實際執行與驗證結果落地。

### 本次修改檔案
- `SETUP_GUIDE.md`（更新）
- `README.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `SETUP_GUIDE.md` 補上可直接重跑的 smoke 操作指令
   - 在 Smoke 區塊新增：
     - 先用 `infra/.env` 啟動 compose
     - 先做 `/api/health` 檢查
     - 再執行 smoke 腳本
   - 新增「2026-04-02 實際驗證指令」範例。
2. `README.md` 更新 Phase 4 狀態
   - `Phase 4 技術總結` 從「進行中，已完成 1~8」改為「已完成 1~9」。
   - 將原本「第 9 項 smoke 待完成」改為「已完成實跑」。

### 驗證結果（實際執行）
- 先啟動 Docker Compose：
  - `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build`
- 再執行 smoke：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\smoke\run-smoke-gateway.ps1 -BaseUrl "http://localhost:8080" -AdminUsername "admin" -AdminPassword "<infra/.env 的 ADMIN_PASSWORD>"`
- 結果：
  - smoke 全部通過（含 snapshot A/B 驗證、400/409、401/403）。

### 小總結
- 文件已補齊可直接執行的 smoke 指令，且 Phase 4 狀態已更新為「1~9 全完成」。

## 2026-04-02 - Step 8A：補齊 Phase 5 目標與待完成項目文件

### 對應清單項目
- `README.md` §12：在 Phase 4 完成後，明確列出 Phase 5 的收斂目標、完成判準與待辦清單。

### 本次修改檔案
- `README.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. `README.md` 新增 Phase 5 執行區塊
   - 新增「Phase 5 細項（收斂與下線 Monolith）」共 8 項可執行工作。
   - 新增「Phase 5 完成判準」共 5 項驗收標準。
   - 新增「Phase 5 待完成項目（截至 2026-04-02）」Checklist，對齊目前狀態。
2. 待辦內容聚焦收斂路徑
   - 涵蓋 Gateway fallback 移除、backend 下線、資料邊界拆分、CI/CD 收斂、smoke 與 runbook 收尾。

### 分段原因說明
- 先補文件定義「下一階段要做什麼」，再按清單逐步落地，可避免 Phase 5 期間改動方向發散。
- 本步僅做文件，不混入程式與部署參數變更，便於審閱與後續拆步執行。

### 驗證結果
- 本步為文件更新，未涉及程式編譯或部署執行驗證。

### 小總結
- Phase 5 已從高層描述落地為可執行清單與驗收標準，後續可直接按 checklist 逐項推進。

## 2026-04-02 - Step 8B-1：盤點 Gateway 仍指向 backend 的路由與環境變數

### 對應清單項目
- `README.md` §12「Phase 5 待完成項目」第 1 項：
  - 盤點所有仍指向 `backend` 的 Gateway 路由與環境變數，確認替代路徑。

### 本次修改檔案
- `docs/phase5-gateway-backend-inventory.md`（新增）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 Phase 5 盤點文件
   - 建立 `docs/phase5-gateway-backend-inventory.md`，集中記錄：
     - 已完成微服務替代的 API 路徑（Auth/Tire/Order）
     - 仍透過 fallback 指向 `backend` 的路由
     - `BACKEND_BASE_URL` 在 compose/k8s/gateway 設定中的落點
2. 確認主要剩餘依賴點
   - `ApiProxyController` 對未命中路徑仍回落到 `backend`。
   - `/api/health` 目前仍依賴 fallback（文件與說明頁仍引用）。
3. 補上下一小步建議
   - 先補 Gateway 的 `/api/health` 專屬端點，再移除 fallback 與 `BACKEND_BASE_URL`。

### 分段原因說明
- 此步驟先做「可驗證的現況盤點」，不直接改路由邏輯，避免一次混入盤點與行為改動。
- 先把替代路徑與剩餘依賴關係寫清楚，下一步移除 fallback 時可精準縮小影響範圍。

### 驗證結果
- 本步為文件盤點更新，未涉及程式編譯與部署執行。
- 盤點依據已附上對應檔案與行號，便於逐項追蹤。

### 小總結
- 已完成 Phase 5 第 1 項的「盤點與替代路徑確認」文件化，下一步可開始最小行為改動：先讓 `/api/health` 脫離 backend fallback。

## 2026-04-02 - Step 8B-2：Gateway 新增 `/api/health` 專屬端點（先解除 health 對 backend 依賴）

### 對應清單項目
- `README.md` §12「Phase 5 待完成項目」第 1 項延伸落地：
  - 先把 `api/health` 從 fallback 依賴中拆出，降低後續移除 fallback 的風險。

### 本次修改檔案
- `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/GatewayHealthController.java`（新增）
- `docs/phase5-gateway-backend-inventory.md`（更新）
- `README.md`（更新）
- `MODIFICATION_HISTORY.md`（更新）

### 變更內容
1. 新增 Gateway 健康檢查相容端點
   - 新增 `GatewayHealthController`，提供 `GET /api/health`。
   - 該端點直接讀取 `HealthEndpoint`（Actuator）健康狀態，回傳：
     - `timestamp`
     - `status`
     - `service=api-gateway`
     - `details`（若 Actuator 有提供）
   - 回應碼規則：
     - `UP` -> `200`
     - 其他狀態 -> `503`
2. 盤點文件同步更新現況
   - `docs/phase5-gateway-backend-inventory.md` 增補 Step 8B-2 狀態：
     - `/api/health` 已不再依賴 backend fallback。
     - 目前剩餘依賴點聚焦於「未命中規則的 fallback API」。
3. Phase 5 清單狀態同步
   - `README.md` 將「盤點 Gateway 指向 backend 路由與環境變數」勾選為已完成，並附上盤點文件路徑。

### 註解規範對齊
- 本步新增程式檔 `GatewayHealthController.java` 已補齊中文註解：
  - 檔案用途（class 註解）
  - 各段邏輯用途（欄位、端點、資料組裝、HTTP 狀態決策）

### 分段原因說明
- 先拆 `/api/health`，可避免下一步移除 fallback 時直接影響既有健康檢查與 smoke 前置檢查。
- 本步不改 `ApiProxyController` fallback 主邏輯，將風險控制在單一路徑。

### 驗證結果
- 已執行：
  - `.\backend\mvnw.cmd -q -DskipTests -f .\api-gateway\pom.xml package`
- 結果：成功。

### 小總結
- `/api/health` 已完成去 backend 依賴；下一步可安全進入 fallback 與 `BACKEND_BASE_URL` 的移除。
