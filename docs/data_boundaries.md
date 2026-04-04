# 資料邊界規則

本文件用於定義各微服務的資料擁有權、跨服務存取規則，以及資料遷移時的基本原則。

## 1. 資料擁有權

### auth-service
`auth-service` 擁有並管理以下資料：
- `admin`
- `refresh_token`

`auth-service` 負責：
- 管理員登入驗證
- access token / refresh token 的簽發與更新
- refresh token 的撤銷與登出流程

### tire-service
`tire-service` 擁有並管理以下資料：
- `tire`

`tire-service` 負責：
- 前台輪胎列表查詢
- 前台單筆輪胎查詢
- 後台輪胎新增、編輯、上下架管理
- 提供其他服務查詢輪胎可售狀態與商品資訊

### order-service
`order-service` 擁有並管理以下資料：
- `orders`
- `order_items`
- `tire_snapshot`

`order-service` 負責：
- 建立訂單
- 管理訂單狀態
- 保存下單當下所需的輪胎快照資料

## 2. 跨服務資料存取規則

### 基本原則
- 每個 service 只能直接讀寫自己擁有的資料。
- 不同 service 之間不得直接查詢彼此的資料表。
- 跨服務資料需求必須透過 API 呼叫完成。

### 具體限制
- `order-service` 不得直接查詢 `tire-service` 的資料表。
- `order-service` 驗證輪胎是否存在、是否可販售時，必須透過 `tire-service` 的 API 完成。
- `auth-service` 不應直接查詢 `order-service` 或 `tire-service` 的資料表。
- `tire-service` 不應直接查詢 `order-service` 的資料表。

## 3. 訂單與輪胎快照規則

建立訂單時，`order-service` 應透過 `tire-service` API 取得必要的輪胎資料，並在自己的資料庫中保存快照資料。

`tire_snapshot` 建議至少保存以下欄位：
- `tireId`
- `brand`
- `series`
- `size`
- `unitPrice`

這樣做的目的：
- 避免訂單查詢依賴即時商品資料
- 避免商品後續修改影響歷史訂單內容
- 確保 `order-service` 具備自己的資料完整性

## 4. 資料遷移策略

當系統從共用資料來源逐步演進到明確資料邊界時，應遵守以下策略：

1. 遷移前必須先備份資料。
2. 優先建立新資料表，不直接修改或刪除舊資料表。
3. 將舊資料遷移至新資料表。
4. 驗證資料筆數是否一致，並進行抽樣檢查。
5. 驗證無誤後，再切換讀寫流程。
6. 舊資料表應暫時保留一段時間，不應立即刪除。

## 5. 建議的演進方向

現階段可先做到以下目標：
- 先劃清各 service 的資料 ownership
- 同一個 DB instance 下，逐步拆成各自獨立的 database 或 schema
- 確保各 service 僅使用自己的連線設定與 migration
- 將跨服務資料需求改為 API 呼叫，而不是共享資料表

本文件的目的不是要求一次完成所有拆分，而是確保後續演進方向一致，避免形成共享資料庫的分散式單體。