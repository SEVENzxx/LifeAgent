# LifeAgent

LifeAgent 采用 Java 控制面与 Python AI Sidecar。Java 和 PostgreSQL 是业务事实源，Python 只负责无状态语义推理，Redis 只缓存可重建数据。

## 技术基线

- Java 17、Spring Boot 3.5.3、Maven 单模块、MyBatis-Plus 3.5.12、SpringDoc OpenAPI 2.8.17
- PostgreSQL 16、Redis 7、Flyway
- Python 3.11、FastAPI、Pydantic、DeepSeek/Mock Provider
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

使用真实 DeepSeek 前，把 `.env.example` 复制为 `.env`，将 `LLM_PROVIDER` 改为 `deepseek` 并设置 `DEEPSEEK_API_KEY`。

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

- 所有新增和修改代码必须遵循 [`CODING_STANDARDS.md`](./CODING_STANDARDS.md)。
- Java 与 Python 的轮次解析模型必须遵循 [`docs/AI_PROTOCOL.md`](./docs/AI_PROTOCOL.md)，协议字段变更时同步修改两端模型、文档和测试。
- AI 工具不得执行 `git add`、`git commit` 或 `git push`，代码必须经过人工 review 后再提交。

## 运行角色

`lifeagent-java` 与 `lifeagent-java-worker` 使用同一镜像：前者启用 `api` Profile，后者启用 `worker` Profile。本地需要合并运行时可启用 `all-in-one` Profile。
