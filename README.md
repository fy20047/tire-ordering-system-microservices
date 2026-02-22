# Tire Ordering System (輪胎訂購系統)

本專案涵蓋了從應用程式開發（Spring Boot + React）、容器化（Docker）、Docker Compose 到生產環境部署（Kubernetes + ArgoCD）的所有環節。

### 後續規劃 (Future Roadmap)
- 系統上線：本專案目前已完成核心業務邏輯以及完整的 DevOps 流程包裝（包含容器化、K8s 部署設定與 CI/CD 測試）。
- 後續迭代：未來的核心目標是將系統正式部署至雲端環境上線。實際的部署架構與後續功能增添，待進一步的需求討論後，再進行調整與實作。

## 專案總覽 (Project Overview)

- **功能目標**：提供顧客瀏覽輪胎、下訂單的前台，以及管理員管理訂單、輪胎庫存的後台。
- **核心技術**：Java 21, Spring Boot, React, TypeScript, Docker, Kubernetes, GitHub Actions, ArgoCD。
- 專案實作重點不僅在於業務邏輯的實現，更在於建構一套可擴展、高可用且自動化的 DevOps 架構。

## 技術棧 (Tech Stack)
| Category | Technologies | 
|----------|--------------| 
| **Backend** | Java 21, Spring Boot 3, Spring Security, JPA, Lombok | 
| **Frontend** | React 18, TypeScript, Vite, CSS Modules, Nginx | 
| **Database** | MariaDB | 
| **DevOps** | Docker, Kubernetes (Minikube), GitHub Actions, ArgoCD | 
| **Tools** | Maven, npm, Kustomize系統架構 (System Architecture)|

### 1. 後端 (Backend)
* **資料庫存取**：使用 Spring Data JPA 搭配 MariaDB，並透過 K8s Secret 安全管理連線憑證。
* **API 設計**：
    * 標準 RESTful API，例如 `OrderController` 接收 `CreateOrderRequest` DTO，經過驗證 (`@Valid`) 後轉交 Service 層處理。
    * 包含 Health Check 接口 (`/api/health`) 供 K8s Probe 使用。
* **安全性設計**：
    * 使用 **Spring Security** 搭配 **JWT** 機制。
    * 實作 **雙 Token 機制**：實作了 Access Token (短效期) + Refresh Token (長效期，存於 HttpOnly Cookie) 的安全驗證流程。
    * `SecurityConfig` 設定了公開 API (`/api/tires`, `/api/orders`) 與管理員 API (`/api/admin/**`) 的權限隔離。

### 2. 前端 (Frontend)
* **路由管理**：使用 `react-router-dom`，區分公開頁面（首頁、促銷、訂購）與後台管理頁面（登入、訂單管理、庫存管理）。
* **API 封裝**：
    * `adminApi.ts` 封裝了 `fetch` 請求，實作了 **自動刷新 Token (Silent Refresh)** 的機制。
    * 當 Access Token 過期 (401/403) 時自動透過 Refresh Token 換發新憑證並重試請求，且 Access Token 僅存在記憶體中。
* **開發體驗**：使用 Vite 進行快速建置，並透過 ESLint 維持程式碼品質。

### 3. 容器化與開發環境 (Docker & Infrastructure)
- **Docker 建置 (Multi-stage Build)**：
    - **Backend**：分為 `build` (Maven 編譯) 與 `runtime` (輕量化 JRE) 兩階段。
    - **Frontend**：分為 `build` (Node.js 編譯) 與 `runtime` (Nginx 伺服器) 兩階段，將 React 產出的靜態檔交由 Nginx 託管。
- **本地開發 (Docker Compose)**：
    - `docker-compose.yml` 定義了 `mariadb`, `adminer` (資料庫管理介面), `backend`, `frontend` 四個服務，讓開發者可以一鍵啟動完整的本地環境。

### 4. Kubernetes 部署架構 (K8s)
#### 本專案採用 **Kustomize** 進行環境配置管理。
- **Base 層**：定義通用的 Deployment 和 Service。
    - Backend Deployment 設定了 `readinessProbe` 和 `livenessProbe`，確保 Pod 健康狀態。
    - 使用 `envFrom` 讀取 Secret (如 `app-secret`)，並透過環境變數注入資料庫帳密。
- **Overlays 層 (Minikube)**：針對特定環境的覆寫。
    - **HPA (Horizontal Pod Autoscaler)**：根據負載自動擴縮。
    - **PDB (Pod Disruption Budget)**：確保在維護時維持最少可用 Pod 數量。
    - **Ingress**：使用 Nginx Ingress Controller，設定域名 `kuang-i-tire` 轉發流量至 Frontend Service。

### 5. CI/CD Pipeline
- **CI (GitHub Actions)**：自動化測試、建置 Docker Image、推送至 GHCR，並自動更新 K8s Manifest 中的 Image Tag。
- **CD (ArgoCD)**：監聽 Git Repository 變更，自動同步 (Sync) 並部署至 K8s Cluster，具備自我修復 (Self-Healing) 能力。

## 如何開始 (Getting Started)

- 在本地環境啟動專案（Docker Compose）或部署至 Kubernetes 的詳細步驟，請參閱 **SETUP_GUIDE.md** ：


### 📂 目錄結構摘要

```text
├── backend/          # Spring Boot 後端原始碼
├── frontend/         # React 前端原始碼
├── infra/            # Docker Compose 本地開發配置
├── k8s/              # Kubernetes Manifests (Kustomize)
│   ├── base/         # 通用配置
│   └── overlays/     # 環境特定配置 (Minikube)
├── argocd/           # ArgoCD Application 定義檔
└── .github/          # GitHub Actions CI/CD 流程
```
