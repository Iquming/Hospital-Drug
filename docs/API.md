# REST 接口说明

## 1. 基础信息

- 默认服务地址：`http://127.0.0.1:8081`
- 默认请求类型：`application/json`
- CSV 接口响应类型：`text/csv; charset=UTF-8`
- 时间字段格式：`yyyy-MM-dd HH:mm:ss`；HIS 入站时间使用 ISO-8601，例如 `2026-09-02T09:30:00`

本文档描述当前代码中的接口契约。真实 HIS 对接时，应由双方根据厂商规范固定字段映射、签名规则、错误码和版本策略。

## 2. 认证与权限

### 2.1 用户接口

登录成功后，在后续请求中携带：

```http
Authorization: Bearer <token>
```

角色说明：

| 角色 | 权限范围 |
| --- | --- |
| `ADMIN` | 全部模块、HIS 模拟、审计和用户管理 |
| `PHARMACIST` | 处方调剂、特殊药品复核、药库、档案、盘点和报表 |
| `NURSE` | 申请单和业务状态只读，以及受限扫描核验 |

### 2.2 HIS 机器认证

所有 `/api/integration/his/v1/**` 请求都必须携带：

```http
X-HIS-Key: <HIS_API_KEY>
```

当 `HIS_MODE=rest` 时，还必须携带：

```http
X-HIS-Timestamp: <Unix 秒时间戳>
X-HIS-Nonce: <本次请求唯一随机数>
X-HIS-Signature: <小写十六进制 HMAC-SHA256>
```

签名原文：

```text
timestamp + "\n" + nonce + "\n" + requestTarget + "\n" + SHA256(rawJsonBody)
```

签名密钥为 `HIS_API_KEY`。`requestTarget` 是原始路径；存在查询参数时必须包含原始查询字符串。服务默认允许约 300 秒时钟偏差，并记录随机数以阻止重放。

## 3. 通用错误

业务错误通常返回：

```json
{
  "code": "INVALID_STATE_TRANSITION",
  "message": "当前申请单状态不允许发药",
  "requestId": "DISPENSE-20260902-001"
}
```

常见 HTTP 状态：

| 状态 | 含义 |
| --- | --- |
| `200` | 请求成功 |
| `400` | 参数错误、追溯码重复、处方或药品不匹配 |
| `401` | 未登录、令牌失效或 HIS 认证失败 |
| `403` | 已登录但角色权限不足 |
| `409` | 库存冲突、状态冲突、幂等冲突、映射缺失或 HIS 修订冲突 |

部分兼容接口仍返回纯文本成功或失败信息，调用方应同时处理 JSON 错误和文本响应。

## 4. 接口总表

### 4.1 登录与会话

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | 公开 | 用户名和密码登录 |
| `GET` | `/auth/me` | 已登录 | 获取当前用户 |
| `POST` | `/auth/logout` | 已登录 | 撤销当前令牌 |

### 4.2 药品档案

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/catalog` | 管理员、药师 | 获取药品档案 |
| `POST` | `/catalog` | 管理员、药师 | 新增药品档案 |
| `PUT` | `/catalog/{id}` | 管理员、药师 | 更新档案及特殊管理属性 |
| `DELETE` | `/catalog/{id}` | 管理员、药师 | 停用档案，不物理删除 |

### 4.3 库存、追溯与兼容处方

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/list` | 已登录 | 查询库存单品 |
| `POST` | `/add` | 管理员、药师 | 按追溯码入库，支持 `requestId` 幂等 |
| `GET` | `/search?code=` | 已登录 | 按追溯码搜索库存 |
| `GET` | `/records` | 已登录 | 查询全部出入库流水 |
| `GET` | `/records/recent?limit=40` | 已登录 | 查询最近流水，最大 200 条 |
| `GET` | `/nearExpiry?days=90` | 已登录 | 查询近效期库存 |
| `GET` | `/dashboard/summary` | 已登录 | 运营总览聚合数据 |
| `GET` | `/stock/status` | 已登录 | 库存状态统计 |
| `GET` | `/alerts/enhanced` | 已登录 | 低库存、近效期及风险预警 |
| `GET` | `/recommend/fifo?drugName=&limit=10` | 已登录 | 按效期优先推荐库存 |
| `GET` | `/prescriptions?patientId=&status=PENDING` | 管理员、药师、护士 | 旧处方查询兼容入口 |
| `POST` | `/dispense` | 管理员、药师 | 旧处方发药兼容入口 |
| `POST` | `/return` | 管理员、药师 | 旧处方退药兼容入口 |
| `GET` | `/health/db` | 公开 | 数据库连接和库存行数健康检查 |

