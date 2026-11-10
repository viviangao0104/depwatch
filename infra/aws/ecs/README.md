# ECS Fargate (Option B) notes

`task-definitions/scan-service.json` is the template. gateway / project-service / alert-service differ only in name, port (8080 / 8081 / 8083), database name and task role. The gateway additionally drops the Kafka variables and adds `DEPWATCH_ROUTES_*` pointing at the Service Connect DNS names:

```
DEPWATCH_ROUTES_PROJECT=http://project-service.depwatch.local:8081
DEPWATCH_ROUTES_SCAN=http://scan-service.depwatch.local:8082
DEPWATCH_ROUTES_ALERT=http://alert-service.depwatch.local:8083
DEPWATCH_ROUTES_ALERT_WS=ws://alert-service.depwatch.local:8083
```

## IAM roles required

| Role | Purpose | Permissions |
|---|---|---|
| `depwatch-ecs-execution` | ECS agent pulls images, writes logs, reads secrets | `AmazonECSTaskExecutionRolePolicy` + `secretsmanager:GetSecretValue` |
| `depwatch-scan-service-task` | identity of the code inside the container | S3 read on the manifest bucket; MSK IAM auth (`kafka-cluster:Connect/ReadData/WriteData/…`) |
| `depwatch-project-service-task` | same | S3 read/write on the manifest bucket; MSK write |
| `depwatch-alert-service-task` | same | MSK read |

## MSK IAM authentication

MSK Serverless supports IAM auth only. Add to each service's pom:

```xml
<dependency>
    <groupId>software.amazon.msk</groupId>
    <artifactId>aws-msk-iam-auth</artifactId>
    <version>2.2.0</version>
    <scope>runtime</scope>
</dependency>
```

The `SPRING_KAFKA_PROPERTIES_*` environment variables in the task definition map to `spring.kafka.properties.*`.

## Autoscaling scan-service

There is no ready-made CloudWatch metric for consumer lag in classic Kafka, but MSK Serverless exposes `SumOffsetLag` per consumer group. Create an Application Auto Scaling target-tracking policy: target `SumOffsetLag` ≤ 100, min 1 / max 5 tasks.

## Making WebSocket safe across replicas

Once alert-service runs more than one replica, the in-memory SimpleBroker can only push to clients connected to *that* replica. Two fixes:
1. **ALB sticky sessions** + broadcast `scan.progress` through Kafka with a distinct consumer group per replica — smallest change;
2. Switch to `enableStompBrokerRelay()` backed by Amazon MQ (RabbitMQ) — the standard answer, one more component.

Being able to name the problem and both fixes in an interview is worth more than silently picking option 2.
