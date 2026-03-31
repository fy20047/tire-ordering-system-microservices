# Tire Ordering System  
## Monolith -> Microservices 詳細遷移規劃

> 將原始專案轉為 microservices 架構。  
> 釐清概念、資料設計、部署與 CI/CD 內容。
> 與 Codex 合作進行 Vibe Coding。

---

## 0. 先講結論（你目前的架構決策）

你提出的拆分方向是正確的：

1. Auth Service  
2. Tire Service  
3. Order Service  
4. API Gateway  

對外流量路徑：

`External Request -> Ingress -> API Gateway -> 各微服務`

資料庫策略已定案：

- 先維持 **MariaDB**（不切 MongoDB）
- 但要改成「每個服務擁有自己的資料庫邊界」（先 schema 隔離）

---

## 1. README 內容說明

如果你是初學者，建議閱讀順序：

1. 第 2 章 微服務基礎概念
2. 第 3 章 目前專案檔案導覽
3. 第 4~6 章（目標架構 / snapshot / DB 策略）
4. 第 7~10 章（遷移步驟 / Docker-K8s-CICD / 風險 / 下一步）

---

## 2. Microservices 基礎概念

### 2.1 Monolith 是什麼

Monolith（單體）是把所有業務放在一個應用程式內。  
你當前舊專案的 `backend` 就是這種：auth + tire + order 全在同一個 Spring Boot 專案。

優點：

- 開發與除錯簡單
- 本機啟動容易
- 交易一致性好處理

缺點：

- 任一功能改動都要整包部署
- 服務無法針對業務熱點獨立擴縮
- 邊界不清楚時，程式容易越寫越耦合

### 2.2 Microservice 是什麼

Microservice 是把系統拆成多個小服務，每個服務有：

- 清楚的業務邊界（bounded context）
- 獨立部署單位
- 自己的資料儲存邊界
- 透過 API（或事件）與其他服務溝通

### 2.3 API Gateway 是什麼

Gateway 是所有 API 的統一入口，負責：

- 路由轉發
- token 驗證（第一道）
- rate limit / logging / policy
- 對外隱藏內部服務拓樸

### 2.4 Ingress 與 Gateway 差異

- Ingress：Kubernetes 的 L7 入口規則（把外部流量送進 cluster）
- Gateway：應用層 API 管理元件（做驗證、路由、策略）

簡單記法：  
**Ingress 管「進到哪個服務」；Gateway 管「API 應該怎麼處理」。**

### 2.5 JWT、Access Token、Refresh Token

- JWT：一種 token 格式，裡面有 claims（像 sub/role/exp）
- Access Token：短效，帶在 Authorization header，用來打 API
- Refresh Token：長效，通常放 HttpOnly Cookie，專門拿來換新的 access token

### 2.6 HS256 vs RS256（按照你說的改成 RS256）

- HS256：對稱金鑰，簽章與驗章都用同一把 secret
- RS256：非對稱金鑰，Auth 用 private key 簽；其他服務用 public key 驗

為什麼 RS256 比較適合微服務：

- private key 不需散落到每個服務
- key rotation 更容易管理
- 權限邊界更清楚

### 2.7 Snapshot 是什麼（非常重要）

Snapshot 是「把某個時間點的關鍵資料複製保存」。

在 Order Service，snapshot 表示：

- 下單當下把輪胎資料複製進訂單
- 之後即使 Tire Service 改名、改價、下架，訂單內容仍保持歷史正確

---

## 3. 目前專案檔案導覽（你說要「檔案解釋都加入」）

這章重點是讓你知道每個關鍵檔案現在在做什麼，未來拆分要怎麼移動。

### 3.1 專案根目錄

| 路徑 | 用途 |
|---|---|
| `backend/` | 目前單體後端（Spring Boot） |
| `frontend/` | React 前端 |
| `infra/` | Docker Compose 設定 |
| `k8s/` | Kubernetes manifests（Kustomize） |
| `argocd/` | ArgoCD Application 設定 |
| `.github/workflows/` | GitHub Actions CI/CD |
| `SETUP_GUIDE.md` | 本機與部署操作指引 |

### 3.2 Backend（目前是單體核心）

#### 3.2.1 啟動與設定

