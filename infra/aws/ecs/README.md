# ECS Fargate 方案（方案 B）说明

`task-definitions/scan-service.json` 是模板；gateway / project-service / alert-service 只需改名字、端口（8080/8081/8083）、
数据库名和 task role。gateway 额外去掉 Kafka 相关环境变量，加 `DEPWATCH_ROUTES_*`，指向 Service Connect 的 DNS 名：

```
DEPWATCH_ROUTES_PROJECT=http://project-service.depwatch.local:8081
DEPWATCH_ROUTES_SCAN=http://scan-service.depwatch.local:8082
DEPWATCH_ROUTES_ALERT=http://alert-service.depwatch.local:8083
DEPWATCH_ROUTES_ALERT_WS=ws://alert-service.depwatch.local:8083
```

## 需要的 IAM Role

| Role | 作用 | 权限 |
|---|---|---|
| `depwatch-ecs-execution` | ECS agent 拉镜像、写日志、读 Secrets | `AmazonECSTaskExecutionRolePolicy` + `secretsmanager:GetSecretValue` |
| `depwatch-scan-service-task` | 容器内代码的身份 | S3 读 manifest bucket；MSK IAM 认证（`kafka-cluster:Connect/ReadData/WriteData/...`） |
| `depwatch-project-service-task` | 同上 | S3 读写 manifest bucket；MSK 写 |
| `depwatch-alert-service-task` | 同上 | MSK 读 |

## MSK IAM 认证

MSK Serverless 只支持 IAM 认证。scan-service 的 pom 需要加：

```xml
<dependency>
    <groupId>software.amazon.msk</groupId>
    <artifactId>aws-msk-iam-auth</artifactId>
    <version>2.2.0</version>
    <scope>runtime</scope>
</dependency>
```

然后任务定义里的 `SPRING_KAFKA_PROPERTIES_*` 环境变量会被 Spring Boot 映射成 `spring.kafka.properties.*`。

## 自动扩缩容（scan-service）

Kafka consumer lag 没有现成的 CloudWatch 指标（MSK Serverless 有 `SumOffsetLag`，按消费组维度）。
在 Application Auto Scaling 里创建 Target Tracking 策略：目标 `SumOffsetLag` ≤ 100，min 1 / max 5 task。

## 把 WebSocket 变成多实例安全

alert-service 跑多副本后，内存 SimpleBroker 只能推给连到自己这台的客户端。两个解法：
1. **ALB 粘性会话** + 让 scan.progress 事件通过 Kafka 广播（每个副本都消费一份，用不同 group id）——改动最小；
2. 换 `enableStompBrokerRelay()` 接 Amazon MQ (RabbitMQ)——标准做法，多一个组件。

面试时能讲出这个问题及两种解法，比直接用方案 2 更能体现思考。
