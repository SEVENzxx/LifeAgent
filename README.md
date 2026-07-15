# LifeAgent

LifeAgent 采用 Java 控制面与 Python AI Sidecar。Java 和 PostgreSQL 是业务事实源，Python 只负责无状态语义推理，Redis 只缓存可重建数据。

## 技术基线

- Java 17、Spring Boot 3.5.3、Maven 单模块、MyBatis-Plus 3.5.12、SpringDoc OpenAPI 2.8.17
- PostgreSQL 16、Redis 7、Flyway
- Python 3.11、FastAPI、Pydantic、Mock/OpenAI-Compatible Provider
- Docker Compose、Nginx

## 启动

默认使用 Mock Provider，不需要 LLM 密钥：

```bash
docker compose up --build --wait
```

启动成功后：

- Nginx 健康检查：`http://localhost:8080/nginx-health`
- Java 就绪检查：`http://localhost:8080/actuator/health/readiness`
- Java 系统信息接口：`http://localhost:8080/api/v1/system`
- Java OpenAPI：`http://localhost:8080/v3/api-docs`
- Java Swagger UI：`http://localhost:8080/swagger-ui/index.html`

使用真实模型前，把 `.env.example` 复制为 `.env`，将 `LLM_PROVIDER` 改为 `openai-compat` 并配置 `OPENAI_COMPAT_API_KEY`（阿里百炼等 OpenAI 兼容平台密钥）。

## 本地测试

Java 项目要求 JDK 17：

```bash
mvn -f backend-java/pom.xml test
```

Python：

```bash
python -m pip install -e "ai-python[dev]"
python -m pytest ai-python/tests
```

## 开发约束

- 完整 PRD、架构、协议、编码规范和任务卡由独立的本地私有文档仓库维护，不随本公开代码仓库发布。
- Java 与 Python 的轮次解析协议发生变化时，必须在同一任务中同步修改两端模型、公开接口说明和契约测试。
- AI 工具不得执行 `git add`、`git commit` 或 `git push`，代码必须经过人工 review 后再提交。

## 运行角色

单机单用户版本只运行一个 `lifeagent-java` 进程。该进程同时提供 HTTP API，并在进程内运行消息处理、调度和 Outbox 投递任务；代码仍保持 Controller、应用服务、Worker 和渠道适配器的职责边界。

后台任务可通过 `WORKER_ENABLED` 开关控制，Compose 默认显式启用。只有未来出现独立扩容或故障隔离需求时，才重新评估拆分 Worker 进程。
