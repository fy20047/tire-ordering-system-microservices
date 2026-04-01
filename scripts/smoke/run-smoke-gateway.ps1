<#
用途說明：
這支腳本用來執行「Gateway 入口切換後」的 Smoke Integration 測試。
目標是快速確認核心鏈路仍正常：Ingress/Proxy/Auth/Cookie/Public/Admin/Write path 都可用。
並覆蓋 Phase 4 的關鍵驗收：Order Service 建單成功/失敗與 Snapshot 不受輪胎後續修改影響。

使用方式（本機範例）：
powershell -ExecutionPolicy Bypass -File .\scripts\smoke\run-smoke-gateway.ps1 `
  -BaseUrl "http://localhost:8080" `
  -AdminUsername "admin" `
  -AdminPassword "admin123"
#>

[CmdletBinding()]
param(
    # API 基底位址（預設走本機入口）
    [string]$BaseUrl = "http://localhost:8080",

    # 後台帳號（可改由環境變數 ADMIN_USERNAME 提供）
    [string]$AdminUsername,

    # 後台密碼（可改由環境變數 ADMIN_PASSWORD 提供）
    [string]$AdminPassword,

    # refresh cookie 名稱，需與後端設定一致
    [string]$RefreshCookieName = "refreshToken",

    # 單次 API 逾時秒數
    [int]$TimeoutSec = 30
)

# 基本安全設定：未宣告變數視為錯誤，發生錯誤就中止流程
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# 測試輸出工具：統一 PASS/STEP 格式，方便閱讀執行紀錄
function Write-Step([string]$Message) {
    Write-Host "[STEP] $Message" -ForegroundColor Cyan
}

function Write-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-Fail([string]$Message) {
    throw "[FAIL] $Message"
}

# 從 Invoke-WebRequest 例外中提取 HTTP Status Code（支援 Windows PowerShell 與 PowerShell 7）
function Get-StatusCodeFromException([System.Exception]$Exception) {
    if ($null -eq $Exception.Response) {
        return $null
    }
    try {
        return [int]$Exception.Response.StatusCode
    } catch {
        return $null
    }
}

# 從失敗回應讀取 body，讓錯誤訊息更可診斷
function Get-ResponseBodyFromException([System.Exception]$Exception) {
    if ($null -eq $Exception.Response) {
        return ""
    }
    try {
        $stream = $Exception.Response.GetResponseStream()
        if ($null -eq $stream) {
            return ""
        }
        $reader = New-Object System.IO.StreamReader($stream)
        $text = $reader.ReadToEnd()
        $reader.Close()
        return $text
    } catch {
        return ""
    }
}

# 通用 API 呼叫器：
# 1) 送出 HTTP 請求
# 2) 驗證狀態碼
# 3) 回傳 body/headers 給後續檢查
function Invoke-ApiRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][int]$ExpectedStatus,
        [hashtable]$Headers,
        $Body,
        [Parameter(Mandatory = $true)][Microsoft.PowerShell.Commands.WebRequestSession]$Session
    )

    Write-Step "$Name -> $Method $Uri"

    $requestParams = @{
        Uri         = $Uri
        Method      = $Method
        TimeoutSec  = $TimeoutSec
        WebSession  = $Session
        ErrorAction = "Stop"
    }

    if ($null -ne $Headers) {
        $requestParams["Headers"] = $Headers
    }

    if ($null -ne $Body) {
        $requestParams["Body"] = ($Body | ConvertTo-Json -Depth 10)
        $requestParams["ContentType"] = "application/json"
    }

    $statusCode = $null
    $responseText = ""
    $responseHeaders = $null

    try {
        $response = Invoke-WebRequest @requestParams
        $statusCode = [int]$response.StatusCode
        $responseText = $response.Content
        $responseHeaders = $response.Headers
    } catch {
        $statusCode = Get-StatusCodeFromException $_.Exception
        $responseText = Get-ResponseBodyFromException $_.Exception
        $responseHeaders = @{}
        if ($null -eq $statusCode) {
            throw
        }
    }

    if ($statusCode -ne $ExpectedStatus) {
        Write-Fail "$Name 預期狀態碼 $ExpectedStatus，實際為 $statusCode。Body=$responseText"
    }

    Write-Pass "$Name 狀態碼符合預期 ($ExpectedStatus)"

    return [PSCustomObject]@{
        StatusCode = $statusCode
        BodyText   = $responseText
        Headers    = $responseHeaders
    }
}

# 將 JSON 文字轉為物件；若非 JSON 或空字串，回傳 $null
function Convert-JsonOrNull([string]$BodyText) {
    if ([string]::IsNullOrWhiteSpace($BodyText)) {
        return $null
    }
    try {
        return $BodyText | ConvertFrom-Json -ErrorAction Stop
    } catch {
        return $null
    }
}

# 從後台訂單列表中依 id 取出指定訂單；若不存在則直接失敗，避免後續檢查誤判。
function Get-OrderFromAdminList {
    param(
        [Parameter(Mandatory = $true)]$AdminOrdersJson,
        [Parameter(Mandatory = $true)][long]$OrderId,
        [Parameter(Mandatory = $true)][string]$ScenarioName
    )

    if ($null -eq $AdminOrdersJson -or $null -eq $AdminOrdersJson.items) {
        Write-Fail "$ScenarioName：後台訂單列表回應缺少 items。"
    }

    $order = $AdminOrdersJson.items | Where-Object { $_.id -eq $OrderId } | Select-Object -First 1
    if ($null -eq $order) {
        Write-Fail "$ScenarioName：後台列表未找到 orderId=$OrderId。"
    }

    return $order
}

# 驗證訂單內的輪胎快照欄位，確保 snapshot 與預期版本完全一致。
function Assert-OrderSnapshot {
    param(
        [Parameter(Mandatory = $true)]$OrderItem,
        [Parameter(Mandatory = $true)][long]$ExpectedTireId,
        [Parameter(Mandatory = $true)][string]$ExpectedBrand,
        [Parameter(Mandatory = $true)][string]$ExpectedSeries,
        [Parameter(Mandatory = $true)][string]$ExpectedOrigin,
        [Parameter(Mandatory = $true)][string]$ExpectedSize,
        [Parameter(Mandatory = $true)][int]$ExpectedPrice,
        [Parameter(Mandatory = $true)][string]$ScenarioName
    )

    if ($OrderItem.tireId -ne $ExpectedTireId) {
        Write-Fail "$ScenarioName：tireId 預期 $ExpectedTireId，實際 $($OrderItem.tireId)。"
    }
    if ($OrderItem.tireBrand -ne $ExpectedBrand) {
        Write-Fail "$ScenarioName：tireBrand 預期 '$ExpectedBrand'，實際 '$($OrderItem.tireBrand)'。"
    }
    if ($OrderItem.tireSeries -ne $ExpectedSeries) {
        Write-Fail "$ScenarioName：tireSeries 預期 '$ExpectedSeries'，實際 '$($OrderItem.tireSeries)'。"
    }
    if ($OrderItem.tireOrigin -ne $ExpectedOrigin) {
        Write-Fail "$ScenarioName：tireOrigin 預期 '$ExpectedOrigin'，實際 '$($OrderItem.tireOrigin)'。"
    }
    if ($OrderItem.tireSize -ne $ExpectedSize) {
        Write-Fail "$ScenarioName：tireSize 預期 '$ExpectedSize'，實際 '$($OrderItem.tireSize)'。"
    }
    if ($null -eq $OrderItem.tirePrice -or [int]$OrderItem.tirePrice -ne $ExpectedPrice) {
        Write-Fail "$ScenarioName：tirePrice 預期 $ExpectedPrice，實際 $($OrderItem.tirePrice)。"
    }

    Write-Pass "$ScenarioName：snapshot 欄位符合預期"
}

# 準備登入帳密：優先使用參數，其次環境變數
$resolvedAdminUsername = if (-not [string]::IsNullOrWhiteSpace($AdminUsername)) { $AdminUsername } else { $env:ADMIN_USERNAME }
$resolvedAdminPassword = if (-not [string]::IsNullOrWhiteSpace($AdminPassword)) { $AdminPassword } else { $env:ADMIN_PASSWORD }

if ([string]::IsNullOrWhiteSpace($resolvedAdminUsername)) {
    Write-Fail "缺少 AdminUsername，請用參數或環境變數 ADMIN_USERNAME 提供。"
}
if ([string]::IsNullOrWhiteSpace($resolvedAdminPassword)) {
    Write-Fail "缺少 AdminPassword，請用參數或環境變數 ADMIN_PASSWORD 提供。"
}

# 建立主測試 session：用於保存 refresh cookie 與後續需登入的流程
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 統一 header（Bearer token 會在登入後動態補上）
$authorizedHeaders = @{}

# 本次 smoke 的唯一識別碼：用於建立測試資料避免與既有資料衝突。
$smokeSuffix = [DateTime]::UtcNow.ToString("yyyyMMddHHmmss")

# 1) Login：驗證管理員登入、取得 access token、設置 refresh cookie
$loginResult = Invoke-ApiRequest `
    -Name "Login" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/login" `
    -ExpectedStatus 200 `
    -Body @{
        username = $resolvedAdminUsername
        password = $resolvedAdminPassword
    } `
    -Session $session

