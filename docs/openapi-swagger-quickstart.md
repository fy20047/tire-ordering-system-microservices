# OpenAPI / Swagger Quickstart

This project now includes OpenAPI UI (`springdoc-openapi`) in:

- `api-gateway`
- `auth-service`
- `tire-service`
- `order-service`

## 1) Docker Compose (quickest)

Run with the OpenAPI override so each microservice is reachable from your browser:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml -f infra/docker-compose.openapi.yml up -d --build
```

Open these URLs:

- Gateway Swagger: `http://localhost:8080/swagger-ui/index.html`
- Auth Swagger: `http://localhost:18081/swagger-ui/index.html`
- Tire Swagger: `http://localhost:18082/swagger-ui/index.html`
- Order Swagger: `http://localhost:18083/swagger-ui/index.html`

Raw JSON docs:

- `http://localhost:8080/v3/api-docs`
- `http://localhost:18081/v3/api-docs`
- `http://localhost:18082/v3/api-docs`
- `http://localhost:18083/v3/api-docs`

## 2) Kubernetes (Minikube / ArgoCD)

Use port-forward for each service:

```powershell
kubectl -n tire-ordering port-forward svc/api-gateway 8080:8080
kubectl -n tire-ordering port-forward svc/auth-service 18081:8080
kubectl -n tire-ordering port-forward svc/tire-service 18082:8080
kubectl -n tire-ordering port-forward svc/order-service 18083:8080
```

Then open the same Swagger URLs listed above.

## Notes

- `tire-service` and `order-service` already enforce admin role on `/api/admin/**`.
- Swagger endpoints are explicitly allowlisted in each service security config:
  - `/v3/api-docs/**`
  - `/swagger-ui/**`
  - `/swagger-ui.html`
