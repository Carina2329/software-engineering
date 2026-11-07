import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { CSRF_HEADER_NAME, readCsrfToken } from '../auth/csrf'
import { notifyAuthExpired } from '../auth/events'
import { clearAuthToken, getAccessToken, setAuthToken } from '../auth/tokenManager'
import type { AuthTokenData } from '../types/auth'

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  trace_id: string
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

interface ApiRequest {
  request<T = unknown>(config: AxiosRequestConfig): Promise<T>
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown, D = unknown>(
    url: string,
    data?: D,
    config?: AxiosRequestConfig<D>,
  ): Promise<T>
  put<T = unknown, D = unknown>(
    url: string,
    data?: D,
    config?: AxiosRequestConfig<D>,
  ): Promise<T>
  patch<T = unknown, D = unknown>(
    url: string,
    data?: D,
    config?: AxiosRequestConfig<D>,
  ): Promise<T>
}

export class ApiError extends Error {
  readonly code: number | null
  readonly status: number | null
  readonly traceId: string | null
  readonly details: unknown

  constructor(
    message: string,
    options: {
      code?: number | null
      status?: number | null
      traceId?: string | null
      details?: unknown
    } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = options.code ?? null
    this.status = options.status ?? null
    this.traceId = options.traceId ?? null
    this.details = options.details
  }
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true,
})

let refreshPromise: Promise<AuthTokenData> | null = null

const UNSAFE_METHODS = new Set(['post', 'put', 'patch', 'delete'])

function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<ApiEnvelope<unknown>>
  return typeof candidate.code === 'number' && typeof candidate.message === 'string'
}

function isAuthTokenData(value: unknown): value is AuthTokenData {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Partial<AuthTokenData>
  return (
    typeof candidate.access_token === 'string' &&
    typeof candidate.expires_at === 'string' &&
    Array.isArray(candidate.roles) &&
    Array.isArray(candidate.permissions)
  )
}

function unwrapEnvelope<T>(value: unknown): T {
  if (!isEnvelope(value)) {
    throw new ApiError('服务端返回了无法识别的响应')
  }

  if (value.code !== 0) {
    throw new ApiError(value.message || '请求失败', {
      code: value.code,
      traceId: value.trace_id,
      details: value.data,
    })
  }

  return value.data as T
}

function rememberTokenFromResponse(url: string | undefined, value: unknown): void {
  if (
    (url === '/auth/login' || url === '/auth/refresh') &&
    isAuthTokenData(value)
  ) {
    setAuthToken(value.access_token, value.expires_at, value)
  }
}

function shouldRefreshRequest(url: string | undefined): boolean {
  if (!url) {
    return false
  }

  return ![
    '/auth/login',
    '/auth/refresh',
    '/auth/logout',
    '/auth/captcha',
    '/auth/email-verification-codes',
    '/auth/register',
    '/auth/password-reset/request',
    '/auth/password-reset/confirm',
  ].some((publicPath) => url.endsWith(publicPath))
}

function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  const axiosError = error as AxiosError<ApiEnvelope<unknown>>
  const response = axiosError.response
  const envelope = response?.data

  if (isEnvelope(envelope)) {
    return new ApiError(envelope.message || '请求失败', {
      code: envelope.code,
      status: response?.status,
      traceId: envelope.trace_id,
      details: envelope.data,
    })
  }

  if (response?.status === 403) {
    return new ApiError('安全校验已失效，请刷新页面后重试', {
      code: null,
      status: response.status,
    })
  }

  if (response?.status === 429) {
    return new ApiError('操作过于频繁，请稍后再试', {
      code: 40001,
      status: response.status,
    })
  }

  if (!response) {
    return new ApiError('网络连接失败，请检查网络后重试')
  }

  return new ApiError(`请求失败（HTTP ${response.status}）`, {
    status: response.status,
  })
}

async function performRefresh(): Promise<AuthTokenData> {
  const csrfToken = readCsrfToken()
  const response = await axios.post<ApiEnvelope<AuthTokenData>>(
    '/api/v1/auth/refresh',
    undefined,
    {
      timeout: 15000,
      withCredentials: true,
      headers: csrfToken ? { [CSRF_HEADER_NAME]: csrfToken } : undefined,
    },
  )

  const tokenData = unwrapEnvelope<AuthTokenData>(response.data)
  if (!isAuthTokenData(tokenData)) {
    throw new ApiError('刷新响应缺少访问令牌')
  }

  setAuthToken(tokenData.access_token, tokenData.expires_at, tokenData)
  return tokenData
}

export async function refreshAccessToken(notifyOnFailure = true): Promise<AuthTokenData> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }

  try {
    return await refreshPromise
  } catch (error) {
    clearAuthToken()
    if (notifyOnFailure) {
      notifyAuthExpired()
    }
    throw normalizeError(error)
  }
}

export function getApiErrorMessage(error: unknown): string {
  return normalizeError(error).message
}

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }

  const method = config.method?.toLowerCase()
  if (method && UNSAFE_METHODS.has(method)) {
    const csrfToken = readCsrfToken()
    if (csrfToken) {
      config.headers.set(CSRF_HEADER_NAME, csrfToken)
    }
  }

  return config
})

http.interceptors.response.use(
  (response) => {
    const unwrapped = unwrapEnvelope<unknown>(response.data)
    rememberTokenFromResponse(response.config.url, unwrapped)
    return unwrapped as AxiosResponse
  },
  async (error: unknown) => {
    const axiosError = error as AxiosError<ApiEnvelope<unknown>>
    const config = axiosError.config as RetryableRequestConfig | undefined

    if (
      axiosError.response?.status === 401 &&
      config &&
      !config._retry &&
      shouldRefreshRequest(config.url)
    ) {
      config._retry = true
      try {
        await refreshAccessToken()
      } catch {
        return Promise.reject(normalizeError(error))
      }

      try {
        return await http.request(config)
      } catch (replayError) {
        return Promise.reject(normalizeError(replayError))
      }
    }

    return Promise.reject(normalizeError(error))
  },
)

const request = http as unknown as ApiRequest

export default request
