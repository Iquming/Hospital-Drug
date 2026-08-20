# Hospital-Drug

医院药品闭环管理系统，包含 Spring Boot 后端和 Vue 前端。

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
