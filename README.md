# tire-ordering-system (輪胎訂購系統)

## 前置需求 (Prereqs)
- 必須安裝 Docker Desktop

## 專案設定 (Setup)
1) 複製一份 `infra/.env.example` 並重新命名為 `infra/.env`
2) 開啟 `.env` 填入必要的變數值（例如：資料庫密碼、管理員帳號密碼、JWT Secret 等）

## 啟動 (本地開發 / Local build)
使用本地的 `Dockerfile` 重新建置映像檔：
```powershell
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

## 啟動 (正式部署 / 從 GHCR 拉取)
從 GitHub Container Registry (GHCR) 下載映像檔來執行：
```powershell
docker compose -f infra/docker-compose.prod.yml --env-file infra/.env up -d
```

## 連線資訊 (Access URLs)
### 本地建置版本 (infra/docker-compose.yml):
- 前端首頁: http://localhost:5173/
- 後台登入: http://localhost:5173/admin/login
- 後端健康狀態: http://localhost:8080/health
- 資料庫管理 (Adminer): http://localhost:8081/

### GHCR 部署版本 (infra/docker-compose.prod.yml):
- 前端首頁: http://localhost/
- 後台登入: http://localhost/admin/login
- 後端健康狀態: http://localhost:8080/health
- 資料庫管理 (Adminer): http://localhost:8081/

## 停止服務 (Stop)
本地開發環境：
```powershell
docker compose -f infra/docker-compose.yml down
```
正式部署環境：
```powershell
docker compose -f infra/docker-compose.prod.yml down
```

## 注意事項 (Notes)
- 如果 GHCR 映像檔設定為 Private (私有)，執行前需先登入：
```powershell
docker login ghcr.io
```

## Kubernetes Secrets (Minikube 環境)
在部署 `k8s/overlays/minikube` 之前必須先建立好 Secret
### 1. 創建 Namespace
```powershell
kubectl create namespace tire-ordering
```
### 2. 建立資料庫相關的 Secret (db-secret)
- 包含資料庫名稱、使用者帳號、密碼以及 Root 密碼
- 要把 'changeme' 替換成想要設定的真實密碼
```powershell
kubectl -n tire-ordering create secret generic db-secret --from-literal=MARIADB_DATABASE=tire_shop --from-literal=MARIADB_USER=app --from-literal=MARIADB_PASSWORD=changeme --from-literal=MARIADB_ROOT_PASSWORD=changeme
```
### 3. 建立應用程式相關的 Secret (app-secret)
- 包含 JWT 加密金鑰、管理員帳號與密碼
- 'changeme' 也要替換成想要設定的真實密碼
```powershell
kubectl -n tire-ordering create secret generic app-secret --from-literal=JWT_SECRET=changeme --from-literal=ADMIN_USERNAME=admin --from-literal=ADMIN_PASSWORD=changeme
```