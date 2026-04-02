# Monolith 改造 Microservices：差異、調整重點與實作過程

> 文件用途：整理本專案從 monolith 拆分成 microservices 的「前後差異」、「逐步改造過程」、「常見注意事項」，並提供一段可直接口述的精簡版本。  
> 適用對象：需要回顧本次改造決策、或準備對外說明這次架構演進的人。

## 1) 轉換前後的核心差異

| 面向 | Monolith（原本） | Microservices（現在） |
|---|---|---|
| API 入口 | 前端直接或間接打 `backend` | 一律進 `api-gateway`，再分流到各服務 |
| 服務邊界 | Auth/Tire/Order 全在同一個 Spring Boot | `auth-service` / `tire-service` / `order-service` 各自獨立 |
| 認證責任 | backend 同時做登入與業務授權 | Auth 專責簽發；Gateway+各服務負責驗章與授權 |
| Token 策略 | 單體內共享邏輯，耦合較高 | RS256（私鑰在 auth，公鑰在驗章方） |
| 訂單資料模型 | 容易直接關聯商品主檔 | `order-service` 寫入 snapshot，保留歷史一致性 |
| 部署拓樸 | compose/k8s 有 backend 節點 | compose/k8s 已移除 backend，純微服務拓樸 |
| CI/CD | backend/frontend 為主 | `api-gateway/auth-service/tire-service/order-service/frontend` |
| 回滾策略 | 多為整體回滾 | 可按服務回滾（但要注意跨服務相依） |

## 2) 這次改造的分階段過程（實際落地順序）

### Phase 1：先建立 Gateway，不先動業務邏輯

- 先把流量入口統一成 `frontend -> api-gateway`。
- 一開始允許 `/api/**` fallback 到舊 backend，目標是先完成「入口切換」而不是一次重寫功能。
- 主要價值：
  - 後續每次拆服務，只要改 Gateway 路由，不必再改前端呼叫方式。

### Phase 2：先抽 Auth（同時切 RS256）

- 把登入/refresh/logout 收斂到 `auth-service`。
- token 簽發改為 RS256（私鑰只在 auth，其他服務只持公鑰驗章）。
- backend 的重複 Auth 入口先改為 feature flag 可關閉，避免雙入口長期並存。
- 主要價值：
  - 先把「安全邊界」獨立出來，後續服務拆分才不會每個服務都自帶登入責任。

### Phase 3：抽 Tire Service

- 將 `/api/tires/**` 與 `/api/admin/tires/**` 搬到 `tire-service`。
- Gateway 只按路徑分流，前端 API 路徑維持不變。
- backend 內重複 Tire 入口改為預設停用。
- 主要價值：
  - 把「商品主檔」責任單一化，減少訂單與商品的耦合。

### Phase 4：抽 Order Service + Snapshot

- 將建單與後台訂單查改搬到 `order-service`。
- 建單時由 `order-service` 呼叫 `tire-service` 驗證商品，並把商品資訊寫成 snapshot。
- 驗證重點：商品後續改價改名不應污染舊訂單。
- 主要價值：
  - 避免跨服務即時 join 造成歷史資料不一致。

### Phase 5：收斂與下線 Monolith

- 先移除 Gateway fallback 與 `BACKEND_BASE_URL`。
- 再從 compose/k8s 移除 backend 服務節點與對應注入。
- 再收斂 CI/CD，移除 backend build/push 與 manifest 更新。
- 主要價值：
  - 從「功能已拆」走到「運行路徑也完全不再依賴 monolith」。

## 3) 改造成 microservices 時，必調整的項目

### A. 路由與入口

- 入口統一（Gateway）要先完成，再拆服務。
- 拆一個服務就加一段明確路由規則，不要一次改全部路由。
- fallback 最後才移除，且要先補齊替代端點（例如 `/api/health`）。

### B. 認證與授權

- Auth 服務要先成形，再抽業務服務，避免授權責任混亂。
- 簽發與驗章責任要分離（RS256 很適合這個場景）。
- backend 的舊入口不要直接刪，先可控停用（feature flag）再下線。

### C. 資料邊界

- 先做到「schema 邊界清楚」，再考慮是否分實例。
- 對歷史資料（例如訂單）優先採 snapshot，避免主檔更新帶來歷史污染。
- 不要把服務拆分與資料庫類型切換（例如再改 NoSQL）同時做。

### D. 部署與環境變數

- compose/k8s/secret/configmap 要同步調整，避免程式完成但部署還接舊參數。
- 服務下線時，要清掉對應的 `depends_on`、patch、HPA、PDB、image override。
- 需要做一次「orphan 清理」驗證（例如 compose `--remove-orphans`）。

### E. CI/CD 與版本流

- CI 應改為「多服務並行建置 + 推送」。
- manifest 更新應只更新現役微服務 image，不應再更新 backend。
- 在 main 以外（PR）應只做 build/test，不要 push image。

### F. 驗證策略（非常重要）

- 每階段都要有 smoke，且分成：
  - 功能成功路徑
  - 授權失敗路徑（401/403）
  - 邊界回歸（例如 snapshot A/B）
- 驗證命令要文件化，避免只靠口頭傳遞。

## 4) 常見陷阱與注意事項

1. 只改程式不改部署：最常見，會導致「本地能跑、叢集失敗」。
2. fallback 太早移除：容易讓健康檢查或歷史腳本一起壞掉。
3. 雙入口長期共存：會讓責任混淆，排錯成本快速上升。
4. 沒做 snapshot 就拆訂單：歷史資料一致性會出問題。
5. CI 還在發舊服務鏡像：會把舊節點反覆帶回來。
6. 沒做分段驗證：一次改太多，失敗時很難定位。
7. 文件與現況不同步：新同仁或值班人員容易操作錯誤。
8. 忽略回滾條件：微服務回滾需要明確對應服務與版本。

## 5) 如果要再重做一次，建議的執行順序

1. 先統一入口（Gateway）。
2. 先抽 Auth，再抽商品、最後抽訂單。
3. 每次只拆一個服務邊界，保留可回切旗標。
4. 先確保 smoke 可重跑，再做 fallback 下線。
5. 最後才做「部署下線 + CI 收斂 + 文件收尾」。

## 6) 可口述的精簡版本（60~90 秒）

> 我們這次不是直接把 monolith 拆掉，而是先把入口統一到 API Gateway，讓前端呼叫路徑固定不變。  
> 接著按風險順序拆服務：先抽 Auth（並改 RS256），再拆 Tire，再拆 Order，同時用 snapshot 解決訂單歷史一致性。  
> 每拆一段就做 smoke 與授權回歸，先用 feature flag 停用舊入口，而不是立刻刪除。  
> 最後在 Phase 5 才移除 Gateway fallback、把 backend 從 compose/k8s 下線，再把 CI 改成只發佈微服務。  
> 所以現在對外運行路徑已是純微服務，後續重點是持續把文件、CI、runbook 維持與現況同步。

