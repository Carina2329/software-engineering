export const AUTH_EXPIRED_EVENT = 'auth:expired'

export function notifyAuthExpired(): void {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
  }
}
