# Smoke Integration 測試挑選說明（Step 4）

## 適用範圍
- 所屬階段：Phase 1
- 所屬步驟：Step 4（驗證前端流程不變）
- 目的：驗證「入口改由 Gateway 接管」後，既有核心功能仍可正常執行。

## 文件目的
這份文件定義「Gateway 入口切換後」的最小整合測試集合，目標是快速驗證核心流程未回歸，而不是一次覆蓋全部 API。

## 什麼是最小集合
最小集合不是「測最少 API」，而是「用最少案例覆蓋最多風險類型」。

本專案的風險類型包含：
- 入口路由風險：`Ingress/Frontend Proxy -> Gateway -> Backend` 是否打通
- 授權風險：Bearer token 保護是否失效
- Cookie 風險：refresh/logout 的 cookie 行為是否正確
- 公開路由風險：不需登入的 API 是否仍可用
- 寫入風險：建單與後台改狀態是否仍可成功寫入
- 失敗路徑風險：未授權或缺 cookie 是否被正確拒絕

## 為何不是所有 API 都放進 smoke
因為 smoke 的任務是「快速擋重大故障」，需要可在每次改動後高頻執行。  
若把所有 API 都放進來，執行時間與維護成本會變高，會降低持續執行率。  
完整 API 覆蓋應放在 SIT/UAT/回歸測試階段。

## 本次選擇的 10 個 smoke 案例
1. `POST /api/admin/login`
2. `POST /api/admin/refresh`（帶 cookie，預期成功）
3. `GET /api/tires?active=true`
4. `POST /api/orders`
5. `GET /api/admin/orders`（帶 Bearer token）
6. `PATCH /api/admin/orders/{id}/status`（帶 Bearer token）
7. `POST /api/admin/logout`
8. `POST /api/admin/refresh`（登出後同 session，預期 `401`）
9. `GET /api/admin/orders`（不帶 token，預期 `403`）
10. `POST /api/admin/refresh`（不帶 cookie，預期 `401`）

## 覆蓋矩陣（案例 -> 風險類型）
- Login：授權入口、token 發放、refresh cookie 設置
- Refresh 成功：cookie 驗證鏈路、token 續期
- Public tires：公開讀取路由
- Create order：公開寫入路由與資料寫入
- Admin list orders：受保護讀取路由
- Admin patch status：受保護寫入路由
- Logout：cookie 清除行為
- Refresh after logout 401：登出後 token 續期保護
- Admin no token 403：Bearer token 保護
- Refresh no cookie 401：cookie 保護

## 執行方式
已提供可重複執行腳本：

- `scripts/smoke/run-smoke-gateway.ps1`

範例：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke\run-smoke-gateway.ps1 `
  -BaseUrl "http://localhost:8080" `
  -AdminUsername "admin" `
  -AdminPassword "admin123"
```

## 判定標準
- 所有 10 案例都通過：可判定本次「入口改造」未造成核心流程回歸。
- 任一案例失敗：不可視為通過，需先修正再進下一階段拆分。
