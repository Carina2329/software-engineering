import { onBeforeUnmount, ref } from 'vue'
import { ApiError, getApiErrorMessage } from '../../../api/request'
import { createDashboardTicket, getDashboardOverview } from '../../../api/dashboard'
import type {
  DashboardConnectionState,
  DashboardOverview,
  DashboardWebSocketMessage,
} from '../../../types/dashboard'

const HEARTBEAT_TIMEOUT_MS = 75_000
const RECONNECT_BASE_DELAY_MS = 1_000
const RECONNECT_MAX_DELAY_MS = 30_000

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isDashboardOverview(value: unknown): value is DashboardOverview {
  if (!isRecord(value)) {
    return false
  }

  return (
    typeof value.generated_at === 'string' &&
    typeof value.data_source === 'string' &&
    isRecord(value.summary) &&
    isRecord(value.contest_operations) &&
    Array.isArray(value.province_schools) &&
    Array.isArray(value.user_roles) &&
    Array.isArray(value.upcoming_deadlines)
  )
}

function isDashboardWebSocketMessage(
  value: unknown,
): value is DashboardWebSocketMessage {
  if (!isRecord(value) || value.version !== 1) {
    return false
  }

  if (value.type === 'CONNECTED') {
    return value.data === null && typeof value.generated_at === 'string'
  }

  if (value.type === 'DASHBOARD_OVERVIEW') {
    return (
      typeof value.generated_at === 'string' &&
      isDashboardOverview(value.data)
    )
  }

  return false
}

export function useDashboardSocket() {
  const snapshot = ref<DashboardOverview | null>(null)
  const connectionState = ref<DashboardConnectionState>('idle')
  const errorMessage = ref<string | null>(null)

  let active = false
  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectAttempt = 0
  let lastMessageAt = 0

  function clearReconnectTimer() {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function stopHeartbeat() {
    if (heartbeatTimer !== null) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function closeSocket() {
    if (!socket) {
      return
    }

    const current = socket
    socket = null
    current.onopen = null
    current.onmessage = null
    current.onerror = null
    current.onclose = null

    if (
      current.readyState === WebSocket.CONNECTING ||
      current.readyState === WebSocket.OPEN
    ) {
      current.close(1000, '页面已关闭')
    }
  }

  function handleApiFailure(error: unknown, initial: boolean): boolean {
    if (error instanceof ApiError && error.status === 403) {
      connectionState.value = 'forbidden'
      errorMessage.value = '当前账号没有平台大屏访问权限'
      return false
    }

    errorMessage.value = getApiErrorMessage(
      error,
      initial ? '大屏数据加载失败' : '大屏数据刷新失败',
    )

    if (!snapshot.value) {
      connectionState.value = 'error'
    } else {
      connectionState.value = 'reconnecting'
    }

    return false
  }

  async function loadSnapshot(initial: boolean): Promise<boolean> {
    if (initial) {
      connectionState.value = 'loading'
    }

    try {
      const data = await getDashboardOverview()
      if (!active) {
        return false
      }

      snapshot.value = data
      errorMessage.value = null
      return true
    } catch (error) {
      if (!active) {
        return false
      }

      return handleApiFailure(error, initial)
    }
  }

  function scheduleReconnect() {
    if (!active || connectionState.value === 'forbidden' || reconnectTimer) {
      return
    }

    const delay = Math.min(
      RECONNECT_MAX_DELAY_MS,
      RECONNECT_BASE_DELAY_MS * 2 ** Math.min(reconnectAttempt, 5),
    )
    reconnectAttempt += 1
    connectionState.value = 'reconnecting'

    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      void reconnect()
    }, delay)
  }

  async function reconnect() {
    if (!active || connectionState.value === 'forbidden') {
      return
    }

    const loaded = await loadSnapshot(false)
    const currentState = connectionState.value as DashboardConnectionState
    if (!active || currentState === 'forbidden') {
      return
    }

    if (!loaded) {
      scheduleReconnect()
      return
    }

    await openSocket()
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (
        active &&
        connectionState.value === 'connected' &&
        Date.now() - lastMessageAt > HEARTBEAT_TIMEOUT_MS
      ) {
        connectionState.value = 'reconnecting'
        closeSocket()
        scheduleReconnect()
      }
    }, 5_000)
  }

  function handleSocketMessage(event: MessageEvent<unknown>) {
    if (!active || typeof event.data !== 'string') {
      return
    }

    lastMessageAt = Date.now()

    let payload: unknown
    try {
      payload = JSON.parse(event.data)
    } catch {
      return
    }

    if (!isDashboardWebSocketMessage(payload)) {
      return
    }

    connectionState.value = 'connected'
    reconnectAttempt = 0
    errorMessage.value = null

    if (payload.type === 'DASHBOARD_OVERVIEW') {
      snapshot.value = payload.data
    }
  }

  async function openSocket() {
    if (!active) {
      return
    }

    clearReconnectTimer()
    closeSocket()
    connectionState.value = 'connecting'
    errorMessage.value = null

    try {
      const ticket = await createDashboardTicket()
      if (!active) {
        return
      }

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = `${protocol}//${window.location.host}/ws/dashboard?ticket=${encodeURIComponent(ticket.ticket)}`
      const nextSocket = new WebSocket(url)
      socket = nextSocket
      lastMessageAt = Date.now()

      nextSocket.onopen = () => {
        if (active && socket === nextSocket) {
          lastMessageAt = Date.now()
          startHeartbeat()
        }
      }

      nextSocket.onmessage = handleSocketMessage

      nextSocket.onclose = (event) => {
        if (socket === nextSocket) {
          socket = null
        }
        stopHeartbeat()

        if (!active) {
          return
        }

        if (event.code === 4403) {
          connectionState.value = 'forbidden'
          errorMessage.value = '当前账号没有平台大屏访问权限'
          return
        }

        connectionState.value = 'reconnecting'
        scheduleReconnect()
      }
    } catch (error) {
      if (!active) {
        return
      }

      handleApiFailure(error, snapshot.value === null)
      scheduleReconnect()
    }
  }

  async function start() {
    if (active) {
      return
    }

    active = true
    reconnectAttempt = 0
    errorMessage.value = null
    const loaded = await loadSnapshot(true)

    if (!active || !loaded) {
      return
    }

    await openSocket()
  }

  async function retry() {
    if (!active) {
      active = true
    }

    reconnectAttempt = 0
    errorMessage.value = null
    const loaded = await loadSnapshot(true)

    if (loaded) {
      await openSocket()
    }
  }

  function stop() {
    active = false
    clearReconnectTimer()
    stopHeartbeat()
    closeSocket()
    connectionState.value = 'idle'
  }

  onBeforeUnmount(stop)

  return {
    snapshot,
    connectionState,
    errorMessage,
    start,
    retry,
    stop,
  }
}