### 4.4 拆零和扫描核验

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/split/create` | 管理员、药师 | 从母包装生成拆零子码 |
| `GET` | `/split/{childCode}/label` | 管理员、药师 | 获取子码标签信息 |
| `POST` | `/device/scan/verify` | 已登录 | 核验追溯码、场景和预期药品；护士仅限发药/退药场景 |

### 4.5 库存盘点

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/inventory?limit=20` | 管理员、药师 | 查询盘点任务 |
| `POST` | `/inventory` | 管理员、药师 | 创建盘点 |
| `POST` | `/inventory/{id}/scan` | 管理员、药师 | 扫描盘点追溯码 |
| `GET` | `/inventory/{id}/items` | 管理员、药师 | 查询盘点明细和差异 |
| `POST` | `/inventory/{id}/complete` | 管理员、药师 | 完成盘点 |

### 4.6 HIS 入站

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/api/integration/his/v1/drug-applications` | HIS 机器认证 | 推送或修订药品申请单 |
| `POST` | `/api/integration/his/v1/drug-applications/{applicationNo}/cancel` | HIS 机器认证 | 撤销申请单 |

### 4.7 药房申请单工作台

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/pharmacy/applications` | 已登录 | 按状态、关键词和优先级查询申请队列 |
| `GET` | `/api/pharmacy/applications/{id}` | 已登录 | 查询申请单及药品明细 |
| `GET` | `/api/pharmacy/his-drug-mappings` | 已登录 | 查询 HIS 药品编码映射 |
| `POST` | `/api/pharmacy/his-drug-mappings` | 管理员、药师 | 保存药品编码映射 |
| `POST` | `/api/pharmacy/applications/{id}/review` | 管理员、药师 | 特殊管理药品人工复核 |
| `POST` | `/api/pharmacy/application-items/{itemId}/dispense` | 管理员、药师 | 扫码发放申请明细 |
| `POST` | `/api/pharmacy/application-items/{itemId}/return` | 管理员、药师 | 扫描原追溯码退药 |

### 4.8 HIS 联调和回传

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/his/callbacks?limit=100` | 管理员、药师 | 查询回传事件，最大 300 条 |
| `POST` | `/api/his/callbacks/{eventId}/retry` | 管理员、药师 | 对最终失败事件人工补发 |
| `GET` | `/api/his/integration/status` | 管理员、药师 | 查询 HIS 模式和回传配置摘要 |
| `POST` | `/api/admin/his-simulator/applications` | 管理员 | 生成本地模拟 HIS 申请 |

### 4.9 用户、审计与报表

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/users` | 管理员 | 查询用户 |
| `POST` | `/users` | 管理员 | 创建用户 |
| `PUT` | `/users/{id}` | 管理员 | 更新用户角色、科室和状态 |
| `PUT` | `/users/{id}/password` | 管理员 | 重置密码 |
| `DELETE` | `/users/{id}` | 管理员 | 禁用用户 |
| `DELETE` | `/users/{id}/hard` | 管理员 | 删除非当前用户 |
| `GET` | `/audit/recent?limit=50` | 管理员 | 查询最近审计日志 |
| `GET` | `/reports/dispense.csv` | 管理员、药师 | 导出发药流水 |
| `GET` | `/reports/audit.csv` | 管理员、药师 | 导出审计日志 |
| `GET` | `/reports/inventory/{id}.csv` | 管理员、药师 | 导出指定盘点 |

