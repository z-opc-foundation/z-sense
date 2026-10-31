# 总体设计

## 1. 这仓解决什么问题

业务文本进来，需要知道**哪一段是敏感的、有多敏感**——这是安全线一切动作的前置判定。
z-sense 只回答这两个问题，**不改写内容、不决定遮不遮、不拦不拦**：

```
text ──▶ SenseService.detect(text) ──▶ SenseResult { hits:[{ruleId, category, level, start, end, fragment}], maxLevel }
```

边界一句话：**只产出「位置 + 类别 + 分级」三元组**，下游拿位置去做事。

| 组件 | 管什么 | 不管什么 |
|---|---|---|
| **z-sense** | 规则目录、识别器 SPI、命中定位、分类 × 分级、重叠消解 | 改写文本、脱敏算法、密钥、拦截决策、留痕 |
| z-mask | 按 z-sense 的命中位置遮蔽 | 识别 |
| z-biz-security | 编排（要不要拦、走哪条策略） | 识别与遮蔽的实现 |

## 2. 模块与依赖方向

```
z-sense/
├── z-sense-core/                 纯 Java 领域层，可脱离 Spring 单测
│   └── src/main/java/com/zifang/z/sense/core/
│       ├── domain/    SenseRule · SenseResult · SensitiveHit · SensitiveCategory · SensitiveLevel
│       ├── spi/       SensitiveDetector（唯一对外扩展点）
│       ├── service/   SenseService（门面）· RuleSensitiveDetector（规则实现）
│       └── support/   BuiltinRules（内置规则 + 校验器 + 白/黑名单）
├── z-sense-starter/              引擎装配（M2 #23 拆出）：宿主嵌入用，无 web / health / 数据源
│   └── src/main/
│       ├── java/com/zifang/z/sense/starter/config/
│       │                  SenseStarterAutoConfiguration（只装配 SenseService）
│       └── resources/META-INF/spring/
│                          org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── z-sense-web/                  运行壳：数据源 + health + HTTP 面（依赖 starter）
    └── src/main/java/com/zifang/z/sense/web/
        ├── config/    SenseAutoConfiguration（数据源 + health）· SenseModuleDataSource · SenseHealthIndicator
        └── api/       SenseController（/z-team/api/sense/detect）· SenseHealthController（/api/sense/health）
```

四条硬约束：

1. `z-sense-core` **不依赖任何同族仓**，也不依赖 Spring —— 底座方向：z-mask 依赖本仓，反向不依赖；
2. 对外扩展只走 `spi/SensitiveDetector` 一个口子 —— 换识别实现（如 NER 模型）不需要改门面；
3. `z-sense-web` 只做装配与暴露，**不放业务逻辑** —— 保证 core 的单测就是全量口径证明；
4. **引擎装配只有一处**（在 starter）：`z-sense-web` 不重复定义 `SenseService`，
   靠依赖 starter 拿到它 —— 重复定义会靠 `@ConditionalOnMissingBean` 侥幸让位，但读代码的人会以为有两处真装配。

包名 `com.zifang.z.sense.*` 与 Maven groupId `io.github.yuku123` 刻意解耦（照 z-cognition 先例）。

## 3. 关键口径

### 3.1 形状 + 语义双闸

正则只能保证「形状像」，判定为敏感还需要语义校验；**校验不过就放弃命中**——
漏了只是少遮一处，误了会把正常业务数据当敏感处理，后者危险得多。

| 数据 | 形状闸 | 语义闸 |
|---|---|---|
| 身份证 | `\d{17}[0-9Xx]` | GB 11643 校验位（加权因子 + 码表） |
| 手机号 | `1[3-9]\d{9}` | 号段：中间四位不全 0 |
| 银行卡 | `\d{13,19}` | Luhn |
| 邮箱 | 标准正则 | 无（形状即足够） |

实现位置：`support/BuiltinRules#valid*`。

### 3.2 类别 × 分级正交

- **类别** `SensitiveCategory`（10 值）= 下游**选脱敏策略的键**；
- **分级** `SensitiveLevel`（L1 公开 / L2 内部 / L3 敏感 / L4 核心）= **防护强度**，决定拦不拦、留多久。

两者正交，**不许**退化成「L3 身份证」这种把两维焊死的写法。

### 3.3 弱规则收紧（L2 宁漏不误）

中文姓名 2~4 字太泛，`BuiltinRules#validPersonName` 叠三闸：姓氏白名单（427 去重首字）
+ 通用词黑名单（48 词）+ 组合词黑名单（33 词）。
不加收紧时「手机」「卡号」「都是本人」会被判成人名，实测污染业务数据。

### 3.4 命中必带位置 → 识别一次、脱敏一次

`SensitiveHit` 携带 `[start, end)`，下游按位置替换而不是重扫一遍：
重扫既双倍开销，又可能因两次识别之间规则集热更而**口径打架**。

### 3.5 重叠消解

同一位置被多条规则命中 ⇒ 留**级别更高者**；同级 ⇒ 留**先注册者**。
消解发生在 `SenseResult#addHit` 内，保证结果幂等稳定。
⚠ 下游 z-mask 对传入命中的再消解必须用**同一算法**，否则位置会对不上导致错位遮蔽。

### 3.6 门面不抛异常

`SenseService` 把内部异常吞成**空结果**——识别是链路边路，不能拖垮主链路。
调用方判空即可，异常口径不外溢。

## 4. 当前实现边界（M1）

| 事项 | 状态 | 说明 |
|---|---|---|
| 规则集来源 | 代码内置 | `BuiltinRules`，**不读库**；规则表持久化与热更是 M2 |
| 版本 | 不发制品 | 坐标 `io.github.yuku123:z-sense:1.0.0`，parent `z-boot-parent:1.2.0`（Java 17 / SB 3.3.13 火车） |
| DDL | 本仓无 | 本仓不建表；编排侧建表见 z-biz-security `_doc/002_deploy/init.sql` |

## 5. 相关文档

- 复跑与验收读数：[`_doc/005_testing/001_验收复跑.md`](_doc/005_testing/001_验收复跑.md)
- 端口 / 环境变量 / 启动方式：[`_doc/002_deploy/00-deploy.md`](_doc/002_deploy/00-deploy.md)
- 立项口径：lead [`002_项目需求/001_特性需求/015_z-biz-security_数据安全线`](../../../z-opc-foundation-lead/002_项目需求/001_特性需求/015_z-biz-security_数据安全线/)
