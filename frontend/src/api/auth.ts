import request from './request'
import type {
  AuthTokenData,
  CaptchaChallenge,
  CaptchaScene,
  EmailVerificationChallenge,
  EmailVerificationCodePayload,
  LoginPayload,
  PasswordResetConfirmPayload,
  PasswordResetRequestPayload,
  RegisterPayload,
  RegisterResult,
  UserProfile,
} from '../types/auth'

export function fetchCaptcha(scene: CaptchaScene): Promise<CaptchaChallenge> {
  return request.get('/auth/captcha', { params: { scene } })
}

export function login(payload: LoginPayload): Promise<AuthTokenData> {
  return request.post('/auth/login', payload)
}

export function sendEmailVerificationCode(
  payload: EmailVerificationCodePayload,
): Promise<EmailVerificationChallenge> {
  return request.post('/auth/email-verification-codes', payload)
}

export function register(payload: RegisterPayload): Promise<RegisterResult> {
  return request.post('/auth/register', payload)
}

export function requestPasswordReset(
  payload: PasswordResetRequestPayload,
): Promise<EmailVerificationChallenge> {
  return request.post('/auth/password-reset/request', payload)
}

export function confirmPasswordReset(
  payload: PasswordResetConfirmPayload,
): Promise<void> {
  return request.post('/auth/password-reset/confirm', payload)
}

export function fetchCurrentUser(): Promise<UserProfile> {
  return request.get('/users/me')
}

export function logout(): Promise<void> {
  return request.post('/auth/logout', undefined, {
    headers: {
      'Idempotency-Key': crypto.randomUUID(),
    },
  })
}
