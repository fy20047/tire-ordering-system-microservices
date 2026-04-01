# Phase 5 Step 8B-1：Gateway 指向 backend 的路由與環境變數盤點

> 文件用途：盤點目前 Gateway 仍依賴 `backend` 的實際位置，並確認每一項可替代路徑，作為後續 Phase 5 拆除 fallback 的依據。  
> 對應 `README.md` §12「Phase 5 待完成項目」第 1 項。

## 1. 盤點範圍與方法

- 掃描範圍：`api-gateway`、`infra/docker-compose*.yml`、`k8s/base/gateway-deployment.yaml`、`SETUP_GUIDE.md`。
- 關鍵字：`BACKEND_BASE_URL`、`backend`、`/api/health`。
- 目標：先找出「仍指向 backend」的路由/環境變數，再對照目前已完成的微服務替代路徑。

## 2. 已完成替代路徑（不再依賴 backend）

| API 路徑 | 目前 Gateway 目標服務 | 依據 |
|---|---|---|
| `/api/admin/login` `/api/admin/refresh` `/api/admin/logout` | `auth-service` | `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java:140-142,153-158` |
| `/api/tires/**` `/api/admin/tires/**` | `tire-service` | `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java:143-145,161-165` |
| `/api/orders/**` `/api/admin/orders/**` | `order-service` | `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java:146-148,168-172` |

## 3. 仍指向 backend 的 Gateway 路由盤點

Step 8B-3 更新後狀態：**無**

- `api-gateway` 已在 `ApiProxyController` 移除未匹配路徑 fallback 到 backend 的邏輯。
- 對於未匹配的 `/api/**` 路徑，Gateway 現在直接回 `404`。
- `/api/health` 已由 `GatewayHealthController` 直接回應，不再透過 fallback。

## 4. 仍指向 backend 的 Gateway 環境變數盤點

Step 8B-3 更新後狀態：**無**

- `api-gateway/src/main/resources/application.yaml` 已移除 `gateway.backend-base-url`。
- `infra/docker-compose.yml`、`infra/docker-compose.prod.yml` 的 `api-gateway` 已移除：
  - `BACKEND_BASE_URL`
  - `depends_on: backend`
- `k8s/base/gateway-deployment.yaml` 已移除 `BACKEND_BASE_URL` env 注入。

## 5. 替代路徑確認結論

- 主業務流量（Auth/Tire/Order）已完成替代路徑，不依賴 backend。
- Gateway 路由層已不再依賴 backend（包含 `/api/health`）。
- 目前 backend 仍存在於部署資源（compose/k8s）層，屬於 Phase 5 下一階段下線工作。

## 6. 後續拆步建議（下一小步）

1. 進入 Phase 5 下一項：從 `infra/docker-compose*.yml` 移除 `backend` 服務節點並驗證本機啟動。
2. 同步規劃並執行 `k8s/base`、`k8s/overlays/minikube` 的 backend 資源移除。
3. 補充 `SETUP_GUIDE.md`：註明 `/api/health` 由 Gateway 自身回應。
