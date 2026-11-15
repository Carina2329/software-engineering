import { readonly, ref, shallowReadonly, shallowRef } from 'vue'
import type { AuthTokenData } from '../types/auth'

const tokenState = ref('')
const expiresAtState = ref<number | null>(null)
const tokenDataState = shallowRef<AuthTokenData | null>(null)

function parseExpiresAt(value: string | number): number | null {
  const parsed = typeof value === 'number' ? value : Date.parse(value)
  return Number.isFinite(parsed) ? parsed : null
}

export function setAuthToken(
  token: string,
  expiresAt: string | number,
  tokenData?: AuthTokenData,
): void {
  tokenState.value = token
  expiresAtState.value = parseExpiresAt(expiresAt)
  tokenDataState.value = tokenData ?? null
}

export function clearAuthToken(): void {
  tokenState.value = ''
  expiresAtState.value = null
  tokenDataState.value = null
}

export const accessToken = readonly(tokenState)
export const accessTokenExpiresAt = readonly(expiresAtState)
export const authTokenData = shallowReadonly(tokenDataState)

export function getAccessToken(): string {
  return tokenState.value
}
