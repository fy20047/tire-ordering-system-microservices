<#
用途說明：
這支腳本用來執行「Gateway 入口切換後」的 Smoke Integration 測試。
目標是快速確認核心鏈路仍正常：Ingress/Proxy/Auth/Cookie/Public/Admin/Write path 都可用。
並覆蓋 Phase 3 的 Tire Service 分流後關鍵路徑（公開輪胎查詢 + 後台輪胎管理）。

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
        Uri        = $Uri
        Method     = $Method
        TimeoutSec = $TimeoutSec
        WebSession = $Session
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
        BodyText = $responseText
        Headers = $responseHeaders
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

# 5) Admin create tire：驗證後台輪胎新增流程。
$createTireResult = Invoke-ApiRequest `
    -Name "Admin create tire" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/tires" `
    -ExpectedStatus 201 `
    -Headers $authorizedHeaders `
    -Body @{
        brand = "SmokeBrand$smokeSuffix"
        series = "SmokeSeries$smokeSuffix"
        origin = "TW"
        size = "205/55R16"
        price = 1234
        isActive = $true
    } `
    -Session $session

$createTireJson = Convert-JsonOrNull $createTireResult.BodyText
if ($null -eq $createTireJson -or $null -eq $createTireJson.id) {
    Write-Fail "後台新增輪胎回應缺少 id。"
}
$smokeTireId = $createTireJson.id
Write-Pass "Admin create tire 成功，tireId=$smokeTireId"

# 6) Admin update tire：驗證後台輪胎更新流程。
$updateTireResult = Invoke-ApiRequest `
    -Name "Admin update tire" `
    -Method "PUT" `
    -Uri "$BaseUrl/api/admin/tires/$smokeTireId" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Body @{
        brand = "SmokeBrand$smokeSuffix"
        series = "SmokeSeries${smokeSuffix}Upd"
        origin = "JP"
        size = "205/55R16"
        price = 1567
        isActive = $true
    } `
    -Session $session

$updateTireJson = Convert-JsonOrNull $updateTireResult.BodyText
if ($null -eq $updateTireJson -or $updateTireJson.price -ne 1567) {
    Write-Fail "後台更新輪胎回應不符合預期（price != 1567）。"
}
Write-Pass "Admin update tire 成功"

# 7) Admin patch tire active：驗證後台上下架切換流程。
$patchTireResult = Invoke-ApiRequest `
    -Name "Admin patch tire active" `
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
Write-Pass "Admin patch tire active 成功"

# 8) Public Tires：驗證公開讀取路徑（無需 token）
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

if ($tiresJson.items.Count -lt 1) {
    Write-Fail "無可用輪胎資料，無法繼續建單 smoke。請先確認測試資料。"
}

$tireId = $tiresJson.items[0].id
Write-Pass "Public tires list 正常，將使用 tireId=$tireId 建單"

# 9) Create Order：驗證公開寫入路徑（寫入資料庫）
$createOrderResult = Invoke-ApiRequest `
    -Name "Create order" `
    -Method "POST" `
    -Uri "$BaseUrl/api/orders" `
    -ExpectedStatus 201 `
    -Body @{
        tireId = $tireId
        quantity = 1
        customerName = "SmokeTestUser"
        phone = "0900000000"
        email = "smoke+$smokeSuffix@example.com"
        installationOption = "INSTALL"
        deliveryAddress = $null
        carModel = "Smoke Car"
        notes = "smoke test"
    } `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession)

$orderJson = Convert-JsonOrNull $createOrderResult.BodyText
if ($null -eq $orderJson -or $null -eq $orderJson.orderId) {
    Write-Fail "建單回應缺少 orderId。"
}

$orderId = $orderJson.orderId
Write-Pass "Create order 正常，orderId=$orderId"

# 10) Admin list orders：驗證 admin 授權讀取路徑
$adminOrdersResult = Invoke-ApiRequest `
    -Name "Admin list orders" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/orders" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Session $session

$adminOrdersJson = Convert-JsonOrNull $adminOrdersResult.BodyText
if ($null -eq $adminOrdersJson -or $null -eq $adminOrdersJson.items) {
    Write-Fail "後台訂單列表回應缺少 items。"
}

$createdOrderInList = $adminOrdersJson.items | Where-Object { $_.id -eq $orderId } | Select-Object -First 1
if ($null -eq $createdOrderInList) {
    Write-Fail "後台列表未找到剛建立的 orderId=$orderId。"
}
Write-Pass "Admin list orders 可讀取剛建立的訂單"

# 11) Admin patch order status：驗證 admin 授權寫入路徑
$patchResult = Invoke-ApiRequest `
    -Name "Admin update order status" `
    -Method "PATCH" `
    -Uri "$BaseUrl/api/admin/orders/$orderId/status" `
    -ExpectedStatus 200 `
    -Headers $authorizedHeaders `
    -Body @{ status = "CONFIRMED" } `
    -Session $session

$patchJson = Convert-JsonOrNull $patchResult.BodyText
if ($null -eq $patchJson -or $patchJson.status -ne "CONFIRMED") {
    Write-Fail "更新訂單狀態後，回應 status 非 CONFIRMED。"
}
Write-Pass "Admin patch order status 正常"

# 12) Logout：驗證登出與 cookie 清除機制
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

# 13) Refresh（失敗）：驗證登出後 refresh 應失敗（401）
Invoke-ApiRequest `
    -Name "Refresh after logout (expect 401)" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/refresh" `
    -ExpectedStatus 401 `
    -Session $session | Out-Null

# 14) Admin endpoint 無 token（失敗）：驗證授權保護有效
Invoke-ApiRequest `
    -Name "Admin orders without token (expect 403)" `
    -Method "GET" `
    -Uri "$BaseUrl/api/admin/orders" `
    -ExpectedStatus 403 `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 15) Refresh 無 cookie（失敗）：驗證 cookie 保護有效
Invoke-ApiRequest `
    -Name "Refresh without cookie (expect 401)" `
    -Method "POST" `
    -Uri "$BaseUrl/api/admin/refresh" `
    -ExpectedStatus 401 `
    -Session (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null

# 全部測試完成：輸出最終結論
Write-Host ""
Write-Host "Smoke Integration 測試完成：全部通過" -ForegroundColor Green