$loginJson = Convert-JsonOrNull $loginResult.BodyText
if ($null -eq $loginJson -or [string]::IsNullOrWhiteSpace($loginJson.token)) {
    Write-Fail "Login 回應缺少 token。"
}

$authorizedHeaders = @{ Authorization = "Bearer $($loginJson.token)" }

$cookieJar = $session.Cookies.GetCookies("$BaseUrl/api/admin")
if ($null -eq $cookieJar[$RefreshCookieName]) {
    Write-Fail "Login 後未取得 $RefreshCookieName cookie。"
}
Write-Pass "Login 後已取得 access token 與 refresh cookie"

# 2) Refresh（成功）：驗證 cookie refresh 鏈路
$refreshOkResult = Invoke-ApiRequest `
    -Name "Refresh with cookie" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/refresh" `
    -ExpectedStatus 200 `
    -Session $session

$refreshJson = Convert-JsonOrNull $refreshOkResult.BodyText
if ($null -eq $refreshJson -or [string]::IsNullOrWhiteSpace($refreshJson.token)) {
    Write-Fail "Refresh 成功回應缺少 token。"
}
Write-Pass "Refresh 成功，cookie 驗證鏈路正常"

# 3) Admin tires list（成功）：驗證後台輪胎管理路徑可用（同時驗證 Gateway 分流到 tire-service）。
$adminTiresResult = Invoke-ApiRequest `
    -Name "Admin tires list with token" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/tires" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Session $session

