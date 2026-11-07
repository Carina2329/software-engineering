import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import {
  fetchCurrentUser,
  login as loginApi,
  logout as logoutApi,
} from '../api/auth'
import { refreshAccessToken } from '../api/request'
import {
  accessToken,
  accessTokenExpiresAt,
  authTokenData,
  clearAuthToken,
} from '../auth/tokenManager'
import type {
  AuthTokenData,
  LoginPayload,
  UserProfile,
} from '../types/auth'

const PROACTIVE_REFRESH_LEAD_MS = 60_000
const MIN_REFRESH_DELAY_MS = 5_000

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserProfile | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const initialized = ref(false)
  const restoring = ref(false)

  let initializeTask: Promise<void> | null = null
  let refreshTimer: ReturnType<typeof setTimeout> | null = null

  const isAuthenticated = computed(() => accessToken.value.length > 0)
  const displayName = computed(
    () => user.value?.real_name || user.value?.username || '未登录',
  )

  function clearRefreshTimer(): void {
    if (refreshTimer) {
      clearTimeout(refreshTimer)
      refreshTimer = null
    }
  }

  function scheduleRefresh(expiresAt: number | null): void {
    clearRefreshTimer()
    if (!isAuthenticated.value || !expiresAt) {
      return
    }

    const delay = Math.max(
      expiresAt - Date.now() - PROACTIVE_REFRESH_LEAD_MS,
      MIN_REFRESH_DELAY_MS,
    )
    refreshTimer = setTimeout(() => {
      void refreshSession().catch(() => {
        clearSessionState()
      })
    }, delay)
  }

  function profileFromToken(tokenData: AuthTokenData): UserProfile {
    return {
      ...tokenData.user,
      skill_tags: [],
      roles: [...tokenData.roles],
      data_scopes: [],
      permissions: [...tokenData.permissions],
    }
  }

  function applyTokenData(tokenData: AuthTokenData): void {
    user.value = profileFromToken(tokenData)
    roles.value = [...tokenData.roles]
    permissions.value = [...tokenData.permissions]
  }

  async function refreshProfile(optional: boolean): Promise<void> {
    try {
      const profile = await fetchCurrentUser()
      user.value = {
        ...profile,
        skill_tags: profile.skill_tags ?? [],
        roles: profile.roles ?? roles.value,
        data_scopes: profile.data_scopes ?? [],
        permissions: profile.permissions ?? permissions.value,
      }
      roles.value = [...user.value.roles]
      permissions.value = [...user.value.permissions]
    } catch (error) {
      if (!optional) {
        throw error
      }
    }
  }

  function clearSessionState(): void {
    clearRefreshTimer()
    clearAuthToken()
    user.value = null
    roles.value = []
    permissions.value = []
  }

  async function refreshSession(): Promise<void> {
    if (!isAuthenticated.value) {
      return
    }

    const tokenData = await refreshAccessToken()
    applyTokenData(tokenData)
    await refreshProfile(true)
  }

  async function ensureInitialized(): Promise<void> {
    if (initialized.value) {
      return
    }

    if (initializeTask) {
      return initializeTask
    }

    initializeTask = (async () => {
      restoring.value = true
      try {
        const tokenData = await refreshAccessToken(false)
        applyTokenData(tokenData)
        await refreshProfile(true)
      } catch {
        clearSessionState()
      } finally {
        restoring.value = false
        initialized.value = true
        initializeTask = null
      }
    })()

    return initializeTask
  }

  async function login(payload: LoginPayload): Promise<AuthTokenData> {
    const tokenData = await loginApi(payload)
    applyTokenData(tokenData)
    await refreshProfile(true)
    initialized.value = true
    return tokenData
  }

  async function logout(): Promise<void> {
    let requestError: unknown = null
    try {
      await logoutApi()
    } catch (error) {
      requestError = error
    } finally {
      clearSessionState()
    }

    if (requestError) {
      throw requestError
    }
  }

  function can(permission: string): boolean {
    return permissions.value.includes(permission)
  }

  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  watch(
    accessTokenExpiresAt,
    (expiresAt) => {
      scheduleRefresh(expiresAt)
    },
    { immediate: true },
  )

  watch(authTokenData, (tokenData) => {
    if (tokenData) {
      applyTokenData(tokenData)
    }
  })

  return {
    user,
    roles,
    permissions,
    accessToken,
    token: accessToken,
    accessTokenExpiresAt,
    initialized,
    restoring,
    isAuthenticated,
    displayName,
    ensureInitialized,
    refreshSession,
    login,
    logout,
    clearSessionState,
    refreshProfile,
    can,
    hasRole,
  }
})
