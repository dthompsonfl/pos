# EnterprisePOS API Guide

## Overview

EnterprisePOS communicates with backend services via RESTful APIs. All endpoints use HTTPS and require authentication via Bearer tokens.

## Authentication

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "storeId": "store-123",
  "employeeId": "emp-456",
  "pin": "1234"
}
```

### Response

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresAt": 1699999999,
  "employee": {
    "id": "emp-456",
    "name": "Alice",
    "role": "MANAGER"
  }
}
```

### Refresh Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

## Orders

### Create Order

```http
POST /api/v1/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "storeId": "store-123",
  "registerId": "reg-789",
  "diningMode": "RETAIL",
  "items": [
    {
      "productId": "prod-1",
      "quantity": 2,
      "modifiers": []
    }
  ]
}
```

### Get Order

```http
GET /api/v1/orders/{orderId}
Authorization: Bearer <token>
```

### Update Order Status

```http
PATCH /api/v1/orders/{orderId}/status
Authorization: Bearer <token>
Content-Type: application/json

{
  "status": "AWAITING_PAYMENT"
}
```

### List Orders

```http
GET /api/v1/orders?storeId=store-123&status=OPEN&limit=50
Authorization: Bearer <token>
```

## Payments

### Process Payment

```http
POST /api/v1/payments
Authorization: Bearer <token>
Content-Type: application/json

{
  "orderId": "order-1",
  "amount": "12.50",
  "currency": "USD",
  "provider": "STRIPE_TERMINAL",
  "method": "CARD_PRESENT"
}
```

### Refund Payment

```http
POST /api/v1/payments/{paymentId}/refund
Authorization: Bearer <token>
Content-Type: application/json

{
  "amount": "12.50",
  "reason": "Customer request"
}
```

## Catalog

### Get Products

```http
GET /api/v1/catalog/products?storeId=store-123&categoryId=cat-1
Authorization: Bearer <token>
```

### Update Product

```http
PUT /api/v1/catalog/products/{productId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Updated Burger",
  "price": "14.00",
  "isAvailable": true
}
```

## Employees

### Get Employees

```http
GET /api/v1/employees?storeId=store-123&active=true
Authorization: Bearer <token>
```

### Create Employee

```http
POST /api/v1/employees
Authorization: Bearer <token>
Content-Type: application/json

{
  "storeId": "store-123",
  "name": "Bob",
  "role": "CASHIER",
  "pin": "5678"
}
```

## Sync

### Upload Pending Changes

```http
POST /api/v1/sync/outbox
Authorization: Bearer <token>
Content-Type: application/json

{
  "entries": [
    {
      "id": "sync-1",
      "operation": "ORDER_CREATE",
      "payload": { ... }
    }
  ]
}
```

### Download Server Changes

```http
GET /api/v1/sync/changes?since=1699999999&storeId=store-123
Authorization: Bearer <token>
```

## Error Handling

All errors follow this structure:

```json
{
  "error": {
    "code": "INVALID_ORDER_STATUS",
    "message": "Cannot transition from CLOSED to OPEN",
    "details": {
      "orderId": "order-1",
      "currentStatus": "CLOSED"
    }
  }
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| UNAUTHORIZED | 401 | Invalid or expired token |
| FORBIDDEN | 403 | Insufficient permissions |
| NOT_FOUND | 404 | Resource does not exist |
| CONFLICT | 409 | Concurrent modification detected |
| RATE_LIMITED | 429 | Too many requests |
| SERVER_ERROR | 500 | Internal server error |

## WebSocket Events

Real-time events are delivered via WebSocket at `wss://api.enterprisepos.com/ws`:

### Connect

```javascript
const ws = new WebSocket('wss://api.enterprisepos.com/ws?token=Bearer+...');
```

### Events

```json
{
  "type": "ORDER_UPDATED",
  "payload": {
    "orderId": "order-1",
    "status": "PAID"
  }
}
```

### Event Types

- `ORDER_CREATED`: New order opened
- `ORDER_UPDATED`: Order status or items changed
- `PAYMENT_RECEIVED`: Payment successfully processed
- `INVENTORY_LOW`: Stock below threshold
- `EMPLOYEE_LOGIN`: Employee authenticated

## Rate Limits

- **Authenticated**: 1000 requests per minute
- **Sync**: 100 requests per minute
- **WebSocket**: 100 messages per minute

Exceeding limits returns `429 Too Many Requests` with `Retry-After` header.

## Pagination

List endpoints use cursor-based pagination:

```http
GET /api/v1/orders?cursor=eyJpZCI6Im9yZGVyLTEwMCJ9&limit=50
```

Response includes:

```json
{
  "data": [...],
  "nextCursor": "eyJpZCI6Im9yZGVyLTE1MCJ9",
  "hasMore": true
}
```