$adminTiresJson = Convert-JsonOrNull $adminTiresResult.BodyText
if ($null -eq $adminTiresJson -or $null -eq $adminTiresJson.items) {
    Write-Fail "後台輪胎列表回應缺少 items。"
}
Write-Pass "Admin tires list 成功，Gateway -> tire-service 路徑可用"

# 4) Admin tires list（失敗）：驗證後台輪胎管理路徑的授權保護有效。
Invoke-ApiRequest `
    -Name "Admin tires without token (expect 403)" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/tires" `
    -ExpectedStatus 403 `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 5) 準備 Phase 4 Snapshot 驗證用輪胎資料（版本 A/B）
$baselineTireBrand = "SmokeBrand$smokeSuffix"
$baselineTireSeries = "SmokeSeries$smokeSuffix"
$baselineTireOrigin = "TW"
$baselineTireSize = "205/55R16"
$baselineTirePrice = 1234

$updatedTireBrand = "${baselineTireBrand}V2"
$updatedTireSeries = "${baselineTireSeries}V2"
$updatedTireOrigin = "JP"
$updatedTireSize = "225/45R17"
$updatedTirePrice = 1567

# 6) Admin create tire：建立 smoke 專用輪胎（版本 A）
$createTireResult = Invoke-ApiRequest `
    -Name "Admin create tire for snapshot scenario" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/tires" `
    -ExpectedStatus 201 `
    -Headers $authorizedHeaders `
    -Body @{
        brand = $baselineTireBrand
        series = $baselineTireSeries
        origin = $baselineTireOrigin
        size = $baselineTireSize
        price = $baselineTirePrice
        isActive = $true
    } `
    -Session $session

$createTireJson = Convert-JsonOrNull $createTireResult.BodyText
if ($null -eq $createTireJson -or $null -eq $createTireJson.id) {
    Write-Fail "後台新增輪胎回應缺少 id。"
}
$smokeTireId = $createTireJson.id
Write-Pass "Admin create tire 成功，tireId=$smokeTireId"

# 7) Public tires：驗證公開查詢路徑，並確認剛建立輪胎可被查到
$tiresResult = Invoke-ApiRequest `
    -Name "Public tires list" `
    -Method "GET" `
    -Uri "$BaseUrl/api/tires?active=true" `
    -ExpectedStatus 200 `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession)

