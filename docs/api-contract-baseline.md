# API 契約基準文件（Phase 0）

## 文件資訊
- 版本：`v1-baseline`
- 建立日期：`2026-03-31`
- 目的：凍結目前單體後端的 API 契約，做為 Phase 1 Gateway 轉發與後續微服務拆分的對照基準。
- 契約來源：
  - `backend/src/main/java/com/fy20047/tireordering/backend/controller/*`
  - `backend/src/main/java/com/fy20047/tireordering/backend/dto/*`
  - `backend/src/main/java/com/fy20047/tireordering/backend/config/SecurityConfig.java`
  - `backend/src/main/java/com/fy20047/tireordering/backend/controller/GlobalExceptionHandler.java`

## 共通規則
- Base URL：依環境而定（例如 `http://localhost:8080`）。
- Content-Type：`application/json`（登入/刷新/登出仍接受 JSON API 呼叫）。
- 時間欄位：`LocalDateTime` 序列化字串（例如 `2026-03-31T14:30:00`）。
- 管理端授權：
  - `POST /api/admin/login`、`POST /api/admin/refresh`、`POST /api/admin/logout` 允許匿名。
  - 其他 `/api/admin/**` 需 `Authorization: Bearer <access_token>` 且角色為 `ADMIN`。
- Refresh Cookie（登入與 refresh 會設定）：
  - Cookie Name 預設：`refreshToken`（可由環境變數覆蓋）
  - Path：`/api/admin`
  - HttpOnly：`true`
  - SameSite 預設：`Lax`
  - Max-Age：預設 `1209600` 秒（14 天）

## Enum 契約
- `InstallationOption`：`INSTALL`、`PICKUP`、`DELIVERY`
- `OrderStatus`：`PENDING`、`CONFIRMED`、`COMPLETED`、`CANCELLED`

## 錯誤回應契約
多數業務/驗證錯誤由全域例外處理器回傳：

```json
{
  "message": "Validation failed",
  "details": {
    "fieldName": "error message"
  }
}
```

- `400 Bad Request`
  - DTO 驗證錯誤（`MethodArgumentNotValidException`）
  - 型別錯誤（`MethodArgumentTypeMismatchException`）
  - 業務參數錯誤（`IllegalArgumentException`）
- `409 Conflict`
  - 業務狀態衝突（`IllegalStateException`，例如輪胎未上架）
- `500 Internal Server Error`
  - 未預期錯誤
- 例外：`POST /api/admin/refresh` 在缺少 refresh cookie 時，回傳 `401` 且 body 為空。

## API 清單（現況凍結）

### 1. 管理員登入/憑證

#### POST `/api/admin/login`
- Auth：不需 access token
- Request Body：
```json
{
  "username": "admin",
  "password": "admin123"
}
```
- 欄位限制：
  - `username`：必填，長度 `<= 100`
  - `password`：必填，長度 `<= 100`
- `200 OK` Response Body：
```json
{
  "token": "<jwt_access_token>",
  "expiresInSeconds": 3600
}
```
- Header：`Set-Cookie`（refresh token）

#### POST `/api/admin/refresh`
- Auth：不需 access token，但需帶 refresh cookie
- `200 OK` Response Body：
```json
{
  "token": "<new_jwt_access_token>",
  "expiresInSeconds": 3600
}
```
- Header：`Set-Cookie`（rotate 後的新 refresh token）
- `401 Unauthorized`：缺少 refresh cookie（空 body）

#### POST `/api/admin/logout`
- Auth：不需 access token（若有 refresh cookie 會一併撤銷）
- `200 OK`：空 body
- Header：`Set-Cookie`（清除 refresh cookie）

### 2. 前台輪胎

#### GET `/api/tires`
- Auth：不需
- Query：
  - `active`（boolean，預設 `true`）
- `200 OK`：
```json
{
  "items": [
    {
      "id": 1,
      "brand": "Michelin",
      "series": "Pilot Sport 5",
      "origin": "EU",
      "size": "225/45R17",
      "price": 4800,
      "isActive": true
    }
  ]
}
```

#### GET `/api/tires/{id}`
- Auth：不需
- Path：
  - `id`：`Long`
- `200 OK`：`TireResponse`（欄位同上單筆）

### 3. 前台建單