| 檔案 | 作用 | 未來去向 |
|---|---|---|
| `backend/src/main/java/.../BackendApplication.java` | Spring Boot 入口 | 每個新服務都會有自己的入口 |
| `config/SecurityConfig.java` | API 權限與 JWT filter 規則 | 拆到 Auth / 各服務自己的 security |
| `config/JwtProperties.java` | JWT 與 refresh cookie 設定 | Auth Service 為主 |
| `config/CorsConfig.java` | CORS 白名單設定 | Gateway 層與各服務協調 |
| `config/AdminSeedRunner.java` | 啟動時建立初始 admin | 留在 Auth Service |

#### 3.2.2 Controller（API 入口）

| 檔案 | 作用 | 未來去向 |
|---|---|---|
| `controller/AdminAuthController.java` | login/refresh/logout | Auth Service |
| `controller/TireController.java` | 公開輪胎查詢 | Tire Service |
| `controller/AdminTireController.java` | 後台輪胎 CRUD / 上下架 | Tire Service |
| `controller/OrderController.java` | 建立訂單 | Order Service |
| `controller/AdminOrderController.java` | 後台訂單查詢 / 狀態更新 | Order Service |
| `controller/HealthController.java` | health check | 各服務都要有 |
| `controller/GlobalExceptionHandler.java` | 全域錯誤格式 | 各服務各自保留 |

#### 3.2.3 Service / Security / Repository / Entity

| 檔案群組 | 作用 | 未來去向 |
|---|---|---|
| `service/AdminService.java` | 管理員登入流程 | Auth Service |
| `security/JwtService.java` | 產生/驗證 JWT | Auth（簽章）+ 其他服務（驗章） |
| `security/JwtAuthenticationFilter.java` | 解析 Authorization Bearer token | Gateway + 各服務輕量驗證 |
| `security/RefreshTokenService.java` | refresh token 建立/撤銷/輪替 | Auth Service |
| `service/TireService.java` + `repository/TireRepository.java` + `entity/Tire.java` | 輪胎業務與儲存 | Tire Service |
| `service/OrderService.java` + `repository/OrderRepository.java` + `entity/Order.java` | 訂單業務與儲存 | Order Service |
| `entity/Admin.java`, `entity/RefreshToken.java`, 對應 repository | 帳號與 refresh token | Auth Service |
| `dto/*` | API 輸入輸出模型 | 拆到對應服務 |

#### 3.2.4 Backend 設定與測試

| 檔案 | 作用 |
|---|---|
| `backend/src/main/resources/application.yaml` | DB 與 JWT 環境變數來源 |
| `backend/src/test/java/...` | 單元測試（Admin/Order/Tire/Jwt） |

### 3.3 Frontend

| 檔案 | 作用 | 是否需改動 |
|---|---|---|
| `frontend/src/api/adminApi.ts` | admin API client + token refresh 重試 | 若保留 API 路徑可少改 |
| `frontend/src/context/AuthContext.tsx` | 登入狀態、啟動時 refresh、logout | Auth 路由改名時要改 |
| `frontend/src/pages/AdminLogin.tsx` | 登入頁 API 呼叫 | Auth 路由改名時要改 |
| `frontend/src/pages/AdminTires.tsx` | 後台輪胎管理 | 主要由 Gateway 對齊 |
| `frontend/src/pages/AdminOrders.tsx` | 後台訂單管理 | 主要由 Gateway 對齊 |
| `frontend/src/pages/Order.tsx` | 建單流程 | 主要由 Gateway 對齊 |
| `frontend/src/pages/Promotions.tsx`, `FindTires.tsx` | 輪胎查詢頁 | 主要由 Gateway 對齊 |
| `frontend/src/App.tsx`, `main.tsx`, `components/*`, `styles/*` | 路由與 UI 樣式 | 通常不需因微服務重寫 |

### 3.4 Infra / K8s / CI / CD

| 檔案 | 作用 | 未來改動重點 |
|---|---|---|
| `infra/docker-compose.yml` | 本機 backend+frontend+mariadb | 改成 gateway+auth+tire+order+frontend |
| `infra/docker-compose.prod.yml` | 生產 compose | 同上 |
| `infra/.env.example` | 環境變數範本 | 拆成多服務 env |
| `k8s/base/*.yaml` | backend/frontend/mariadb 基礎資源 | 新增 gateway/auth/tire/order |
| `k8s/overlays/minikube/*.yaml` | minikube overlay、hpa/pdb/ingress 等 | 每服務都要對應資源 |
| `.github/workflows/ci.yml` | backend/frontend 的 CI 與 image push | 改 matrix，多服務發版 |
| `argocd/app.yaml` | ArgoCD 同步 kustomize | 納入新服務與 image |

