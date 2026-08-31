# Database Scripts

本目录保存 Hospital-Drug 的 MySQL 初始化与升级脚本。项目暂未引入自动迁移工具，请在执行前备份数据库，并记录已执行的脚本。

建议顺序：

1. 准备原有基础表：`drug_stock`、`prescription`、`dispense_record`。
2. `split_drug_schema.sql`：增加拆零药品与子追溯码结构。
3. `concurrency_hardening_schema.sql`：增加库存并发控制与幂等字段。
4. `enhancement_schema.sql`：增加药品档案、盘点和审计相关结构。
5. `user_table.sql`：增加系统用户与角色数据。
6. `his_integration_schema.sql`：增加 HIS 申请单、药品映射和状态回传队列。
7. `professional_pharmacy_hardening.sql`：增加处方审方、原路退药、HIS 防重放和回传恢复字段。

脚本以手动升级为前提。已经执行过的脚本不要重复运行，也不要在未核对当前数据库结构时跳过顺序。升级到专业审方流程后，历史未发药申请会进入 `REVIEW_PENDING`，需要药师重新审核。