#### POST `/api/orders`
- Auth：不需
- Request Body：
```json
{
  "tireId": 1,
  "quantity": 4,
  "customerName": "王小明",
  "phone": "0912345678",
  "email": "user@example.com",
  "installationOption": "INSTALL",
  "deliveryAddress": "台北市中正區...",
  "carModel": "Toyota Altis",
  "notes": "請先電話聯絡"
}
```
- 欄位限制：
  - `tireId`：必填
  - `quantity`：必填，`>= 1`
  - `customerName`：必填，`<= 100`
  - `phone`：必填，`<= 50`
  - `email`：選填，若提供需符合 email，`<= 255`
  - `installationOption`：必填（`INSTALL`/`PICKUP`/`DELIVERY`）
  - `deliveryAddress`：選填，`<= 500`；當 `installationOption=DELIVERY` 時必填
  - `carModel`：必填，`<= 100`
  - `notes`：選填，`<= 1000`
- `201 Created`：
```json
{
  "orderId": 1001,
  "status": "PENDING",
  "message": "訂單已送出，客服將與您聯繫確認。"
}
```

### 4. 後台輪胎管理（需 ADMIN Bearer Token）

#### GET `/api/admin/tires`
- Query（皆選填）：`brand`、`series`、`size`、`active`
- `200 OK`：
```json
{
  "items": [
    {
      "id": 1,
      "brand": "Michelin",
      "series": "Pilot Sport 5",
      "origin": "EU",
      "size": "225/45R17",
      "price": 4800,
      "isActive": true,
      "createdAt": "2026-03-30T10:00:00",
      "updatedAt": "2026-03-30T10:00:00"
    }
  ]
}
```

#### POST `/api/admin/tires`
- Request Body（`AdminTireRequest`）：
```json
{
  "brand": "Michelin",
  "series": "Pilot Sport 5",
  "origin": "EU",
  "size": "225/45R17",
  "price": 4800,
  "isActive": true
}
```
- 欄位限制：
  - `brand`：必填，`<= 100`
  - `series`：必填，`<= 100`
  - `origin`：選填，`<= 50`
  - `size`：必填，`<= 50`
  - `price`：選填，若提供需 `>= 0`
  - `isActive`：必填
- `201 Created`：回傳 `AdminTireResponse`

#### PUT `/api/admin/tires/{id}`
- Request Body：同 `AdminTireRequest`
- `200 OK`：`AdminTireResponse`

#### PATCH `/api/admin/tires/{id}/active`
- Request Body：
```json
{
  "isActive": false
}
```
- `200 OK`：`AdminTireResponse`

### 5. 後台訂單管理（需 ADMIN Bearer Token）

#### GET `/api/admin/orders`
- Query（選填）：`status`（`OrderStatus`）
- `200 OK`：
```json
{
  "items": [
    {
      "id": 1001,
      "status": "PENDING",
      "quantity": 4,
      "customerName": "王小明",
      "phone": "0912345678",
      "email": "user@example.com",
      "installationOption": "INSTALL",
      "deliveryAddress": "台北市中正區...",
      "carModel": "Toyota Altis",
      "notes": "請先電話聯絡",
      "createdAt": "2026-03-30T10:00:00",
      "updatedAt": "2026-03-30T10:00:00",
      "tireId": 1,
      "tireBrand": "Michelin",
      "tireSeries": "Pilot Sport 5",
      "tireOrigin": "EU",
      "tireSize": "225/45R17",
      "tirePrice": 4800
    }
  ]
}
```

#### PATCH `/api/admin/orders/{id}/status`
- Request Body：
```json
{
  "status": "CONFIRMED"
}
```
- `200 OK`：`AdminOrderResponse`

### 6. 健康檢查

#### GET `/health`
#### GET `/api/health`
- Auth：不需
- 成功 `200 OK`：
```json
{
  "timestamp": "2026-03-31T14:30:00Z",
  "status": "UP",
  "db": "UP"
}
```
- 失敗 `503 Service Unavailable`：
```json
{
  "timestamp": "2026-03-31T14:30:00Z",
  "status": "DOWN",
  "db": "DOWN",
  "message": "..."
}
```

## 凍結原則
- 在 Phase 1（Gateway 導入）期間，此文件中的 API 路徑、欄位名稱、回應結構視為凍結契約。
- 若需破壞性變更，必須先更新本文件並在修改紀錄檔標註版本與影響範圍。