---

## 4. 目標微服務架構

```text
Client (Browser)
  -> Ingress (K8s)
  -> API Gateway
     -> Auth Service
     -> Tire Service
     -> Order Service
```

### 4.1 各服務職責（Bounded Context）

| 服務 | 負責 | 不負責 |
|---|---|---|
| Auth Service | 管理員登入、refresh、logout、token 簽發、token policy | 輪胎查詢、訂單流程 |
| Tire Service | 輪胎查詢、後台輪胎 CRUD、上下架 | 身份驗證邏輯、訂單狀態 |
| Order Service | 建立訂單、查詢訂單、狀態更新 | 帳號登入、輪胎商品維護 |
| API Gateway | 對外入口、路由、token 第一層驗證、rate limit、log/policy | 業務資料持久化 |

### 4.2 路由建議（前端相容優先）

| 外部路徑 | 內部路由到 |
|---|---|
| `/api/admin/login`, `/api/admin/refresh`, `/api/admin/logout` | Auth Service |
| `/api/tires/**`, `/api/admin/tires/**` | Tire Service |
| `/api/orders/**`, `/api/admin/orders/**` | Order Service |

這樣做的好處：

- 前端多數程式碼可以先不動
- 先完成後端拆分，再逐步重命名為 `/api/auth/**`

---

## 5. Snapshot 章節

### 5.1 你提到的欄位到底是什麼

| 欄位 | 定義 | 用途 |
|---|---|---|
| `tireId` | 下單時所選輪胎的業務 ID | 追溯來源商品 |
| `tireBrandSnapshot` | 下單時輪胎品牌文字 | 歷史訂單展示/對帳 |
| `tireSeriesSnapshot` | 下單時輪胎系列文字 | 歷史訂單展示/對帳 |
| `unitPriceSnapshot` | 下單時單價 | 金流、發票、報表一致性 |

### 5.2 為什麼不能只存 tireId

如果只存 `tireId`，當 Tire Service 的資料被改動時：

- 品牌或系列改名，舊訂單畫面會被污染
- 價格改變，舊訂單金額會不正確
- 商品下架或刪除，訂單資料可能無法完整呈現

### 5.3 Snapshot 的實務規則

建議規則：

1. 訂單建立時一次寫入 snapshot
2. snapshot 之後不可被業務更新（唯讀）
3. 訂單狀態可變，但商品快照不可變

### 5.4 目前程式為何需要改

現在 `Order` entity 有 `@ManyToOne Tire`，這是單體時代可行。  
拆成微服務後，Order DB 不應該直接 FK 到 Tire DB。

因此要改成：

- 訂單表保留 `tire_id`（純欄位，不做跨服務 FK）
- 新增 snapshot 欄位保存當下資訊

### 5.5 建議的 Order 資料表示意（MariaDB）

```sql
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tire_id BIGINT NOT NULL,
  tire_brand_snapshot VARCHAR(100) NOT NULL,
  tire_series_snapshot VARCHAR(100) NOT NULL,
  unit_price_snapshot INT NOT NULL,
  quantity INT NOT NULL,
  customer_name VARCHAR(100) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  email VARCHAR(255),
  installation_option VARCHAR(20) NOT NULL,
  delivery_address TEXT,
  car_model VARCHAR(100),
  notes TEXT,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);
```

---

## 6. 資料庫策略（維持 MariaDB，不換 MongoDB）

### 6.1 為什麼現在不建議改 MongoDB

目前你的主學習目標是「拆服務邊界與部署流程」。  
此時切 DB 類型會增加額外難度：

- 資料遷移複雜度
- 查詢語法與索引策略重學
- 維運成本增加
- 問題定位維度變多

### 6.2 Microservices 不等於一定要 NoSQL

正確觀念：  
**Microservices 關鍵是服務邊界，不是資料庫品牌。**

MariaDB 在你的情境（auth/order/tire）完全可用。

### 6.3 你現在應該採用的 MariaDB 方案

先採「單一 MariaDB instance + 多 schema + 多帳號」：

