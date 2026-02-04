# =========================
# 1. Build
# =========================
# 使用 Node.js 20 的 Alpine Linux image
FROM node:20-alpine AS build
# 設定 container 的工作目錄為 /app
WORKDIR /app

# 只先複製 package.json（需求清單）跟 package-lock.json（精確版本鎖）
# 這樣可以用 Docker cache，避免每次都重新 npm install
COPY package.json package-lock.json ./

# 使用 npm ci 安裝套件
# npm ci：
# - 依照 package-lock.json
# - 安裝速度快
# - 適合 CI / Docker
RUN npm ci

# 把剩下的前端原始碼複製進來
COPY . .
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
# 執行前端建置（Vite / React 會產生 dist/ 資料夾）
RUN npm run build

# =========================
# 2. Runtime
# =========================
# 使用 Nginx 把 HTML / JS / CSS 送給瀏覽器
FROM nginx:alpine

# 覆蓋 Nginx 預設設定
# nginx.conf 通常用來處理：
# - SPA rewrite（/ -> /index.html）
# - API proxy（/api -> backend）
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 從「build 階段」複製編譯後的靜態檔案
# dist → Nginx 的網站目錄
COPY --from=build /app/dist /usr/share/nginx/html

# 宣告容器會使用 80 port（文件用途）
EXPOSE 80

# 啟動 Nginx，daemon off 表示在前景執行（Docker 必須）
CMD ["nginx", "-g", "daemon off;"]
