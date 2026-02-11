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
### 2. 建立 Secret
2-1. 建立資料庫相關的 Secret (db-secret)
- 包含資料庫名稱、使用者帳號、密碼以及 Root 密碼
- 要把 'changeme' 替換成想要設定的真實密碼
```powershell
kubectl -n tire-ordering create secret generic db-secret --from-literal=MARIADB_DATABASE=tire_shop --from-literal=MARIADB_USER=app --from-literal=MARIADB_PASSWORD=changeme --from-literal=MARIADB_ROOT_PASSWORD=changeme
```
2-2. 建立應用程式相關的 Secret (app-secret)
- 包含 JWT 加密金鑰、管理員帳號與密碼
- 'changeme' 也要替換成想要設定的真實密碼
```powershell
kubectl -n tire-ordering create secret generic app-secret --from-literal=JWT_SECRET=changeme --from-literal=ADMIN_USERNAME=admin --from-literal=ADMIN_PASSWORD=changeme
```

## Sealed Secrets (Git-safe secrets)
Purpose: keep encrypted secrets in Git. Only the Sealed Secrets controller inside the cluster can unseal them into normal Secrets.

### Why SealedSecret?
- The SealedSecret contains `encryptedData`, not raw values.
- Only the controller's private key can decrypt it (cluster-specific).

### Install controller (cluster side)
```powershell
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.27.1/controller.yaml
```
If you hit CRD annotation errors, retry with:
```powershell
kubectl apply --server-side --force-conflicts -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.27.1/controller.yaml
```
Controller runs in `kube-system`, so `kubeseal` must use `--controller-namespace kube-system`.

### Install kubeseal (local)
What failed:
- `choco install kubeseal` failed (package not found).

Working method (manual download from GitHub release):
```powershell
$version = "0.27.1"
$dst = "C:\path\to\repo\tools\kubeseal"
New-Item -ItemType Directory -Force -Path $dst | Out-Null
Invoke-WebRequest -Uri "https://github.com/bitnami-labs/sealed-secrets/releases/download/v$version/kubeseal-$version-windows-amd64.tar.gz" -OutFile "$dst\kubeseal-$version-windows-amd64.tar.gz"
tar -xf "$dst\kubeseal-$version-windows-amd64.tar.gz" -C $dst
$dst\kubeseal.exe --version
```
Tip: keep `tools/kubeseal/` local (do not commit the binary).

### Create SealedSecret from existing Secrets
```powershell
kubectl -n tire-ordering get secret app-secret -o yaml `
  | .\tools\kubeseal\kubeseal.exe --controller-namespace kube-system --format yaml `
  > k8s/overlays/minikube/app-sealedsecret.yaml

kubectl -n tire-ordering get secret db-secret -o yaml `
  | .\tools\kubeseal\kubeseal.exe --controller-namespace kube-system --format yaml `
  > k8s/overlays/minikube/db-sealedsecret.yaml
```

### Add to kustomization
```yaml
resources:
  - app-sealedsecret.yaml
  - db-sealedsecret.yaml
```

### Apply and verify
```powershell
kubectl apply -k k8s/overlays/minikube
kubectl -n tire-ordering get sealedsecrets
kubectl -n tire-ordering get secrets | findstr secret
```

### Optional: remove plaintext Secrets (SealedSecret will recreate them)
```powershell
kubectl -n tire-ordering delete secret app-secret db-secret
kubectl -n tire-ordering rollout restart deployment backend
```

### Notes
- `encryptedData` looks like random text because it is encrypted.
- To change values, re-run `kubeseal` and re-apply.

## Kubernetes (Minikube) 指令
### 1. 啟動cluster 
啟動 Minikube (使用 Docker 作為 Driver)，並切換 kubectl context 到 minikube 
```powershell
minikube start --driver=docker
kubectl config use-context minikube
```
### 2. 部署應用程式
使用 Kustomize 部署會用到的 K8s 資源 (DB, Backend, Frontend)，第一次部署前，要先確定有手動建立 Secret (上方的 db-secret, app-secret)
```powershell
# Apply manifests
kubectl apply -k k8s/overlays/minikube
```
### 3. 檢查狀態
部署後確認所有 Pods 是否都成功啟動 (Running)
```powershell
# Check status
kubectl -n tire-ordering get pods
kubectl -n tire-ordering get svc
```
### 4. 開啟前端頁面
Minikube 會自動分配一個 IP 和 Port，用下方指令獲取網站網址
```powershell
minikube service frontend -n tire-ordering --url
```
### 5. 修改後重啟
#### 1) commit + push 後，CI 會把 image 推到 GHCR，tag = 該 commit 的 SHA
#### 2) 此時要把 k8s/overlays/minikube/kustomization.yaml 裡的 newTag 改成那個 SHA
#### 3) 執行套用 
```powershell
kubectl apply -k k8s/overlays/minikube
```
#### 備註
如果已經改 tag，就不需要 rollout restart，只有在 tag 沒變（像是用 latest）時才需要：
```powershell
kubectl -n tire-ordering rollout restart deployment frontend
kubectl -n tire-ordering rollout restart deployment backend
```

## ArgoCD 部署 (Minikube)
最後用 ArgoCD 🐙 進行 GitOps 自動化部署

### 1. 安裝 ArgoCD
```powershell
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
# 如果遇到 "CRD annotation too long" 錯誤，改用 Server-side Apply 安裝：
# kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml --server-side --force-conflicts
```
### 2. 確認安裝狀態，等待所有 Pods 變成 Running
```powershell
kubectl -n argocd get pods
```
### 3. 部署 Application 設定
告訴 ArgoCD 開始監控這個專案 (namespace)
```powershell
kubectl apply -f argocd/app.yaml
```
### 4. 開啟 ArgoCD UI
這邊使用 Port-forward 將 UI 對應到本機 8082 Port，443 是 ArgoCD Server Service 在 cluster 內的 Port
```powershell
kubectl -n argocd port-forward svc/argocd-server 8082:443
```
接著就可以用瀏覽器開啟：https://localhost:8082 (忽略憑證不安全警告)

### 5. 取得登入密碼 (PowerShell)
```powershell
$pwd = kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}"
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($pwd))
```

## K8s 容量控制 (Minikube)
- HPA (HorizontalPodAutoscaling)：根據 CPU 使用率自動伸縮前後端 (最少 3 個，最多 6 個)
- PDB (PodDisruptionBudget)：在中斷期間 (如維護時) 確保前後端至少各有 2 個 Pod 可用
- ResourceQuota: 限制 Namespace 中 CPU、記憶體與 Pod 的總數量上限
- LimitRange: 設定容器的預設 CPU/記憶體請求 (requests) 與限制 (limits)
- 在專案前後端的 Deployment 都定義資源請求與限制 (Resource requests/limits)

啟用 HPA 所需的 metrics server:
```powershell
minikube addons enable metrics-server
```