- `auth_db`：`admins`, `refresh_tokens`
- `tire_db`：`tires`
- `order_db`：`orders`

每個服務只給自己的 DB 帳號權限，不可跨 schema 查表。

---

## 7. 安全設計（Auth + Gateway + RS256）

### 7.1 建議 Token Flow

1. `POST /api/admin/login`（Auth Service）
2. Auth 驗證帳密，回 access token，並設 refresh cookie
3. Client 帶 access token 打 API
4. Gateway 先驗證 JWT
5. token 過期時，client 呼叫 refresh 換新 token

### 7.2 RS256 的 key 職責

- Auth Service：持有 private key（簽發）
- Gateway / Tire / Order：持有 public key（驗章）
- Auth Service 提供 JWKS endpoint（含 `kid`）

### 7.3 是否只有 Gateway 驗證就好

建議：

- Gateway 做第一層驗證（必要）
- 各服務做第二層最小驗證（建議）

理由：避免內網誤路由或繞過 gateway 時完全失防。

---

## 8. 遷移步驟（詳細版）

### Phase 0：Baseline 與契約凍結

目標：

- 先把目前 API 契約與主要流程測試固定住

必做：

- 整理現有 endpoints 與 request/response 範例
- 建立 smoke tests（登入、查輪胎、建單、後台查單改狀態）

完成標準：

- 你能在不拆服務前先證明功能可用

### Phase 1：先導入 API Gateway（後端先不拆）

目標：

- 入口統一，不改業務邏輯

必做：

- 新增 gateway 專案
- Gateway 先把 `/api/**` 全轉發到舊 backend
- Ingress 改成 `/api -> gateway`

完成標準：

- 前端無感，API 仍可用，但已經改由 gateway 進出

### Phase 2：抽出 Auth Service

目標：

- 把認證責任從 monolith 拆出來

必做：

- 搬移 `AdminAuthController/AdminService/JwtService/RefreshTokenService/...`
- 改 RS256（private/public key）
- 加入 JWKS endpoint
- Gateway 路由登入/refresh/logout 到 Auth Service

完成標準：

- login/refresh/logout 全由 Auth Service 提供

### Phase 3：抽出 Tire Service

目標：

- 輪胎業務獨立

必做：

- 搬移 `TireController/AdminTireController/TireService/TireRepository/Tire`
- Gateway 路由 `/api/tires` 相關路徑到 Tire Service

完成標準：

- 查輪胎、後台輪胎管理不再依賴 monolith

### Phase 4：抽出 Order Service + Snapshot 改造

目標：

- 訂單業務獨立且去除跨服務資料耦合

必做：

- 搬移 `OrderController/AdminOrderController/OrderService/OrderRepository/Order`
- 將 `Order -> Tire` 關聯改為 `tireId + snapshot 欄位`
- 建單時向 Tire Service 驗證商品可下單

完成標準：

- Order Service 可獨立運作，不需直接查 Tire DB

### Phase 5：收斂與下線 Monolith

目標：

- 只保留微服務架構

必做：

- monolith backend 下線
- 補全監控、告警、追蹤
- 完整文件化 runbook

---

## 9. Docker / Kubernetes / CI/CD 詳細改造

### 9.1 Docker Compose

目前：`mariadb + adminer + backend + frontend`  
目標：`mariadb + adminer + gateway + auth + tire + order + frontend`

環境變數要拆分：

- `AUTH_DB_*`
- `TIRE_DB_*`
- `ORDER_DB_*`
- `JWT_PRIVATE_KEY`（Auth 專用）
- `JWT_PUBLIC_KEY`（Gateway/各服務）

### 9.2 Kubernetes（k8s/base 與 overlays）

要新增（至少）：

- `gateway-deployment.yaml`, `gateway-service.yaml`
- `auth-deployment.yaml`, `auth-service.yaml`
- `tire-deployment.yaml`, `tire-service.yaml`
- `order-deployment.yaml`, `order-service.yaml`

Ingress 原則：

- `/` -> frontend
- `/api` -> gateway

Config 與 Secret 原則：

- 每服務自己的 ConfigMap/Secret
- 不再使用一包 `app-secret` 把所有服務機密塞在一起

### 9.3 CI（GitHub Actions）

目前 `ci.yml` 只有 backend/frontend。  
未來改 matrix 方向：

- `api-gateway`
- `auth-service`
- `tire-service`
- `order-service`
- `frontend`

