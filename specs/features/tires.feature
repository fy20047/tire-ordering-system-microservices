Feature: 輪胎商品查詢與後台管理

  # Service ownership:
  # - tire-service owns: tire
  # - tire-service is responsible for tire listing, tire detail, tire creation,
  #   tire update, and tire active/inactive status change
  # - public and admin tire APIs are routed through API Gateway to tire-service

  Background:
    Given tire-service 已啟動
    And API Gateway 已正確轉發 "/api/tires/**" 與 "/api/admin/tires/**" 至 tire-service

  Scenario: 前台未指定 active 參數時，只回傳上架輪胎
    Given 系統中存在多筆輪胎資料
    And 其中包含上架輪胎與下架輪胎
    When Client 呼叫 GET "/api/tires"
    Then 系統應回傳 200 OK
    And Response body.items 只應包含上架輪胎

  Scenario: 前台指定 active=false 時，回傳全部輪胎
    Given 系統中存在多筆輪胎資料
    And 其中包含上架輪胎與下架輪胎
    When Client 呼叫 GET "/api/tires?active=false"
    Then 系統應回傳 200 OK
    And Response body.items 應包含所有輪胎資料

  Scenario: 前台查詢有效輪胎 id 成功
    Given 系統中存在 id 為 "<existing_tire_id>" 的輪胎
    When Client 呼叫 GET "/api/tires/<existing_tire_id>"
    Then 系統應回傳 200 OK
    And Response body.id 應為 "<existing_tire_id>"
    And Response body 應包含 "brand"
    And Response body 應包含 "series"
    And Response body 應包含 "size"
    And Response body 應包含 "price"

  Scenario: 前台查詢不存在的輪胎 id
    Given 系統中不存在 id 為 "<non_existing_tire_id>" 的輪胎
    When Client 呼叫 GET "/api/tires/<non_existing_tire_id>"
    Then 系統應回傳 400 Bad Request
    And Response body.message 應為 "Tire not found"

  Scenario: 管理員查詢輪胎列表成功
    Given Client 已持有有效的管理員 access token
    And 系統中存在多筆輪胎資料
    When Client 呼叫 GET "/api/admin/tires"
    And Request header "Authorization" 為有效 Bearer token
    Then 系統應回傳 200 OK
    And Response body.items 應包含輪胎清單

  Scenario: 管理員依條件篩選輪胎
    Given Client 已持有有效的管理員 access token
    And 系統中存在不同品牌、尺寸與上下架狀態的輪胎
    When Client 呼叫 GET "/api/admin/tires?brand=<brand>&size=<size>&active=<active>"
    And Request header "Authorization" 為有效 Bearer token
    Then 系統應回傳 200 OK
    And Response body.items 中每筆資料都應符合篩選條件

  Scenario: 管理員新增輪胎成功
    Given Client 已持有有效的管理員 access token
    When Client 呼叫 POST "/api/admin/tires"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "brand": "<brand>",
        "series": "<series>",
        "origin": "<origin>",
        "size": "<size>",
        "price": <price>,
        "isActive": true
      }
      """
    Then 系統應回傳 201 Created
    And Response body.brand 應為 "<brand>"
    And Response body.series 應為 "<series>"
    And Response body.size 應為 "<size>"
    And Response body.price 應為 <price>
    And Response body.isActive 應為 true

  Scenario: 管理員新增輪胎時缺少必要欄位
    Given Client 已持有有效的管理員 access token
    When Client 呼叫 POST "/api/admin/tires"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "brand": "",
        "series": "<series>",
        "origin": "<origin>",
        "size": "<size>",
        "price": <price>,
        "isActive": true
      }
      """
    Then 系統應回傳 400 Bad Request
    And Response body.message 應指出請求資料驗證失敗

  Scenario: 管理員完整更新輪胎資料成功
    Given Client 已持有有效的管理員 access token
    And 系統中存在 id 為 "<existing_tire_id>" 的輪胎
    When Client 呼叫 PUT "/api/admin/tires/<existing_tire_id>"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "brand": "<updated_brand>",
        "series": "<updated_series>",
        "origin": "<updated_origin>",
        "size": "<updated_size>",
        "price": <updated_price>,
        "isActive": true
      }
      """
    Then 系統應回傳 200 OK
    And Response body.id 應為 "<existing_tire_id>"
    And Response body.brand 應為 "<updated_brand>"
    And Response body.series 應為 "<updated_series>"
    And Response body.size 應為 "<updated_size>"
    And Response body.price 應為 <updated_price>

  Scenario: 管理員更新不存在的輪胎
    Given Client 已持有有效的管理員 access token
    And 系統中不存在 id 為 "<non_existing_tire_id>" 的輪胎
    When Client 呼叫 PUT "/api/admin/tires/<non_existing_tire_id>"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為合法且完整的輪胎資料
    Then 系統應回傳 400 Bad Request
    And Response body.message 應為 "Tire not found"

  Scenario: 管理員將輪胎設為下架
    Given Client 已持有有效的管理員 access token
    And 系統中存在 id 為 "<existing_tire_id>" 的上架輪胎
    When Client 呼叫 PATCH "/api/admin/tires/<existing_tire_id>/active"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "isActive": false
      }
      """
    Then 系統應回傳 200 OK
    And Response body.id 應為 "<existing_tire_id>"
    And Response body.isActive 應為 false

  Scenario: 管理員更新不存在輪胎的上下架狀態
    Given Client 已持有有效的管理員 access token
    And 系統中不存在 id 為 "<non_existing_tire_id>" 的輪胎
    When Client 呼叫 PATCH "/api/admin/tires/<non_existing_tire_id>/active"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "isActive": false
      }
      """
    Then 系統應回傳 400 Bad Request
    And Response body.message 應為 "Tire not found"