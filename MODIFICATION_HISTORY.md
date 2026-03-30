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