每個服務都要有：

- test
- build
- docker image push

### 9.4 CD（ArgoCD）

- `k8s/overlays/minikube/kustomization.yaml` 需管理多個 images
- `argocd/app.yaml` 需對應新 deployment
- ignoreDifferences 規則需要擴充（若你維持 HPA 自動調整）

---

## 10. 風險與對策（實作時最容易踩雷）

| 風險 | 說明 | 對策 |
|---|---|---|
| 跨服務資料庫 join 習慣 | 微服務後會破壞邊界 | 統一改 API 呼叫或事件同步 |
| 訂單資料不穩定 | 商品資料被改會污染舊單 | 使用 snapshot 欄位 |
| 一次改太多 | 容易大爆炸 | 按 phase 逐步推進 |
| token 驗證散亂 | 安全策略不一致 | Gateway + 服務雙層最小驗證 |
| CI/CD 複雜度上升 | 多服務發版流程變長 | matrix 與標準化模板 |

---

## 11. 回答你的疑問

### Q1：微服務是不是要搭配多種資料庫引擎？

不是。你現在維持 MariaDB 是正確選擇。  
先把邊界拆對，之後才考慮 polyglot persistence。

### Q2：Gateway 驗證 token 後，後端還要驗嗎？

建議要做最小驗證。  
這樣防禦更完整，也比較符合零信任思路。

### Q3：snapshot 會不會重複資料？

會，這是刻意的。  
它換來的是訂單歷史正確性與服務解耦。

### Q4：為什麼不直接讓 Order Service 讀 Tire DB？

這樣會回到單體耦合，微服務邊界會被破壞。

---

## 12. 下一步執行清單（我們就照這份走）

### Phase 1 小總結（已完成）

1. 已建立 API 契約 baseline 文件（以 monolith 現況為準）
2. 已新增 `api-gateway`，本機路徑改為 `frontend -> api-gateway -> backend`
3. 已調整 K8s Ingress，讓 `/api` 先進 Gateway
4. 已完成 smoke integration 驗證（登入、查輪胎、建單、後台核心流程）
5. 已補 Gateway root 說明頁與 `/login` 導引，避免 `localhost:8080` Whitelabel

### Phase 2 細項（抽 Auth + RS256）

1. 建立 `auth-service` 專案骨架（Spring MVC），先搬移登入/刷新/登出與 refresh token 邏輯
2. 導入 RS256：產生金鑰對、設定環境變數、簽章改用 private key、驗章改用 public key
3. 調整 Gateway 路由：`/api/admin/login|refresh|logout` 導向 `auth-service`，其他 API 先維持 `backend`
4. 調整 backend：停用重複 Auth 入口，保留 tire/order 業務 API，並套用 RS256 驗章設定
5. 補 Phase 2 smoke：login/refresh/logout、admin API 帶 token、無效/過期 token、logout 後 refresh 失敗
6. 更新部署與文件：`docker-compose`、`k8s`、`.env.example`、README、回滾步驟與驗證紀錄

### Phase 2 完成判準

1. 所有 Auth API 僅由 `auth-service` 對外提供
2. 系統簽章/驗章全面改為 RS256，不再依賴 HS256 shared secret
3. Phase 2 smoke 全部通過，前端行為與操作路徑維持不變

### Phase 2 技術總結（已完成）

- 服務切分：新增 `auth-service`，承接 `/api/admin/login|refresh|logout`，Gateway 完成路由分流。
- 安全機制：JWT 由 HS256 升級為 RS256，簽章集中於 `auth-service`（private key），驗章由業務服務使用 public key。
- 邊界收斂：`backend` 停用重複 Auth 入口，專注 tire/order 業務 API 與 token 驗章授權。
- 部署同步：`docker-compose`、`k8s`、`.env.example`、`SealedSecret`、`SETUP_GUIDE` 已對齊新拓樸與 key 參數。
- 驗證補強：完成 compile/test 與 gateway smoke（成功路徑 + 401/403 失敗路徑）驗證。
- 風險控制：補齊密鑰外洩熱修（歷史重寫、key 輪替、測試改動態產生），避免私鑰再次入版控。

### Phase 3 細項（抽 Tire Service）