$tiresJson = Convert-JsonOrNull $tiresResult.BodyText
if ($null -eq $tiresJson -or $null -eq $tiresJson.items) {
    Write-Fail "輪胎列表回應缺少 items。"
}

$smokeTireInPublic = $tiresJson.items | Where-Object { $_.id -eq $smokeTireId } | Select-Object -First 1
if ($null -eq $smokeTireInPublic) {
    Write-Fail "公開輪胎列表未找到剛建立的 tireId=$smokeTireId。"
}
Write-Pass "Public tires list 正常，且可查到 smoke 輪胎"

# 8) Create order #1：使用版本 A 輪胎建單，作為舊快照基準
$createOrderResultV1 = Invoke-ApiRequest `
    -Name "Create order with tire snapshot A" `
    -Method "POST" `
    -Uri "$BaseUrl/api/orders" `
    -ExpectedStatus 201 `
    -Body @{
        tireId = $smokeTireId
        quantity = 1
        customerName = "SmokeSnapshotA"
        phone = "0900000000"
        email = "smoke.snapshot.a.$smokeSuffix@example.com"
        installationOption = "INSTALL"
        deliveryAddress = $null
        carModel = "Smoke Car A"
        notes = "snapshot A"
    } `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession)

$orderJsonV1 = Convert-JsonOrNull $createOrderResultV1.BodyText
if ($null -eq $orderJsonV1 -or $null -eq $orderJsonV1.orderId) {
    Write-Fail "第一筆建單回應缺少 orderId。"
}
$orderIdV1 = $orderJsonV1.orderId
Write-Pass "Create order #1 成功，orderId=$orderIdV1"

# 9) Admin list orders：驗證訂單 #1 快照等於版本 A
$adminOrdersResultV1 = Invoke-ApiRequest `
    -Name "Admin list orders after order #1" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/orders" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Session $session

$adminOrdersJsonV1 = Convert-JsonOrNull $adminOrdersResultV1.BodyText
$orderV1FromAdmin = Get-OrderFromAdminList `
    -AdminOrdersJson $adminOrdersJsonV1 `
    -OrderId $orderIdV1 `
    -ScenarioName "Order #1 查詢"

Assert-OrderSnapshot `
    -OrderItem $orderV1FromAdmin `
    -ExpectedTireId $smokeTireId `
    -ExpectedBrand $baselineTireBrand `
    -ExpectedSeries $baselineTireSeries `
    -ExpectedOrigin $baselineTireOrigin `
    -ExpectedSize $baselineTireSize `
    -ExpectedPrice $baselineTirePrice `
    -ScenarioName "Order #1 快照檢查（建立當下版本 A）"

