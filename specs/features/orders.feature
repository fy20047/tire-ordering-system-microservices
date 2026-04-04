Feature: 訂單建立與後台訂單管理

  # Service ownership:
  # - order-service owns: orders, order_items, tire_snapshot
  # - order-service must not directly query tire-service tables
  # - when creating an order, order-service should retrieve tire information
  #   through tire-service API and persist a tire snapshot in its own database
  # - admin order APIs are routed through API Gateway to order-service

  Background:
    Given order-service 已啟動
    And tire-service 已啟動
    And API Gateway 已正確轉發 "/api/orders/**" 與 "/api/admin/orders/**" 至 order-service

  Scenario: 使用有效資料建立訂單成功
    Given 系統中存在 id 為 "<available_tire_id>" 的上架輪胎
    And tire-service 可正確回應該輪胎資料
    When Client 呼叫 POST "/api/orders"
    And Request body 為
      """
      {
        "tireId": "<available_tire_id>",
        "quantity": 4,
        "customerName": "<customer_name>",
        "phone": "<customer_phone>",
        "email": "<customer_email>",
        "installationOption": "INSTALL",
        "deliveryAddress": null,
        "carModel": "<car_model>",
        "notes": "<notes>"
      }
      """
    Then 系統應回傳 201 Created
    And Response body 應包含 "orderId"
    And Response body.status 應為 "PENDING"
    And order-service 應建立新的訂單資料

  Scenario: 建立訂單時選擇配送但未填寫地址
    Given 系統中存在 id 為 "<available_tire_id>" 的上架輪胎
    When Client 呼叫 POST "/api/orders"
    And Request body 為
      """
      {
        "tireId": "<available_tire_id>",
        "quantity": 4,
        "customerName": "<customer_name>",
        "phone": "<customer_phone>",
        "email": "<customer_email>",
        "installationOption": "DELIVERY",
        "deliveryAddress": "",
        "carModel": "<car_model>",
        "notes": ""
      }
      """
    Then 系統應回傳 400 Bad Request
    And Response body.message 應指出 "deliveryAddress" 為必填

  Scenario: 建立訂單時輪胎不存在
    Given 系統中不存在 id 為 "<non_existing_tire_id>" 的輪胎
    When Client 呼叫 POST "/api/orders"
    And Request body.tireId 為 "<non_existing_tire_id>"
    Then order-service 應透過 tire-service API 驗證輪胎失敗
    And 系統應回傳 400 Bad Request

  Scenario: 嘗試購買已下架輪胎
    Given 系統中存在 id 為 "<inactive_tire_id>" 的輪胎
    And 該輪胎為下架狀態
    When Client 呼叫 POST "/api/orders"
    And Request body 為
      """
      {
        "tireId": "<inactive_tire_id>",
        "quantity": 4,
        "customerName": "<customer_name>",
        "phone": "<customer_phone>",
        "email": "<customer_email>",
        "installationOption": "INSTALL",
        "deliveryAddress": null,
        "carModel": "<car_model>",
        "notes": ""
      }
      """
    Then order-service 應透過 tire-service API 取得輪胎狀態
    And 系統應回傳 409 Conflict
    And Response body.message 應為 "Tire is not available"

  Scenario: 建立訂單時保存輪胎快照
    Given 系統中存在 id 為 "<available_tire_id>" 的上架輪胎
    And tire-service 可提供該輪胎的 brand、series、size 與 price
    When Client 呼叫 POST "/api/orders" 並建立訂單成功
    Then order-service 應在自己的資料庫中保存 tire snapshot
    And tire snapshot 應包含 "tireId"
    And tire snapshot 應包含 "brand"
    And tire snapshot 應包含 "series"
    And tire snapshot 應包含 "size"
    And tire snapshot 應包含 "unitPrice"

  Scenario: order-service 不得直接查詢 tire-service 的資料表
    Given order-service 與 tire-service 各自擁有獨立的資料邊界
    When order-service 處理建立訂單請求
    Then 輪胎資料驗證必須透過 tire-service API 完成
    And 不得直接查詢 tire-service 的資料表

  Scenario: 管理員查詢全部訂單
    Given Client 已持有有效的管理員 access token
    And 系統中存在多筆訂單
    When Client 呼叫 GET "/api/admin/orders"
    And Request header "Authorization" 為有效 Bearer token
    Then 系統應回傳 200 OK
    And Response body.items 應包含訂單清單

  Scenario: 管理員依狀態篩選訂單
    Given Client 已持有有效的管理員 access token
    And 系統中存在不同狀態的訂單
    When Client 呼叫 GET "/api/admin/orders?status=<order_status>"
    And Request header "Authorization" 為有效 Bearer token
    Then 系統應回傳 200 OK
    And Response body.items 中每筆訂單的 status 都應為 "<order_status>"

  Scenario: 管理員將訂單狀態更新為指定值
    Given Client 已持有有效的管理員 access token
    And 系統中存在 id 為 "<existing_order_id>" 的訂單
    When Client 呼叫 PATCH "/api/admin/orders/<existing_order_id>/status"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "status": "<new_order_status>"
      }
      """
    Then 系統應回傳 200 OK
    And Response body.id 應為 "<existing_order_id>"
    And Response body.status 應為 "<new_order_status>"

  Scenario: 管理員更新不存在的訂單
    Given Client 已持有有效的管理員 access token
    And 系統中不存在 id 為 "<non_existing_order_id>" 的訂單
    When Client 呼叫 PATCH "/api/admin/orders/<non_existing_order_id>/status"
    And Request header "Authorization" 為有效 Bearer token
    And Request body 為
      """
      {
        "status": "<new_order_status>"
      }
      """
    Then 系統應回傳 400 Bad Request
    And Response body.message 應為 "Order not found"