1. 建立 `tire-service` 專案骨架（Spring MVC + JPA + Security 驗章），先確保可獨立 build/run
2. 搬移 Tire 領域核心：`Tire`、`TireRepository`、`TireService`，維持既有資料表與查詢行為
3. 搬移公開輪胎 API：`/api/tires`、`/api/tires/{id}`（含 DTO 與例外處理）
4. 搬移後台輪胎 API：`/api/admin/tires`、`/api/admin/tires/{id}`、`/api/admin/tires/{id}/active`（含 DTO 與驗證）
5. 調整 Gateway 分流：`/api/tires/**` 與 `/api/admin/tires/**` 導向 `tire-service`，Auth 與其他路由維持現況
6. 調整 backend 邊界：停用重複 Tire API 入口，保留 order 與其相依資料模型，避免一次牽動 Phase 4
7. 更新部署與文件：`docker-compose`、`k8s`、`.env.example`、`SETUP_GUIDE`、README 驗證紀錄
8. 補 Phase 3 smoke：公開查詢、後台 CRUD/上下架、未授權路徑（401/403）、Gateway 路由正確性

### Phase 3 完成判準

1. `Tire` 相關 API 僅由 `tire-service` 對外提供（經由 Gateway）
2. 查輪胎與後台輪胎管理流程不再依賴 `backend` 的 Tire Controller
3. Phase 3 smoke 全部通過，前端操作路徑維持不變

---

## 13. 目標目錄結構（最終樣貌）

```text
.
├─ services/
│  ├─ api-gateway/
│  ├─ auth-service/
│  ├─ tire-service/
│  └─ order-service/
├─ frontend/
├─ infra/
│  ├─ docker-compose.yml
│  └─ docker-compose.prod.yml
├─ k8s/
│  ├─ base/
│  └─ overlays/minikube/
├─ argocd/
└─ .github/workflows/
```

---

## 14. 你目前一定要記住的 5 句話

1. 微服務重點是「服務邊界」，不是先換技術。
2. 每個服務要有自己的資料邊界。
3. 訂單要存 snapshot，才能保證歷史正確。
4. 先用 MariaDB 把架構拆對，再談是否換 MongoDB。
5. 用 phase 漸進遷移，比一次大改可靠很多。

---

## 15. 你所提供的完整檔案索引（現況）

> 這裡列出目前專案的重要檔案與用途。  
> 之後實作時可以直接用這份索引對照「該改哪裡」。

### 15.1 Backend `src/main/java`