# 10) Admin update tire：將同一顆輪胎改為版本 B
$updateTireResult = Invoke-ApiRequest `
    -Name "Admin update tire to snapshot B source" `
    -Method "PUT" `
    -Uri "$BaseUrl/api/admin/tires/$smokeTireId" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Body @{
        brand = $updatedTireBrand
        series = $updatedTireSeries
        origin = $updatedTireOrigin
        size = $updatedTireSize
        price = $updatedTirePrice
        isActive = $true
    } `
    -Session $session

$updateTireJson = Convert-JsonOrNull $updateTireResult.BodyText
if ($null -eq $updateTireJson -or [int]$updateTireJson.price -ne $updatedTirePrice) {
    Write-Fail "後台更新輪胎回應不符合預期（price != $updatedTirePrice）。"
}
Write-Pass "Admin update tire 成功，輪胎主資料已切換至版本 B"

# 11) Create order #2：再次用同一顆輪胎建單，應帶入版本 B 快照
$createOrderResultV2 = Invoke-ApiRequest `
    -Name "Create order with tire snapshot B" `
    -Method "POST" `
    -Uri "$BaseUrl/api/orders" `
    -ExpectedStatus 201 `
    -Body @{
        tireId = $smokeTireId
        quantity = 2
        customerName = "SmokeSnapshotB"
        phone = "0911000000"
        email = "smoke.snapshot.b.$smokeSuffix@example.com"
        installationOption = "INSTALL"
        deliveryAddress = $null
        carModel = "Smoke Car B"
        notes = "snapshot B"
    } `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession)

$orderJsonV2 = Convert-JsonOrNull $createOrderResultV2.BodyText
if ($null -eq $orderJsonV2 -or $null -eq $orderJsonV2.orderId) {
    Write-Fail "第二筆建單回應缺少 orderId。"
}
$orderIdV2 = $orderJsonV2.orderId
Write-Pass "Create order #2 成功，orderId=$orderIdV2"

# 12) Admin list orders：同時驗證
#     - 訂單 #1 仍是版本 A（舊快照不變）
#     - 訂單 #2 為版本 B（新訂單吃到新資料）
$adminOrdersResultV2 = Invoke-ApiRequest `
    -Name "Admin list orders after tire update and order #2" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/orders" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Session $session

$adminOrdersJsonV2 = Convert-JsonOrNull $adminOrdersResultV2.BodyText

$orderV1AfterUpdate = Get-OrderFromAdminList `
    -AdminOrdersJson $adminOrdersJsonV2 `
    -OrderId $orderIdV1 `
    -ScenarioName "Order #1 輪胎更新後查詢"

