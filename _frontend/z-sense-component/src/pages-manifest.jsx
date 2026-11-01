import { HomeOutlined, ScanOutlined, ReadOutlined } from '@ant-design/icons'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import ComponentDocPage from './pages/ComponentDoc.jsx'
import DetectPage from './pages/DetectPage.jsx'

/** 菜单 + 路由清单（lead 005 §8.3：主壳按本清单重组）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-sense 识别底座', short: 'z-sense' }

export const menuItems = [
    { key: '/z-sense/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-sense/detect', label: '敏感识别', icon: <ScanOutlined /> },
    { key: '/z-sense/docs', label: '组件文档', icon: <ReadOutlined /> },
]

export const routes = [
    { path: '/z-sense/home', Component: HomePage },
    { path: '/z-sense/detect', Component: DetectPage },
    { path: '/z-sense/docs', Component: ComponentDocPage },
]

export { HomePage, LoginPage, ComponentDocPage }