| 檔案 | 說明 |
|---|---|
| `backend/src/main/java/com/fy20047/tireordering/backend/BackendApplication.java` | Spring Boot 應用程式入口 |
| `backend/src/main/java/com/fy20047/tireordering/backend/config/AdminSeedRunner.java` | 啟動時建立預設管理員 |
| `backend/src/main/java/com/fy20047/tireordering/backend/config/CorsConfig.java` | CORS 設定 |
| `backend/src/main/java/com/fy20047/tireordering/backend/config/JwtProperties.java` | JWT/refresh cookie 組態映射 |
| `backend/src/main/java/com/fy20047/tireordering/backend/config/SecurityConfig.java` | Spring Security 授權規則 |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/AdminAuthController.java` | 後台登入/refresh/logout API |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/AdminOrderController.java` | 後台訂單列表與狀態更新 API |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/AdminTireController.java` | 後台輪胎 CRUD 與上下架 API |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/GlobalExceptionHandler.java` | 全域例外轉換成統一錯誤回應 |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/HealthController.java` | 健康檢查 API |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/OrderController.java` | 前台建立訂單 API |
| `backend/src/main/java/com/fy20047/tireordering/backend/controller/TireController.java` | 前台輪胎查詢 API |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminLoginRequest.java` | 登入請求 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminLoginResponse.java` | 登入回應 DTO（access token） |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminOrderListResponse.java` | 後台訂單列表回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminOrderResponse.java` | 後台單筆訂單 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminTireListResponse.java` | 後台輪胎列表回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminTireRequest.java` | 後台輪胎新增/更新請求 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/AdminTireResponse.java` | 後台輪胎回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/CreateOrderRequest.java` | 建單請求 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/CreateOrderResponse.java` | 建單回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/ErrorResponse.java` | 錯誤回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/TireListResponse.java` | 前台輪胎列表回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/TireResponse.java` | 前台輪胎回應 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/UpdateOrderStatusRequest.java` | 訂單狀態更新請求 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/dto/UpdateTireStatusRequest.java` | 輪胎上下架請求 DTO |
| `backend/src/main/java/com/fy20047/tireordering/backend/entity/Admin.java` | 管理員資料實體 |
| `backend/src/main/java/com/fy20047/tireordering/backend/entity/Order.java` | 訂單資料實體（目前仍關聯 Tire） |
| `backend/src/main/java/com/fy20047/tireordering/backend/entity/RefreshToken.java` | refresh token 實體 |
| `backend/src/main/java/com/fy20047/tireordering/backend/entity/Tire.java` | 輪胎資料實體 |
| `backend/src/main/java/com/fy20047/tireordering/backend/enums/InstallationOption.java` | 安裝方式列舉 |
| `backend/src/main/java/com/fy20047/tireordering/backend/enums/OrderStatus.java` | 訂單狀態列舉 |
| `backend/src/main/java/com/fy20047/tireordering/backend/repository/AdminRepository.java` | 管理員資料存取 |
| `backend/src/main/java/com/fy20047/tireordering/backend/repository/OrderRepository.java` | 訂單資料存取 |
| `backend/src/main/java/com/fy20047/tireordering/backend/repository/RefreshTokenRepository.java` | refresh token 資料存取 |
| `backend/src/main/java/com/fy20047/tireordering/backend/repository/TireRepository.java` | 輪胎資料存取 |
| `backend/src/main/java/com/fy20047/tireordering/backend/security/JwtAuthenticationFilter.java` | JWT 驗證 filter |
| `backend/src/main/java/com/fy20047/tireordering/backend/security/JwtService.java` | JWT 產生與解析 |
| `backend/src/main/java/com/fy20047/tireordering/backend/security/RefreshTokenService.java` | refresh token 產生/驗證/撤銷 |
| `backend/src/main/java/com/fy20047/tireordering/backend/service/AdminService.java` | 後台帳密驗證與 token 流程 |
| `backend/src/main/java/com/fy20047/tireordering/backend/service/OrderService.java` | 建單、查單、改狀態邏輯 |
| `backend/src/main/java/com/fy20047/tireordering/backend/service/TireService.java` | 輪胎查詢與維護邏輯 |

### 15.2 Backend `resources` 與 `tests`

| 檔案 | 說明 |
|---|---|
| `backend/src/main/resources/application.yaml` | Spring 主要設定（DB/JWT） |
| `backend/src/test/java/com/fy20047/tireordering/backend/BackendApplicationTests.java` | Spring context 基本測試 |
| `backend/src/test/java/com/fy20047/tireordering/backend/security/JwtServiceTest.java` | JWT service 單元測試 |
| `backend/src/test/java/com/fy20047/tireordering/backend/service/AdminServiceTest.java` | Admin service 單元測試 |
| `backend/src/test/java/com/fy20047/tireordering/backend/service/OrderServiceTest.java` | Order service 單元測試 |
| `backend/src/test/java/com/fy20047/tireordering/backend/service/TireServiceTest.java` | Tire service 單元測試 |

### 15.3 Frontend `src`

