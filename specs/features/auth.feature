Feature: 管理員驗證與憑證管理

  # Service ownership:
  # - auth-service owns: admin, refresh_token
  # - auth-service is responsible for login, refresh, and logout
  # - admin credentials used in tests should come from test fixtures or test environment variables
  # - do not hardcode production credentials in this file

  Background:
    Given auth-service 已啟動
    And API Gateway 已正確轉發 "/api/admin/**" 至 auth-service

  Scenario: 使用有效的管理員帳號密碼登入成功
    Given 系統中存在一組有效的管理員登入憑證
    When Client 呼叫 POST "/api/admin/login"
    And Request body 為
      """
      {
        "username": "<valid_admin_username>",
        "password": "<valid_admin_password>"
      }
      """
    Then 系統應回傳 200 OK
    And Response body 應包含 "token"
    And Response body 應包含 "expiresInSeconds"
    And Response header 應設定 refresh token 的 HttpOnly Cookie

  Scenario: 使用錯誤密碼登入失敗
    Given 系統中存在一組有效的管理員登入憑證
    When Client 呼叫 POST "/api/admin/login"
    And Request body 為
      """
      {
        "username": "<valid_admin_username>",
        "password": "<invalid_password>"
      }
      """
    Then 系統應回傳 400 Bad Request
    And Response body.message 應為 "Invalid username or password"

  Scenario: 使用不存在的帳號登入失敗
    Given 系統中不存在帳號 "<unknown_admin_username>"
    When Client 呼叫 POST "/api/admin/login"
    And Request body 為
      """
      {
        "username": "<unknown_admin_username>",
        "password": "<any_password>"
      }
      """
    Then 系統應回傳 400 Bad Request
    And Response body.message 應為 "Invalid username or password"

  Scenario: 使用有效 refresh token 成功換發新 access token
    Given Client 已持有有效的 refresh token cookie
    When Client 呼叫 POST "/api/admin/refresh"
    Then 系統應回傳 200 OK
    And Response body 應包含新的 "token"
    And Response body 應包含 "expiresInSeconds"
    And Response header 應重新設定新的 refresh token cookie

  Scenario: 未攜帶 refresh token cookie 時無法刷新
    Given Client 未攜帶 refresh token cookie
    When Client 呼叫 POST "/api/admin/refresh"
    Then 系統應回傳 401 Unauthorized

  Scenario: 使用有效 refresh token 登出成功
    Given Client 已持有有效的 refresh token cookie
    When Client 呼叫 POST "/api/admin/logout"
    Then 系統應回傳 200 OK
    And 系統應撤銷該 refresh token
    And Response header 應清除 refresh token cookie

  Scenario: 未攜帶 refresh token 仍可呼叫登出
    Given Client 未攜帶 refresh token cookie
    When Client 呼叫 POST "/api/admin/logout"
    Then 系統應回傳 200 OK
    And Response header 應清除 refresh token cookie