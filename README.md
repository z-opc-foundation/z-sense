# z-sense

> **敏感识别与分类分级引擎** —— 规则目录 + 可插拔识别器 + 命中定位 + 分类 × 分级。
> 边界一句话：**只回答「哪里是敏感的、多敏感」，不改内容、不决定脱不脱敏。**

安全线四层能力栈的**底座**（全族依赖它）。同族：`z-mask`（脱敏）· `z-watermark`（水印）· `z-audit`（审计）· `z-security`（编排）。

立项文档：lead `002_项目需求/001_特性需求/015_z-security_数据安全线`。

## 📋 基本信息

| 项 | 值 |
|---|---|
| 层 | L3 能力仓（可被 Maven Central 口径消费；**本轮不发制品**） |
| 坐标 | `io.github.yuku123:z-sense`，版本 `1.0.0` |
| parent | `io.github.yuku123:z-boot-parent:1.2.2`（Java 17 / Spring Boot 3.3.13 火车） |
| 包名 | `com.zifang.z.sense.core.*`（groupId 与包名 namespace 解耦，照 z-cognition 先例） |
| 端口 | 8094 |
| 模块 | `z-sense-core`（领域模型 + 领域服务 + SPI）· `z-sense-web`（数据源 + health + AutoConfiguration） |

## 🎯 能力清单

| 能力 | 状态 | 代码位置 |
|---|---|---|
| 规则目录（规则即数据：ruleId / 类别 / 分级 / 正则 / 优先级） | ✅ | `domain/SenseRule` · `support/BuiltinRules` |
| 可插拔识别器 SPI | ✅ | `spi/SensitiveDetector` |
| 命中定位（`[start, end)` 偏移 + 片段） | ✅ | `domain/SensitiveHit` |
| 分类 × 分级（10 类别 × L1–L4 正交） | ✅ | `domain/SensitiveCategory` · `domain/SensitiveLevel` |
| 重叠消解（级别高者留；同级先注册者留） | ✅ | `domain/SenseResult#addHit` |
| 语义校验器（身份证校验位 / 手机号号段 / 银行卡 Luhn） | ✅ | `support/BuiltinRules#valid*` |
| 弱规则收紧（人名三闸） | ✅ | `support/BuiltinRules#validPersonName` |
| 门面（异常 ⇒ 空结果，不拖垮主链路） | ✅ | `service/SenseService` |
| 规则集持久化（DB） | ❌ M2 | 当前规则集在代码里 |
| 规则热更新 / 版本化 | ❌ M2 | — |

## 🔑 核心口径

### 敏感识别 = 形状 + 语义双闸

正则只保证「形状像」，不保证「是真的」：

| 数据 | 形状 | 语义闸 |
|---|---|---|
| 身份证（18 位） | `\d{17}[0-9Xx]` | **GB 11643 校验位**（加权因子 `7 9 10 5 8 4 2 1 6 3 7 9 10 5 8 4 2` / 码表 `10X98765432`） |
| 手机号 | `1[3-9]\d{9}` | **号段**：中间四位不全 0 |
| 银行卡 | `\d{13,19}` | **Luhn 校验位** |
| 邮箱 | 标准正则 | 无（形状即足够） |

校验不过 ⇒ **放弃命中**（宁漏不误：漏了只是少脱一处，误了会把正常数据当敏感处理）。

### 分类 × 分级正交（不是笛卡尔积）

| 维度 | 作用 | 取值 |
|---|---|---|
| **类别** `SensitiveCategory` | **脱敏策略的选择键** | PERSONAL_NAME / CONTACT / ID_CARD / BANK_ACCOUNT / ADDRESS / CREDENTIAL / IP / BUSINESS_SECRET / INTERNAL_INFO / OTHER |
| **分级** `SensitiveLevel` | **防护强度调节**（这档要不要拦、留多久） | L1 公开 / L2 内部 / L3 敏感 / L4 核心 |

> 避免退化成「L3 身份证 / L4 身份证」。

### 弱规则必须收紧（误报比漏报危险）

中文姓名（2~4 字）太泛，三闸收紧：

1. **姓氏白名单**（427 个去重首字）
2. **通用词黑名单**（48 词：备注 / 说明 / 手机 …）
3. **组合词黑名单**（33 词：都是本人 / 查询 / 个人 …）