| 檔案 | 說明 |
|---|---|
| `frontend/src/main.tsx` | React 入口 |
| `frontend/src/App.tsx` | 路由與主框架 |
| `frontend/src/vite-env.d.ts` | Vite 型別定義 |
| `frontend/src/api/adminApi.ts` | 管理端 API 客戶端與 refresh 重試 |
| `frontend/src/context/AuthContext.tsx` | 登入狀態、silent refresh、logout 流程 |
| `frontend/src/components/Layout.tsx` | 版面容器 |
| `frontend/src/components/Navbar.tsx` | 導覽列 |
| `frontend/src/pages/Home.tsx` | 首頁 |
| `frontend/src/pages/About.tsx` | 關於頁 |
| `frontend/src/pages/FindTires.tsx` | 輪胎查詢頁 |
| `frontend/src/pages/Promotions.tsx` | 促銷頁 |
| `frontend/src/pages/RepairServices.tsx` | 保養維修資訊頁 |
| `frontend/src/pages/TireKnowledge.tsx` | 輪胎知識頁 |
| `frontend/src/pages/TireSeries.tsx` | 系列介紹頁 |
| `frontend/src/pages/Order.tsx` | 建單頁 |
| `frontend/src/pages/AdminLogin.tsx` | 管理員登入頁 |
| `frontend/src/pages/AdminTires.tsx` | 管理端輪胎維護頁 |
| `frontend/src/pages/AdminOrders.tsx` | 管理端訂單管理頁 |
| `frontend/src/pages/PagePlaceholder.tsx` | 佔位頁元件 |
| `frontend/src/styles/About.module.css` | About 樣式 |
| `frontend/src/styles/AdminLogin.module.css` | AdminLogin 樣式 |
| `frontend/src/styles/AdminOrders.module.css` | AdminOrders 樣式 |
| `frontend/src/styles/AdminTires.module.css` | AdminTires 樣式 |
| `frontend/src/styles/FindTires.module.css` | FindTires 樣式 |
| `frontend/src/styles/globals.css` | 全域樣式 |
| `frontend/src/styles/Home.module.css` | Home 樣式 |
| `frontend/src/styles/Layout.module.css` | Layout 樣式 |
| `frontend/src/styles/Navbar.module.css` | Navbar 樣式 |
| `frontend/src/styles/PagePlaceholder.module.css` | PagePlaceholder 樣式 |
| `frontend/src/styles/Promotions.module.css` | Promotions 樣式 |
| `frontend/src/styles/RepairServices.module.css` | RepairServices 樣式 |
| `frontend/src/styles/TireKnowledge.module.css` | TireKnowledge 樣式 |
| `frontend/src/styles/TireOrder.module.css` | Order 頁樣式 |
| `frontend/src/styles/TireSeries.module.css` | TireSeries 樣式 |

### 15.4 Infra

| 檔案 | 說明 |
|---|---|
| `infra/.env.example` | 本機 compose 環境變數範本 |
| `infra/docker-compose.yml` | 開發環境 docker compose |
| `infra/docker-compose.prod.yml` | 生產環境 docker compose |

### 15.5 Kubernetes

| 檔案 | 說明 |
|---|---|
| `k8s/base/kustomization.yaml` | base 資源組合入口 |
| `k8s/base/backend-deployment.yaml` | backend Deployment |
| `k8s/base/backend-service.yaml` | backend Service |
| `k8s/base/frontend-deployment.yaml` | frontend Deployment |
| `k8s/base/frontend-service.yaml` | frontend Service |
| `k8s/base/mariadb-service.yaml` | MariaDB Service |
| `k8s/base/mariadb-statefulset.yaml` | MariaDB StatefulSet |
| `k8s/overlays/minikube/kustomization.yaml` | minikube overlay 入口 |
| `k8s/overlays/minikube/namespace.yaml` | namespace 定義 |
| `k8s/overlays/minikube/ingress.yaml` | Ingress 規則 |
| `k8s/overlays/minikube/app-config.yaml` | app config ConfigMap |
| `k8s/overlays/minikube/app-sealedsecret.yaml` | app 密鑰 SealedSecret |
| `k8s/overlays/minikube/db-sealedsecret.yaml` | DB 密鑰 SealedSecret |
| `k8s/overlays/minikube/backend-configmap-env.yaml` | backend env patch |
| `k8s/overlays/minikube/backend-resources.yaml` | backend 資源限制 patch |
| `k8s/overlays/minikube/backend-scale.yaml` | backend replica patch |
| `k8s/overlays/minikube/frontend-nodeport.yaml` | frontend NodePort patch |
| `k8s/overlays/minikube/frontend-resources.yaml` | frontend 資源限制 patch |
| `k8s/overlays/minikube/frontend-scale.yaml` | frontend replica patch |
| `k8s/overlays/minikube/hpa-backend.yaml` | backend HPA |
| `k8s/overlays/minikube/hpa-frontend.yaml` | frontend HPA |
| `k8s/overlays/minikube/pdb-backend.yaml` | backend PDB |
| `k8s/overlays/minikube/pdb-frontend.yaml` | frontend PDB |
| `k8s/overlays/minikube/resourcequota.yaml` | namespace 資源配額 |
| `k8s/overlays/minikube/limitrange.yaml` | Pod/container 預設限制 |

### 15.6 CI/CD

| 檔案 | 說明 |
|---|---|
| `.github/workflows/ci.yml` | 目前 CI pipeline（backend/frontend build, push, update manifest） |
| `argocd/app.yaml` | ArgoCD Application（自動 sync k8s overlay） |