$orderV2AfterUpdate = Get-OrderFromAdminList `
    -AdminOrdersJson $adminOrdersJsonV2 `
    -OrderId $orderIdV2 `
    -ScenarioName "Order #2 輪胎更新後查詢"

Assert-OrderSnapshot `
    -OrderItem $orderV1AfterUpdate `
    -ExpectedTireId $smokeTireId `
    -ExpectedBrand $baselineTireBrand `
    -ExpectedSeries $baselineTireSeries `
    -ExpectedOrigin $baselineTireOrigin `
    -ExpectedSize $baselineTireSize `
    -ExpectedPrice $baselineTirePrice `
    -ScenarioName "Order #1 快照檢查（輪胎更新後仍保持版本 A）"

Assert-OrderSnapshot `
    -OrderItem $orderV2AfterUpdate `
    -ExpectedTireId $smokeTireId `
    -ExpectedBrand $updatedTireBrand `
    -ExpectedSeries $updatedTireSeries `
    -ExpectedOrigin $updatedTireOrigin `
    -ExpectedSize $updatedTireSize `
    -ExpectedPrice $updatedTirePrice `
    -ScenarioName "Order #2 快照檢查（輪胎更新後使用版本 B）"

# 13) Create order（失敗案例）：不存在的輪胎應回 400
$nonExistentTireId = 9223372036854775807
Invoke-ApiRequest `
    -Name "Create order with non-existent tire (expect 400)" `
    -Method "POST" `
    -Uri "$BaseUrl/api/orders" `
    -ExpectedStatus 400 `
    -Body @{
        tireId = $nonExistentTireId
        quantity = 1
        customerName = "SmokeInvalidTire"
        phone = "0922000000"
        email = "smoke.invalid.$smokeSuffix@example.com"
        installationOption = "INSTALL"
        deliveryAddress = $null
        carModel = "Smoke Invalid"
        notes = "expect 400"
    } `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 14) Admin patch tire active=false：準備停用輪胎案例
$patchTireResult = Invoke-ApiRequest `
    -Name "Admin patch tire active false" `
    -Method "PATCH" `
    -Uri "$BaseUrl/api/admin/tires/$smokeTireId/active" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Body @{ isActive = $false } `
    -Session $session

$patchTireJson = Convert-JsonOrNull $patchTireResult.BodyText
if ($null -eq $patchTireJson -or $patchTireJson.isActive -ne $false) {
    Write-Fail "輪胎上下架更新後，回應 isActive 非 false。"
}
Write-Pass "Admin patch tire active=false 成功"

# 15) Create order（失敗案例）：停用輪胎應回 409
Invoke-ApiRequest `
    -Name "Create order with inactive tire (expect 409)" `
    -Method "POST" `
    -Uri "$BaseUrl/api/orders" `
    -ExpectedStatus 409 `
    -Body @{
        tireId = $smokeTireId
        quantity = 1
        customerName = "SmokeInactiveTire"
        phone = "0933000000"
        email = "smoke.inactive.$smokeSuffix@example.com"
        installationOption = "INSTALL"
        deliveryAddress = $null
        carModel = "Smoke Inactive"
        notes = "expect 409"
    } `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 16) Admin patch order status：驗證 admin 授權寫入路徑
$patchOrderResult = Invoke-ApiRequest `
    -Name "Admin update order #2 status" `
    -Method "PATCH" `
    -Uri "$BaseUrl/api/admin/orders/$orderIdV2/status" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Body @{ status = "CONFIRMED" } `
    -Session $session

$patchOrderJson = Convert-JsonOrNull $patchOrderResult.BodyText
if ($null -eq $patchOrderJson -or $patchOrderJson.status -ne "CONFIRMED") {
    Write-Fail "更新訂單狀態後，回應 status 非 CONFIRMED。"
}
Write-Pass "Admin patch order status 正常"

# 17) Logout：驗證登出與 cookie 清除機制
$logoutResult = Invoke-ApiRequest `
    -Name "Logout" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/logout" `
    -ExpectedStatus 200 `
    -Session $session

$setCookieHeader = $logoutResult.Headers["Set-Cookie"]
if ($null -eq $setCookieHeader -or ($setCookieHeader -notmatch "Max-Age=0")) {
    Write-Fail "Logout 回應未看到清除 cookie 的 Set-Cookie(Max-Age=0)。"
}
Write-Pass "Logout 正常，cookie 清除 header 存在"

# 18) Refresh（失敗）：驗證登出後 refresh 應失敗（401）
Invoke-ApiRequest `
    -Name "Refresh after logout (expect 401)" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/refresh" `
    -ExpectedStatus 401 `
    -Session $session | Out-Null

# 19) Admin endpoint 無 token（失敗）：驗證授權保護有效
Invoke-ApiRequest `
    -Name "Admin orders without token (expect 403)" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/orders" `
    -ExpectedStatus 403 `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 20) Refresh 無 cookie（失敗）：驗證 cookie 保護有效
Invoke-ApiRequest `
    -Name "Refresh without cookie (expect 401)" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/refresh" `
    -ExpectedStatus 401 `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 全部測試完成：輸出最終結論（含 Phase 4 Snapshot 驗證）
Write-Host ""
Write-Host "Smoke Integration 測試完成：全部通過（含 Phase 4 Snapshot 驗證）" -ForegroundColor Green

