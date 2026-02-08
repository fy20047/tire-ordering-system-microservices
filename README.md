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
