# Hospital-Drug

医院药品闭环管理系统，包含 Spring Boot 后端、Vue 前端和 HIS 门诊处方申请闭环。

## 项目结构

- `backend/`：后端服务，默认端口 `8081`
- `frontend/`：前端页面，默认端口 `5173`

## 本地启动

后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd frontend
npm install
npm run dev
```

默认前端访问地址：

```text
http://127.0.0.1:5173/
```

默认后端接口地址：

```text
http://127.0.0.1:8081/
```

## 数据库配置

后端默认连接：

```text
jdbc:mysql://localhost:3306/hospital_drug_system
```

可以通过环境变量覆盖：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`
- `CORS_ALLOWED_ORIGINS`

## 数据库初始化与升级

在 `hospital_drug_system` 数据库中按现有项目情况执行 SQL：

1. 首次部署先准备原有的 `drug_stock`、`prescription` 和 `dispense_record` 基础表。
2. 执行 `backend/sql/split_drug_schema.sql`。
3. 执行 `backend/sql/concurrency_hardening_schema.sql`。
4. 执行 `backend/sql/enhancement_schema.sql` 和 `backend/sql/user_table.sql`。
5. 最后执行 `backend/sql/his_integration_schema.sql`。

`his_integration_schema.sql` 会创建申请单、申请明细、HIS 药品编码映射、入站事件和状态回传队列，并为库存及发药流水补充关联字段。

## HIS 运行模式

默认使用本地模拟 HIS：

```text
HIS_MODE=mock
HIS_API_KEY=his-demo-key
```

管理员登录后进入“HIS联调”，可以生成模拟门诊处方。药房在“处方调剂”中完成药品映射和扫码发药，状态回传会在联调中心显示为已送达。

连接真实 REST HIS 时设置：

```text
HIS_MODE=rest
HIS_API_KEY=由HIS与药房约定的接口密钥
HIS_STATUS_CALLBACK_URL=http://his-address/api/his/drug-application-status
HIS_CONNECT_TIMEOUT_MS=3000
HIS_READ_TIMEOUT_MS=5000
```

HIS 向药房推送申请单：

```http
POST /api/integration/his/v1/drug-applications
X-HIS-Key: his-demo-key
Content-Type: application/json
```

示例请求：

```json
{
  "eventId": "HIS-EVENT-20260830-001",
  "sourceSystem": "HIS",
  "applicationNo": "HIS-OP-20260830-001",
  "revision": 1,
  "patientId": "P001",
  "patientName": "张三",
  "encounterNo": "OP-20260830-001",
  "departmentCode": "OPD",
  "departmentName": "门诊部",
  "priority": "NORMAL",
  "prescribedAt": "2026-08-30T10:30:00",
  "items": [
    {
      "itemNo": "ITEM-1",
      "hisDrugCode": "HIS-DRUG-001",
      "drugName": "阿莫西林胶囊",
      "specification": "0.25g*24粒",
      "quantity": 1,
      "unit": "盒"
    }
  ]
}
```

HIS 撤销尚未发药的申请单：

```http
POST /api/integration/his/v1/drug-applications/{applicationNo}/cancel?sourceSystem=HIS
X-HIS-Key: his-demo-key
```

如果申请单已经发药，接口返回 `HIS_RETURN_REQUIRED`，药房必须先完成退药。状态回传失败会自动重试 5 次，最终失败后可在“HIS联调”中人工补发。