> 实测不加收紧会把「手机」「卡号」「都是本人」判成人名 ⇒ 污染业务数据。
> L2 级弱规则遵循**宁漏不误**。

### 命中必带位置 ⇒ 识别一次、脱敏一次

`SensitiveHit` 带 `[start, end)` ⇒ 下游 z-mask **按位置替换**而不是重扫 ⇒ 口径确定、结果幂等。

### 重叠消解

同一位置被多规则命中 ⇒ **保留级别更高者**；级别相同 ⇒ 保留**先注册者** ⇒ 结果幂等稳定。

## 🏗️ 项目结构

```
z-sense/
├── pom.xml
├── z-sense-core/
│   └── src/main/java/com/zifang/z/sense/core/
│       ├── domain/      SenseRule · SenseResult · SensitiveHit · SensitiveCategory · SensitiveLevel
│       ├── spi/         SensitiveDetector（可插拔识别器）
│       ├── service/     SenseService（门面）· RuleSensitiveDetector（规则实现）
│       └── support/     BuiltinRules（9 条内置规则 + 校验器 + 白黑名单）
├── z-sense-starter/          ← M2 #23 拆出：宿主嵌入用（无 web / 健康检查 / 数据源包袱）
│   └── src/main/
│       ├── java/com/zifang/z/sense/starter/config/
│       │                  SenseStarterAutoConfiguration（只装配引擎）
│       └── resources/META-INF/spring/
│                          org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── z-sense-web/              ← 管控面部署（传递依赖 starter）
    └── src/main/java/com/zifang/z/sense/web/
        ├── config/      SenseAutoConfiguration（数据源 + 健康检查）· SenseModuleDataSource · SenseHealthIndicator
        └── api/         SenseController（/z-team/api/sense/detect）· SenseHealthController
```

> **starter 与 web 的分工（M2 #23）**：早先引擎装配长在 web 里 ⇒ 宿主要嵌入识别能力就得连
> `spring-boot-starter-web`、健康检查、`z-boot-datasource-starter`（druid + mybatis + mysql 驱动）一起拖进来。
> 拆分后：**只想嵌入引擎 ⇒ 依赖 `z-sense-starter`（轻）；要跑管控面 ⇒ 依赖 `z-sense-web`（传递带 starter）**。

## 🧪 测试

```bash
# ⚠ 必须用 JDK 17（本机默认 JDK 23 会让 Groovy 读不懂 class major 67 ⇒ 假红）
export JAVA_HOME=/Users/zifang/Library/Java/JavaVirtualMachines/corretto-17.0.19/Contents/Home
bash ../../.workbuddy/run-all-tests.sh z-sense     # 全 reactor（core + starter + web）
```

> **CI**：push / PR 自动跑 GitHub Actions（`.github/workflows/ci.yml`：JDK 17 + `mvn -B -ntp verify`）。

**22 例全绿**（core 15 + starter 2 + web 5，10 个模块零失败）：

- **core 15**：身份证校验位（对/错）、手机号号段、邮箱、凭据、最高分级、类别分布、
  空输入、普通文本不误伤、阈值判定、幂等、位置回切。
- **starter 2**（M2 #23 新增）：宿主只放 starter jar（`com.example.embedhost` 包树，**组件扫描扫不到装配类**）
  ⇒ 引擎经 imports 机制自动装配；imports 文件每一行逐行验类存在（**抓得住包名斜杠笔误**）。
- **web 5**：REST 端点可路由 + 健康检查。

## 📌 与同族的关系

```
z-security（编排）
   ├─▶ z-sense      detect(text) → SenseResult（命中列表 + 分级）   ← 本仓
   ├─▶ z-mask       按命中位置遮蔽
   ├─▶ z-watermark  打溯源水印
   └─▶ z-audit      留痕（哈希链）
```

**本仓不依赖其它三仓**（底座方向：z-mask 依赖本仓，反向不依赖）。

## 文档目录

本项目文档统一收口在 `_doc/` 下（lead 002 规范八类固定编号表，桶按需存在）:

- [`_doc/001_arch/`](_doc/001_arch/) — 架构与口径：
  - [`00-overview.md`](_doc/001_arch/00-overview.md) — 总体设计（模块与依赖方向、形状+语义双闸、类别×分级正交、弱规则收紧、重叠消解、门面不抛异常、M1 边界）。
