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
