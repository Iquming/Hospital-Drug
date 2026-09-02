# 文件与模块说明

本文按业务功能介绍仓库中的主要文件，便于从“功能在哪里实现”快速定位代码。

## 1. 顶层目录

| 路径 | 职责 |
| --- | --- |
| `README.md` | 项目定位、主要功能、本地运行和文档入口 |
| `CONTEXT.md` | 医院药房领域术语及系统统一用语 |
| `docs/` | 系统架构、文件说明和 REST 接口文档 |
| `frontend/` | Vue 3 医院药房工作台 |
| `backend/` | Spring Boot 业务服务、MySQL 脚本和测试 |
| `.gitignore` | 排除构建产物、本地环境文件、IDE 配置和缓存 |

## 2. 前端文件

### 2.1 应用入口与框架

| 文件 | 功能 |
| --- | --- |
| `frontend/src/main.js` | 创建 Vue 应用并加载全局样式 |
| `frontend/src/App.vue` | 管理登录态、当前业务模块、通知和确认框 |
| `frontend/src/components/AppShell.vue` | 渲染侧栏、顶部栏、连接状态及当前业务页面 |
| `frontend/src/components/LoginView.vue` | 登录表单、密码显隐、提交状态和错误提示 |
| `frontend/src/config/navigation.js` | 导航分组、角色可见模块和角色默认入口 |
| `frontend/src/api/client.js` | Axios 客户端、API 基址、Bearer Token 与会话失效处理 |
| `frontend/src/styles/main.css` | 颜色、尺寸、排版、表格、按钮和表单等统一设计规范 |

### 2.2 处方与 HIS 闭环

| 文件 | 功能 |
| --- | --- |
| `frontend/src/components/HisApplicationWorkbench.vue` | 申请单队列、患者详情、药品映射、特殊药品复核、扫码发药和退药 |
| `frontend/src/components/HisIntegrationConsole.vue` | 模拟 HIS 申请、接口状态、回传时间线和失败任务补发 |

### 2.3 药事管理页面

| 文件 | 功能 |
| --- | --- |
| `frontend/src/views/DashboardView.vue` | 待处理处方、急诊、低库存、近效期、回传异常和业务趋势 |
| `frontend/src/views/PharmacyView.vue` | 从药品档案选择品种后扫码入库、库存处置、拆零建码和库存流水 |
| `frontend/src/views/CatalogView.vue` | 药品基础档案、包装换算、拆零规则和特殊管理属性维护 |
| `frontend/src/views/InventoryView.vue` | 创建盘点、扫描追溯码、查看差异和完成盘点 |
| `frontend/src/views/AuditView.vue` | 审计日志查询及发药、审计、盘点 CSV 报表导出 |
| `frontend/src/views/UserManagementView.vue` | 用户新增、编辑、密码重置、禁用和删除 |

### 2.4 公共交互组件

| 文件 | 功能 |
| --- | --- |
| `frontend/src/components/SidebarShortcuts.vue` | 按账号维护常用功能的添加、删除、重命名和排序 |
| `frontend/src/components/ui/AppDrawer.vue` | 右侧业务抽屉、焦点锁定、Esc 关闭和焦点恢复 |
| `frontend/src/components/ui/ConfirmDialog.vue` | 普通、警告和危险操作确认 |
| `frontend/src/components/ui/ToastRegion.vue` | 全局成功、提示和错误通知，提供 `aria-live` |
| `frontend/src/utils/download.js` | 携带认证信息下载 CSV 文件 |
| `frontend/src/assets/login-pharmacy-hero.jpg` | 压缩后的登录页药房实景图片 |

### 2.5 构建配置

| 文件 | 功能 |
| --- | --- |
| `frontend/package.json` | 前端依赖和 `dev`、`build`、`preview` 命令 |
| `frontend/package-lock.json` | npm 依赖锁定文件 |
| `frontend/vite.config.js` | Vite 与 Vue 插件配置 |
| `frontend/Dockerfile` | 前端容器构建入口 |

## 3. 后端文件

后端包路径统一位于 `backend/src/main/java/com/hospital/pharmacy/`。

### 3.1 启动与基础配置

