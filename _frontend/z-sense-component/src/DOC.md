# @yuku123/z-sense-component

z-sense（敏感信息识别底座）可复用前端组件（lead 005 §9 component 共享）。

## 使用

```js
import { configureSense } from '@yuku123/z-sense-component'
configureSense({ apiBase: '' })   // 走同源网关时可省略
```

## pages 入口（路由清单）

```js
import { routes, menuItems, appMeta } from '@yuku123/z-sense-component/pages'
```

| 路径 | 页面 | 说明 |
|---|---|---|
| `/z-sense/home` | HomePage | 首页着陆 |
| `/z-sense/detect` | DetectPage | 敏感识别试跑（POST `/z-team/api/sense/detect`） |
| `/z-sense/docs` | ComponentDocPage | 组件文档 |

## 后端契约

- `POST /z-team/api/sense/detect`，body `{text}`，返回 `{ok, hitCount, sensitive, maxLevel, categoryCounts, elapsedMillis, hits[]}`
- `GET /api/sense/health`（健康检查）

`.` 入口保持 props-only（lead 005 §5）；fetch 只出现在 `./pages` 的默认装配里。
