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

| 類型 | 現況 | 影響 | 依據 |
|---|---|---|---|
| fallback 路由 | 目前 `ApiProxyController` 將「不符合 Auth/Tire/Order 規則的 `/api/**`」全部導向 `backendBaseUrl` | 任何未明確匹配的 API 仍會經過 monolith backend | `api-gateway/src/main/java/com/fy20047/tireordering/apigateway/ApiProxyController.java:138-150` |

補充（Step 8B-2 已完成）：
- `api-gateway` 已新增 `GatewayHealthController`，`/api/health` 直接由 Gateway 回應，不再落入 fallback。
- 對應檔案：`api-gateway/src/main/java/com/fy20047/tireordering/apigateway/GatewayHealthController.java`

## 4. 仍指向 backend 的 Gateway 環境變數盤點

| 位置 | 目前設定 | 備註 |
|---|---|---|
| `api-gateway` 應用設定 | `gateway.backend-base-url: ${BACKEND_BASE_URL:http://backend:8080}` | 仍保留 fallback 目標位址 | `api-gateway/src/main/resources/application.yaml:17` |
| 本機 compose | `BACKEND_BASE_URL: http://backend:8080` | `api-gateway` 服務仍依賴 backend service 名稱 | `infra/docker-compose.yml:136` |
| 本機 compose | `depends_on: backend` | fallback 流量存在時需要 backend 容器 | `infra/docker-compose.yml:143` |
| prod compose | `BACKEND_BASE_URL: http://backend:8080` | 與本機 compose 同步，仍保留 fallback 參數 | `infra/docker-compose.prod.yml:123` |
| prod compose | `depends_on: backend` | 與本機 compose 同步 | `infra/docker-compose.prod.yml:130` |
| k8s base | `env BACKEND_BASE_URL=http://backend:8080` | Gateway deployment 仍注入 backend fallback 位址 | `k8s/base/gateway-deployment.yaml:27-28` |

## 5. 替代路徑確認結論

- 主業務流量（Auth/Tire/Order）已完成替代路徑，不依賴 backend。
- 目前仍依賴 backend 的主要剩餘路徑是：所有未明確匹配的 fallback API。
- `/api/health` 依賴已解除，現在由 Gateway 自身回應。

## 6. 後續拆步建議（下一小步）

1. 移除 `ApiProxyController` 的 fallback 分支與 `BACKEND_BASE_URL` 設定。
2. 同步更新 compose/k8s 的 `BACKEND_BASE_URL` 與 `depends_on backend`。
3. 更新 `SETUP_GUIDE.md` 的健康檢查說明，補充 `/api/health` 已由 Gateway 提供。
