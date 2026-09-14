import request from './request'

/** 脚手架连通性测试，正式开发后可删除 */
export const fetchPing = (): Promise<string> => request.get('/test/ping')