## 5. 核心接口示例

### 5.1 用户登录

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "<password>"
}
```

成功响应：

```json
{
  "token": "<bearer-token>",
  "user": {
    "id": 1,
    "username": "admin",
    "displayName": "系统管理员",
    "role": "ADMIN",
    "department": "信息科"
  }
}
```

### 5.2 HIS 推送申请单

```http
POST /api/integration/his/v1/drug-applications
X-HIS-Key: <HIS_API_KEY>
Content-Type: application/json
```

```json
{
  "eventId": "HIS-EVENT-20260902-0001",
  "sourceSystem": "HIS",
  "applicationNo": "OP-20260902-0001",
  "revision": 1,
  "patientId": "P-DEMO-001",
  "patientName": "测试患者",
  "patientGender": "女",
  "patientAge": 35,
  "encounterNo": "ENC-20260902-001",
  "departmentCode": "DEPT-OP",
  "departmentName": "门诊内科",
  "priority": "NORMAL",
  "prescribedAt": "2026-09-02T09:30:00",
  "prescriberId": "D-DEMO-001",
  "prescriberName": "测试医师",
  "diagnosis": "上呼吸道感染",
  "allergyInfo": "未发现药物过敏史",
  "items": [
    {
      "itemNo": "ITEM-1",
      "hisDrugCode": "HIS-DRUG-001",
      "drugName": "阿莫西林胶囊",
      "specification": "0.25g*24粒",
      "quantity": 1,
      "unit": "盒",
      "dosage": "0.5g",
      "frequency": "每日3次",
      "administrationRoute": "口服",
      "usageInstruction": "饭后服用"
    }
  ]
}
```

成功响应：

```json
{
  "eventId": "HIS-EVENT-20260902-0001",
  "localApplicationId": 101,
  "applicationNo": "OP-20260902-0001",
  "status": "MAPPING_REQUIRED",
  "duplicate": false,
  "warnings": [
    "HIS-DRUG-001 阿莫西林胶囊 尚未映射本地药品档案"
  ]
}
```

同一 `eventId` 重复推送时返回首次结果，并将 `duplicate` 置为 `true`，不会重复生成申请。相同申请单号只接受更高 `revision`；发药开始后拒绝覆盖修订。

### 5.3 HIS 撤销申请单

```http
POST /api/integration/his/v1/drug-applications/OP-20260902-0001/cancel?sourceSystem=HIS
X-HIS-Key: <HIS_API_KEY>
Content-Type: application/json
```

```json
{
  "eventId": "HIS-CANCEL-20260902-0001",
  "revision": 1,
  "reason": "HIS处方作废"
}
```

未发药申请返回 `CANCELLED`；已经发药的申请返回 `RETURN_REQUIRED`，药房完成原路退药后转为 `RETURNED`。

### 5.4 保存药品编码映射

```http
POST /api/pharmacy/his-drug-mappings
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "sourceSystem": "HIS",
  "hisDrugCode": "HIS-DRUG-001",
  "localCatalogId": 7
}
```

映射完成后，系统根据本地药品档案的特殊管理属性重新计算尚未发药申请的状态。

### 5.5 特殊管理药品复核

```http
POST /api/pharmacy/applications/101/review
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "decision": "APPROVED",
  "comment": "专用处方及限量核对通过"
}
```

`decision` 仅允许 `APPROVED` 或 `REJECTED`。普通药品申请不接受此接口操作；通用处方审核问题应由通用审核环节处理。

### 5.6 扫码发药和退药

发药：

```http
POST /api/pharmacy/application-items/1001/dispense
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "requestId": "DISPENSE-20260902-0001",
  "traceCode": "TRACE-DEMO-0001"
}
```

退药：

```http
POST /api/pharmacy/application-items/1001/return
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "requestId": "RETURN-20260902-0001",
  "traceCode": "TRACE-DEMO-0001"
}
```

两者都返回完整申请单，其中包含整单状态、复核信息和最新明细数量。`requestId` 用于阻止网络重试造成重复扣减或重复退药。

### 5.7 药品档案与特殊管理属性

```http
POST /catalog
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "drugName": "示例药品",
  "specification": "10mg*20片",
  "dosageForm": "片剂",
  "manufacturer": "示例药厂",
  "controlCategory": "PSYCHOTROPIC_II",
  "isSplitAllowed": true,
  "packageUnit": "盒",
  "minUnit": "片",
  "minUnitsPerPackage": 20,
  "lowStockThreshold": 30,
  "status": "ENABLED"
}
```

普通药品使用 `GENERAL`。前端默认选择普通药品，且普通药品不显示特殊标签。

### 5.8 HIS 状态回传报文

在 `rest` 模式下，系统向 `HIS_STATUS_CALLBACK_URL` 发送：

```json
{
  "eventId": "PHARMACY-<uuid>",
  "sourceSystem": "HIS",
  "applicationNo": "OP-20260902-0001",
  "revision": 1,
  "status": "DISPENSED",
  "reviewStatus": "APPROVED",
  "specialReviewRequired": false,
  "reviewedBy": "系统规则",
  "reviewedAt": "2026-09-02T09:35:00",
  "patientId": "P-DEMO-001",
  "operator": "测试药师(PHARMACIST)",
  "eventTime": "2026-09-02T09:40:00",
  "items": [
    {
      "itemNo": "ITEM-1",
      "hisDrugCode": "HIS-DRUG-001",
      "requestedQuantity": 1,
      "dispensedQuantity": 1,
      "returnedQuantity": 0,
      "unit": "盒",
      "controlCategory": "GENERAL",
      "status": "DISPENSED"
    }
  ]
}
```

回传携带与入站相同的 `X-HIS-Key`、时间戳、随机数和签名头。HIS 端只要返回任意 `2xx` 即视为接收成功。

## 6. 状态与枚举

### 6.1 申请单状态

| 状态 | 含义 |
| --- | --- |
| `RECEIVED` | 已接收，正在计算后续状态 |
| `MAPPING_REQUIRED` | 存在未映射药品 |
| `REVIEW_PENDING` | 特殊管理药品等待人工复核 |
| `REVIEW_REJECTED` | 特殊药品复核未通过 |
| `READY` | 可进入发药 |
| `PARTIALLY_DISPENSED` | 已发部分药品 |
| `DISPENSED` | 全部发药完成 |
| `RETURN_REQUIRED` | HIS 已撤销，必须退回已发药品 |
| `CANCELLED` | 未发药申请已撤销 |
| `RETURNED` | 已发药品全部退回 |

### 6.2 申请明细状态

| 状态 | 含义 |
| --- | --- |
| `UNMAPPED` | 未关联本地药品档案 |
| `PENDING` | 待发药 |
| `PARTIAL` | 部分发药 |
| `DISPENSED` | 明细全部发药 |
| `CANCELLED` | 明细已撤销 |
| `RETURNED` | 明细已退回 |

### 6.3 特殊管理属性

| 值 | 含义 |
| --- | --- |
| `GENERAL` | 普通药品 |
| `NARCOTIC` | 麻醉药品 |
| `PSYCHOTROPIC_I` | 第一类精神药品 |
| `PSYCHOTROPIC_II` | 第二类精神药品 |
| `MEDICAL_TOXIC` | 医疗用毒性药品 |

## 7. 幂等与重试约定

- HIS 入站使用全局唯一 `eventId` 防止重复接收。
- 申请修订使用递增 `revision`；相同或较低修订不会覆盖当前申请。
- 入库、拆零、发药和退药使用 `requestId` 防止客户端重复提交。
- 同一个 `requestId` 携带不同业务载荷时返回幂等冲突。
- HIS 回传失败自动重试，最终失败后通过 `/api/his/callbacks/{eventId}/retry` 人工补发。

## 8. 相关文档

- [系统架构](ARCHITECTURE.md)
- [文件与模块说明](PROJECT_STRUCTURE.md)
- [项目 README](../README.md)