- [`_doc/002_deploy/`](_doc/002_deploy/) — 部署与运行：
  - [`00-deploy.md`](_doc/002_deploy/00-deploy.md) — 端口 8094、`Z_SENSE_*` 环境变量、`z.base.db.sense.*` 键名模式、建表口径（本仓无 DDL）、构建验证与「起进程」当前状态。
- [`_doc/005_testing/`](_doc/005_testing/) — 验收复跑：
  - [`001_验收复跑.md`](_doc/005_testing/001_验收复跑.md) — 五仓复跑命令、实测读数、已知边界与生产执行清单。

---

<!-- z-opc-foundation-sibling-registry:v1 -->
## 🔗 兄弟仓全景（z-opc-foundation 组织）

本仓隶属于 [z-opc-foundation](https://github.com/orgs/z-opc-foundation/repositories) 组织——一人公司基座的全部自研件（现存 61 仓，含私有仓；口径 2026-11-24 GitHub org API 实读）。

| 仓 | 一句话说明 |
|---|---|
| [z-agent](https://github.com/z-opc-foundation/z-agent) | 云端 Agent 平台的库与 starter——ReAct/Plan 实现 + 注册中心 + REST 网关 + 只读控制面 |
| [z-agent-kernel](https://github.com/z-opc-foundation/z-agent-kernel) | Agent 框架基座——消息/工具/记忆/provider/会话 SPI + 轻量默认实现（纯 POJO 无 Spring） |
| [z-asset](https://github.com/z-opc-foundation/z-asset) | 企业资产台账——实物设备全生命周期 + 域名/证书/许可到期提醒 |
| [z-audit](https://github.com/z-opc-foundation/z-audit) | 审计与合规留痕——事件模型 + 哈希链 + 事件出口 SPI + 留存期策略 |
| [z-biz](https://github.com/z-opc-foundation/z-biz) | 商务外壳域——客户/合同/票据/对账四聚合 |
| [z-biz-erp](https://github.com/z-opc-foundation/z-biz-erp) | 进销存 ERP——商品/客商/采购/销售/库存流水/应收应付核销（z-biz 业务线） |
| [z-biz-security](https://github.com/z-opc-foundation/z-biz-security) | 业务数据安全防护入口——场景化策略注册 + 安全线四段流水线对外编排 |
| [z-boot](https://github.com/z-opc-foundation/z-boot) | Spring Boot Starter 聚合仓 + 版本权威（火车制）——唯一消费入口 z-boot-parent |
| [z-bot](https://github.com/z-opc-foundation/z-bot) | 本地 agent 应用——终端 CLI + HTTP/SSE web 控制台 + 多通道 gateway（飞书/钉钉/webhook） |
| [z-cache](https://github.com/z-opc-foundation/z-cache) | RESP2 协议兼容的分布式内存缓存——Java 8 + Netty，Redis 客户端零修改直连 |
| [z-camuda](https://github.com/z-opc-foundation/z-camuda) | 【已退役·归档】基于 Camunda 的工作流封装层（原 z-wf 改名，与现行 z-wf 无共同历史） |
| [z-cognition](https://github.com/z-opc-foundation/z-cognition) | 业务认知域/认知舱——术语/概念/证据链 + memory/kb/ontology 渐进式模块 |
| [z-config](https://github.com/z-opc-foundation/z-config) | 分布式配置中心 + 服务注册中心（类 Nacos）——Netty 长连接推送 + React 控制台 |
| [z-ctc](https://github.com/z-opc-foundation/z-ctc) | 4A 中心——认证/授权/账户/审计，多租户 JWT + SSO 跨子域 |
| [z-dms](https://github.com/z-opc-foundation/z-dms) | 数据源管控底座——统一登记、统一建池、统一探活、变更统一推送 |
| [z-env](https://github.com/z-opc-foundation/z-env) | 本地中间件与基础设施环境——31 个 docker-compose 场景 + make 一键运维 |
| [z-etl](https://github.com/z-opc-foundation/z-etl) | ETL 引擎——DataX 式线性作业 + Flink 式 DAG 流管道（键控状态/水位/窗口） |
| [z-ext](https://github.com/z-opc-foundation/z-ext) | Java 扩展点框架——@ExtPoint/@ExtImpl 运行期选实现，五档侵入方式 |
| [z-frontend-common](https://github.com/z-opc-foundation/z-frontend-common) | 全仓共享前端基座——AppLayout/LoginPage 等前端规范的唯一承载 |
| [z-frontend-markdown](https://github.com/z-opc-foundation/z-frontend-markdown) | Markdown 通用共享组件包（npm）——MarkdownLiveEditor/NoteMarkdown/MermaidBlock 单一真源 |
| [z-frontend-render](https://github.com/z-opc-foundation/z-frontend-render) | 平台共享前端渲染引擎——后端 schema 是唯一事实源，报表与低代码共用 |
| [z-graph](https://github.com/z-opc-foundation/z-graph) | 独立图数据库——MVCC 版本链存储 + OpenCypher 子集 + Bolt 风格协议 + React 控制台 |
| [z-gw](https://github.com/z-opc-foundation/z-gw) | 自研高性能 API 网关——Netty 手写运行时，Route/Predicate/Filter 模型可嵌 Spring Boot |
| [z-indexer](https://github.com/z-opc-foundation/z-indexer) | 代码索引服务——仓库注册表 + 定时重建索引 + 符号/调用链查询 |
| [z-introduce](https://github.com/z-opc-foundation/z-introduce) | 组织仓库展示站——全仓实测元数据汇总成可搜索静态站点 |
| [z-lc](https://github.com/z-opc-foundation/z-lc) | 低代码平台——DB 驱动通用 CRUD 引擎 + 报表/模型设计框架 |
| [z-llm-agent](https://github.com/z-opc-foundation/z-llm-agent) | CLI Agent 服务化——把本机 CLI agent（CC/Hermes/Qoder 等）暴露成 OpenAI 兼容对话 API |
| [z-llm-gateway](https://github.com/z-opc-foundation/z-llm-gateway) | LLM 网关——OpenAI/Anthropic 双协议面 + 多 vendor 凭据池、限流与 token 记账 |
| [z-llm-msghub](https://github.com/z-opc-foundation/z-llm-msghub) | msgHub 中心消息路由——WebSocket 中心枢纽 + Agent 侧客户端 SDK |
| [z-mask](https://github.com/z-opc-foundation/z-mask) | 脱敏与遮蔽引擎——按敏感命中位置遮蔽，策略即数据 |
| [z-mcp](https://github.com/z-opc-foundation/z-mcp) | MCP 服务端 + 上游聚合网关——Java 8 手写协议层，Spring Boot 自动装配 |
| [z-meta](https://github.com/z-opc-foundation/z-meta) | 元数据管理平台——租户/应用/接口清单登记 + @BizModel 业务模型采集 |
| [z-mill](https://github.com/z-opc-foundation/z-mill) | 资料磨坊——全格式文档转 Markdown/JSON/纯文本/HTML（对标 MinerU） |
| [z-mist](https://github.com/z-opc-foundation/z-mist) | KMS 分布式密钥/秘密管理平台——集中加密存储 + Netty 协议 + starter 嵌入 |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 自研分布式消息队列——NameServer+Broker+Store，形状对标 RocketMQ、自有 Netty 协议 |
| [z-msg](https://github.com/z-opc-foundation/z-msg) | 多通道消息 + WebSocket 实时下发基础设施——短信/邮件/站内信/Webhook/IM 平台 |
| [z-observer](https://github.com/z-opc-foundation/z-observer) | 可观测性域——指标/日志/链路 + 告警引擎 |
| [z-opc](https://github.com/z-opc-foundation/z-opc) | 一人公司统一后端+统一前端——单 JVM all-in-one 承载协作/Agent/RPA/交易/资产等业务域与自研中间件 |
| [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) | 组织治理大脑——事实表、强制规范、跨仓工具集、新机器恢复钥匙（本清单权威持有处） |
| [z-opcs](https://github.com/z-opc-foundation/z-opcs) | 任务撮合/服务市场——用户付费发布诉求，持有者接单完成，金额自动瓜分 |
| [z-ops](https://github.com/z-opc-foundation/z-ops) | 个人公司运维平台——部署单元/镜像构建/WebShell/K8s 巡检 + Ops Agent 执行器 |
| [z-oss](https://github.com/z-opc-foundation/z-oss) | 对象存储服务——自持 local 后端 + aliyun/s3 对接层，starter 注入 OssTemplate |
| [z-product](https://github.com/z-opc-foundation/z-product) | 产品设计信息汇总仓（开发域 PM 工件） |
| [z-qa](https://github.com/z-opc-foundation/z-qa) | 步骤式 API 链路测试平台——用例/计划/执行/报告/质量看板（对标 TestRail·MeterSphere） |
| [z-rpa](https://github.com/z-opc-foundation/z-rpa) | RPA 产品皮——应用市场/需求中心（执行归 z-script，触发归 z-schedule） |
| [z-rpc](https://github.com/z-opc-foundation/z-rpc) | Java 8 + Netty RPC 框架——微内核 + SPI 插件化，形状参照 Dubbo/gRPC |
| [z-schedule](https://github.com/z-opc-foundation/z-schedule) | 分布式任务调度中枢（对标 XXL-Job）——选主 + 时间环排期，starter 嵌入宿主 |
| [z-script](https://github.com/z-opc-foundation/z-script) | 多 DSL 脚本平台 + Mock 平台——沙箱执行/版本灰度/可注册 MCP Tool，starter 内嵌 |
| [z-security](https://github.com/z-opc-foundation/z-security) | 数据安全域编排层——场景→策略→识别→脱敏→水印→审计 四段流水线编排 |
| [z-sense](https://github.com/z-opc-foundation/z-sense) | 敏感识别与分类分级引擎——数据安全线底座（规则目录 + 可插拔识别器 + 分类分级） **（本仓）** |
| [z-skill](https://github.com/z-opc-foundation/z-skill) | Skill 平台聚合器——归一化各家 Skill 形状，再按各家 API 形状发回 |
| [z-surl](https://github.com/z-opc-foundation/z-surl) | 短网址服务——短码生成/解析/命中统计，starter 一行集成 |
| [z-task](https://github.com/z-opc-foundation/z-task) | 任务协作平台——项目空间/看板/任务卡片（看板范式，对标 Trello·Teambition） |
| [z-team](https://github.com/z-opc-foundation/z-team) | 数字员工协同域——全栈独立仓，自 z-opc 毕业 |
| [z-trade](https://github.com/z-opc-foundation/z-trade) | 交易域独立仓——自 z-opc reactor 迁出 |
| [z-util](https://github.com/z-opc-foundation/z-util) | 底层 Java 工具库——89 个模块 POM，Java 8/17 口径，按需引用按需升级 |
| [z-vector](https://github.com/z-opc-foundation/z-vector) | 自研 Java 向量数据库——嵌入式+可独立部署，ANN+Payload 过滤，兼容 Qdrant REST |
| [z-voice-demo](https://github.com/z-opc-foundation/z-voice-demo) | 语音链路 demo——流式识别→kernel LLM→流式 TTS→可打断 的契约验证实现 |
| [z-watermark](https://github.com/z-opc-foundation/z-watermark) | 水印引擎——显式文字/图片隐写/盲水印，三种技术成对「打+验」 |
| [z-webide](https://github.com/z-opc-foundation/z-webide) | 企业级 WebIDE 后端——Git 仓/共享目录秒变 code-server 实例，全生命周期管理 |
| [z-wf](https://github.com/z-opc-foundation/z-wf) | 自研流程引擎——不依赖 Camunda/Flowable，定义层与运行时全自研 |

**已退役仓**（迁至 [z-trash](https://github.com/z-trash) 组织，archived 只读）：

| 仓 | 说明 |
|---|---|
| [z-agent-proxy](https://github.com/z-trash/z-agent-proxy) | 三方 coding agent 适配代理（Aider/Cursor → kernel Agent SPI），2026-10-26 退役 |
| [z-kb](https://github.com/z-trash/z-kb) | 自研知识库系统（对标 LightRAG/RAGFlow），2026-11 并入 z-cognition 认知舱后退役 |
| [z-ontology](https://github.com/z-trash/z-ontology) | 本体层（TBox/ABox/RDFS 推理），并入 z-cognition 后退役 |

另有 [z-report](https://github.com/z-opc-foundation/z-report)（报表域，2026-10-11 撤销并入 z-lc（远端已删除））。

总清单权威持有处在 [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) `008_组织规范/005_兄弟仓清单.md`。
