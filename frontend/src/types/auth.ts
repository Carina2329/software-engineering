export type CaptchaScene = 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD'

export type EmailOtpScene = 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD'

export interface CaptchaChallenge {
  captcha_id: string
  image_base64: string
  expires_at: string
}

export interface EmailVerificationChallenge {
  challenge_id: string
  expires_at: string
}

export interface AuthUserSummary {
  id: string
  username: string
  real_name: string
  email?: string
}

export interface AuthTokenData {
  access_token: string
  token_type: 'Bearer'
  expires_at: string
  user: AuthUserSummary
  roles: string[]
  permissions: string[]
}

export interface UserProfile {
  id: string
  username: string
  real_name: string
  email?: string
  user_type?: 'STUDENT' | 'TEACHER' | 'REVIEWER' | 'ADMIN'
  college?: string
  major?: string
  grade?: string
  skill_tags: string[]
  roles: string[]
  data_scopes: string[]
  permissions: string[]
}

export interface UsernamePasswordLoginPayload {
  login_type: 'USERNAME_PASSWORD'
  username: string
  password: string
  captcha_id: string
  captcha_code: string
}

export interface EmailOtpLoginPayload {
  login_type: 'EMAIL_OTP'
  email: string
  challenge_id: string
  email_code: string
}

export type LoginPayload = UsernamePasswordLoginPayload | EmailOtpLoginPayload

export interface EmailVerificationCodePayload {
  email: string
  scene: EmailOtpScene
  captcha_id: string
  captcha_code: string
}

export interface RegisterPayload {
  username: string
  real_name: string
  password: string
  email: string
  challenge_id: string
  email_code: string
}

export interface RegisterResult {
  id: string
  username: string
}

export interface PasswordResetRequestPayload {
  email: string
  captcha_id: string
  captcha_code: string
}

export interface PasswordResetConfirmPayload {
  email: string
  challenge_id: string
  email_code: string
  new_password: string
}
