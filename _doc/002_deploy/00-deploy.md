# 部署与运行

## 1. 运行形态

`z-sense-core` 是纯领域库，编排方（z-security）按 Maven 坐标直接依赖，**不必起进程**——
安全线的实际形态就是编排层在自己的请求路径上调 `SenseService`。

`z-sense-web` 提供一层独立可跑的 REST 管控面（`ZSenseWebApplication`）：

| 项 | 值 |
|---|---|
| 端口 | `8094`（`server.port=${Z_SENSE_SERVER_PORT:8094}`） |
| Java / 框架 | 17 / Spring Boot 3.3.13 火车（parent `z-boot-parent:1.2.0`） |
| HTTP 面 | `POST /z-team/api/sense/detect`（识别）· `GET /api/sense/health`（健康） |
| 自动装配 | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `SenseAutoConfiguration` |
| 制品 | 本轮**不发 Maven Central** |

## 2. 环境变量

数据源键名模式 `z.base.db.sense.*`（`ModuleDataSourceTemplate` 的权威前缀常量，缺省回落 `z.base.db.default.*`）。
**凭据一律走环境变量注入，不落明文**（lead 008 凭证管理规范）。

| 变量 | 默认 | 用途 |
|---|---|---|
| `Z_SENSE_SERVER_PORT` | `8094` | HTTP 端口 |
| `Z_SENSE_DB_HOST` | `127.0.0.1` | 数据源主机 |
| `Z_SENSE_DB_PORT` | `3306` | 数据源端口 |
| `Z_SENSE_DB_NAME` | `oc` | 库名（安全线共用单库，同 z-trade / z-biz / z-opcs） |
| `Z_SENSE_DB_USERNAME` | **空** | 必须注入，无默认值 |
| `Z_SENSE_DB_PASSWORD` | **空** | 必须注入，无默认值 |

连接池：`initial-size=2` / `min-idle=2` / `max-active=10`；`mybatis-plus.configuration.map-underscore-to-camel-case=true`。

显式给 `z.base.db.sense.url` 即切直连模式（绕开键名模式拼装）——本地验收就跑这条路到 H2：

```
z.base.db.sense.url=jdbc:h2:mem:zsense_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1
z.base.db.sense.username=sa
z.base.db.sense.password=
```

> ⚠ 本仓验收**全部在本地 H2 内存库**：不连生产 RDS、不在 `oc` 库建表、不对任何真实库写入。

## 3. 建表

**本仓无 DDL**：M1 的规则集内置在代码（`support/BuiltinRules`），不读库，因此没有本仓要建的表。
安全线真正需要建表的是 z-audit（哈希链台账）与 z-security（策略表），
两者的 `init.sql` 在各自仓的 `_doc/002_deploy/`，属**人工执行动作**，见
[`_doc/005_testing/001_验收复跑.md`](_doc/005_testing/001_验收复跑.md) §4 的生产执行清单。

## 4. 构建与验证

```bash
export JAVA_HOME=/Users/zifang/Library/Java/JavaVirtualMachines/corretto-17.0.19/Contents/Home

mvn -f z-sense-core/pom.xml test    # 15 例
mvn -f z-sense-web/pom.xml  test    # 5 例（含真起上下文的端点验收）
```

> ⚠ **必须 JDK 17**。本机默认 JDK 23 会让 Groovy 3.0.9 读不懂 class major 67 ⇒ **假红**。
> ⚠ 复跑命令**别接 `| tail; echo $?`** —— 取到的是 `tail` 的退出码 ⇒ **假绿**；先重定向到日志再取 `$?`。

端点验收不靠外部进程：`SenseApiE2ETest` 以 `WebEnvironment.RANDOM_PORT` **真起 Spring 上下文**
并用 `TestRestTemplate` 打真 HTTP，因此不需要先启服务。

## 5. 起进程这件事的当前状态

`z-sense-web/pom.xml` **没有配 `spring-boot-maven-plugin`** —— 打出来的是普通 jar（无 repackage、无 `Main-Class`），
所以 `mvn spring-boot:run` 与 `java -jar` 两条路**目前都走不通**。
M1 的口径是「能力以库形态被编排层消费 + REST 面由测试证明可路由」；
要独立部署成进程，需要补 repackage 插件与镜像资产，届时按 lead 002 规范落 `002_deploy/` 与 `003_script/`。