| 文件 | 功能 |
| --- | --- |
| `HospitalDrugApplication.java` | Spring Boot 启动类和定时任务入口 |
| `config/SecurityConfig.java` | URL 权限、角色规则、无状态会话和认证过滤器 |
| `config/CorsConfig.java` | 前端来源和跨域配置 |
| `config/PasswordConfig.java` | BCrypt 密码编码器 |
| `resources/application.properties` | 数据库、端口、CORS、Token 和 HIS 默认配置 |

### 3.2 认证、用户与审计

| 层 | 文件 | 功能 |
| --- | --- | --- |
| Controller | `controller/AuthController.java` | 登录、当前用户和退出 |
| Controller | `controller/UserController.java` | 用户增删改、密码重置和状态维护 |
| Controller | `controller/AuditLogController.java` | 最近审计记录 |
| Service | `service/AuthService.java` | 账号校验、登录审计和令牌签发 |
| Service | `service/LoginGuardService.java` | 登录失败次数与临时限制 |
| Service | `service/SysUserService.java` | 用户、角色、密码和删除规则 |
| Service | `service/AuditLogService.java` | 统一记录操作者和业务变更 |
| Security | `security/TokenService.java` | 令牌生成、校验和撤销 |
| Security | `security/TokenAuthenticationFilter.java` | 从 Bearer Token 恢复当前用户 |
| Security | `security/CurrentUser.java`、`SecurityUtils.java` | 当前用户模型和访问工具 |
| DAO | `dao/SysUserDao.java`、`AuditLogDao.java` | 用户与审计数据库操作 |

### 3.3 药品档案与特殊管理属性

| 层 | 文件 | 功能 |
| --- | --- | --- |
| Controller | `controller/DrugCatalogController.java` | 药品档案查询、新增、更新和停用 |
| Service | `service/DrugCatalogService.java` | 属性校验、入库默认值继承和未发药申请重新计算 |
| DAO | `dao/DrugCatalogDao.java` | `drug_catalog` 表读写 |
| Model | `entity/DrugCatalog.java` | 药品名称、规格、包装、拆零、阈值和特殊管理属性 |
| Constant | `constant/DrugControlCategory.java` | 普通、麻醉、第一/二类精神和医疗用毒性药品枚举 |

### 3.4 HIS 申请单闭环

| 层 | 文件 | 功能 |
| --- | --- | --- |
| Controller | `controller/HisIntegrationController.java` | HIS 申请推送、修订和撤销入口 |
| Controller | `controller/PharmacyApplicationController.java` | 药房队列、映射、复核、发药、退药、回传和模拟 HIS |
| DTO | `dto/HisDtos.java` | HIS 请求、响应、发药、退药、复核和映射契约 |
| Service | `service/HisApiKeyService.java` | HIS 密钥、HMAC 签名、时间窗和随机数防重放 |
| Service | `service/HisApplicationService.java` | 入站幂等、版本控制、映射、撤销、复核和整单状态汇总 |
| Service | `service/ApplicationDispenseService.java` | 申请明细扫码发药、原路退药和事务一致性 |
| Service | `service/HisCallbackService.java` | 回传事件、定时发送、重试和人工补发 |
| DAO | `dao/HisIntegrationDao.java` | 申请、明细、映射、入站事件和回传队列 SQL |
| Model | `entity/DrugApplication.java`、`DrugApplicationItem.java` | 申请单主表和药品明细 |
| Model | `entity/HisDrugMapping.java`、`HisCallbackEvent.java` | 药品编码映射和回传事件 |
| Constant | `constant/HisApplicationStatus.java`、`HisApplicationItemStatus.java` | 整单及明细状态枚举 |

### 3.5 库存、追溯与拆零

