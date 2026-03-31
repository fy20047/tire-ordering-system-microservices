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
