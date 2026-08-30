# Hospital-Drug

医院药品闭环管理学习系统，用于熟悉医院门诊药房、药库、HIS 对接及药品追溯等常见业务流程。

> 本项目为个人学习与业务流程演练项目，不用于真实诊疗决策或生产环境，也不应录入真实患者数据。

## 项目作用

系统模拟 HIS 开立药品申请后，药房接收、匹配药品、扫码发药、退药并回传状态的完整闭环，帮助理解医院信息系统与药房工作台之间的协作方式。

主要功能：

- HIS 门诊申请单接收、修订、撤销和幂等处理
- HIS 药品编码与本地药品档案映射
- 一单多药、逐项发药、分次发药和退药
- 库存入库、出库、拆零、追溯码校验和效期预警
- 发药状态异步回传、失败重试与人工补发
- 库存盘点、审计日志、报表导出和用户权限
- 管理员、药师、护士角色工作台及可配置常用功能

## 技术架构

```text
Hospital-Drug/
├─ backend/                 Spring Boot 后端服务
│  ├─ sql/                  MySQL 初始化与升级脚本
│  └─ src/                  业务代码与测试
├─ frontend/                Vue 3 药房工作台
│  ├─ public/               静态资源
│  └─ src/                  页面、组件与图片资源
├─ .gitignore
└─ README.md
```

- 后端：Java 17、Spring Boot、Spring Security、Spring JDBC、MySQL
- 前端：Vue 3、Vite、Axios、ECharts、Lucide Icons
- 默认端口：前端 `5173`，后端 `8081`

## 本地运行

准备 MySQL 数据库：

```sql
CREATE DATABASE hospital_drug_system
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

根据 [backend/sql/README.md](backend/sql/README.md) 执行数据库脚本，然后启动后端：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

访问 [http://127.0.0.1:5173/](http://127.0.0.1:5173/)。学习环境默认管理员账号为 `admin`，密码为 `123456`。

## 环境配置

后端支持通过环境变量覆盖本地默认值：

| 环境变量 | 作用 | 默认值 |
| --- | --- | --- |
| `DB_URL` | MySQL 连接地址 | `jdbc:mysql://localhost:3306/hospital_drug_system` |
| `DB_USERNAME` | 数据库账号 | `root` |
| `DB_PASSWORD` | 数据库密码 | `123456` |
| `TOKEN_SECRET` | 登录令牌签名密钥 | 本地学习密钥 |
| `CORS_ALLOWED_ORIGINS` | 允许访问后端的前端地址 | 本地开发地址 |
| `HIS_MODE` | HIS 模式：`mock` 或 `rest` | `mock` |
| `HIS_API_KEY` | HIS 接口认证密钥 | `his-demo-key` |
| `HIS_STATUS_CALLBACK_URL` | 真实 HIS 状态回传地址 | 本地模拟地址 |

连接真实接口前应设置独立的数据库密码、令牌密钥和 HIS 密钥，不要继续使用学习环境默认值。

## HIS 闭环演练

1. 管理员进入“HIS 联调”，生成模拟门诊药品申请。
2. 在“处方调剂”中完成 HIS 药品编码映射。
3. 药师按申请明细扫码并分次或一次性发药。
4. 系统在同一事务中核销库存、记录发药结果并创建回传任务。
5. 在“HIS 联调”查看状态时间线、回传结果和异常补发。
6. 对已发药明细执行退药，观察申请单最终转为 `RETURNED`。

真实 HIS 可通过以下接口推送申请：

```http
POST /api/integration/his/v1/drug-applications
X-HIS-Key: <HIS_API_KEY>
Content-Type: application/json
```

## 验证命令

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run build
```