| 层 | 文件 | 功能 |
| --- | --- | --- |
| Controller | `controller/DrugController.java` | 库存、入库、旧处方兼容接口、流水、预警和健康检查 |
| Controller | `controller/DrugSplitController.java` | 拆零建码和子码标签 |
| Controller | `controller/DeviceScanController.java` | 扫描场景和预期药品核验 |
| Service | `service/DrugAcceptanceService.java` | 入库有效期、重复追溯码和药品档案校验 |
| Service | `service/DrugDispenseService.java` | 旧处方兼容发药与退药流程 |
| Service | `service/DrugSplitService.java` | 拆零数量、母码库存和子码状态 |
| Service | `service/DeviceScanService.java` | 扫描结果及药品一致性检查 |
| DAO | `dao/DrugDao.java` | 库存、处方、流水和拆零相关 SQL |
| Model | `entity/DrugStock.java`、`DrugSplitCode.java`、`DispenseRecord.java` | 库存单品、拆零子码和业务流水 |
| Constant | `StockStatus.java`、`StockType.java`、`SplitCodeStatus.java`、`DispenseType.java` | 库存与发药状态常量 |

### 3.6 盘点、预警与报表

| 层 | 文件 | 功能 |
| --- | --- | --- |
| Controller | `controller/InventoryCheckController.java` | 盘点创建、扫描、明细和完成 |
| Controller | `controller/AlertController.java` | 低库存、近效期和业务风险摘要 |
| Controller | `controller/RecommendationController.java` | 按效期优先推荐库存单品 |
| Controller | `controller/ReportController.java` | 发药、审计和盘点 CSV 导出 |
| Service | `service/InventoryCheckService.java` | 盘点状态与差异计算 |
| Service | `service/AlertService.java` | 风险预警聚合 |
| Service | `service/ReportService.java` | CSV 内容生成 |
| DAO | `dao/InventoryCheckDao.java` | 盘点主表和明细 SQL |
| Model | `InventoryCheck.java`、`InventoryCheckItem.java`、`AuditLog.java` | 盘点和审计数据模型 |

### 3.7 一致性与错误处理

| 文件 | 功能 |
| --- | --- |
| `service/IdempotencyService.java` | 对重复业务请求返回首次结果或阻止载荷冲突 |
| `dao/IdempotentRequestDao.java` | 幂等请求状态与响应持久化 |
| `exception/BusinessException.java`、`ErrorCode.java` | 可识别的业务错误及错误码 |
| `exception/GlobalExceptionHandler.java`、`ApiError.java` | 将业务、安全和参数异常转换为统一 JSON 错误 |

## 4. 数据库脚本

脚本位于 `backend/sql/`，执行顺序以该目录的 `README.md` 为准。

| 文件 | 作用 |
| --- | --- |
| `split_drug_schema.sql` | 拆零字段、子追溯码和发药流水扩展 |
| `concurrency_hardening_schema.sql` | 乐观锁、唯一索引、幂等请求和药品编码序列 |
| `enhancement_schema.sql` | 审计、盘点和药品档案 |
| `user_table.sql` | 系统用户、角色和学习账号 |
| `his_integration_schema.sql` | HIS 申请、明细、映射、入站事件和回传队列 |
| `professional_pharmacy_hardening.sql` | 处方复核、原路退药、令牌撤销、登录保护和 HIS 防重放 |
| `controlled_drug_review_schema.sql` | 特殊管理属性及历史普通申请状态升级 |

## 5. 测试文件

测试位于 `backend/src/test/java/com/hospital/pharmacy/`：

| 文件 | 覆盖范围 |
| --- | --- |
| `HospitalDrugApplicationTests.java` | Spring 应用上下文 |
| `HisApplicationServiceTest.java` | 映射、普通/特殊复核、发药与退药状态汇总 |
| `ApplicationDispenseServiceTest.java` | 未复核发药拦截和原路退药归属校验 |
| `DrugCatalogServiceTest.java` | 入库继承药品档案与无效档案拦截 |
| `HisApiKeyServiceTest.java` | HIS 密钥和签名校验 |
| `HisCallbackServiceTest.java` | 回传队列处理 |

## 6. 查找功能的推荐路径

定位一个业务功能时，通常按以下顺序阅读：

`前端页面 -> Controller -> Service -> DAO -> Entity/DTO -> SQL 脚本 -> Test`

例如“特殊药品复核”对应：

`HisApplicationWorkbench.vue -> PharmacyApplicationController -> HisApplicationService -> HisIntegrationDao -> DrugApplication/DrugCatalog -> controlled_drug_review_schema.sql -> HisApplicationServiceTest`

## 7. 延伸文档

- [系统架构](ARCHITECTURE.md)
- [REST 接口说明](API.md)
- [项目 README](../README.md)
