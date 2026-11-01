/**
 * z-sense API client：走 /z-team/api/sense/** 面（敏感识别：detect）。
 */
import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({baseURL: '', tokenKey: 'zsense_token'})

export default request

export function configureSense(config) {
    if (config && config.apiBase !== undefined) {
        request.defaults.baseURL = config.apiBase
    }
}

export const senseApi = {
    detect: (text) => request.post('/z-team/api/sense/detect', {text}),
    health: () => request.get('/api/sense/health'),
}
