<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthLayout from '../../components/auth/AuthLayout.vue'
import CaptchaField from '../../components/auth/CaptchaField.vue'
import {
  sendEmailVerificationCode,
} from '../../api/auth'
import { getApiErrorMessage } from '../../api/request'
import { useAuthStore } from '../../stores/auth'

type LoginMode = 'password' | 'email'

interface LoginForm {
  username: string
  password: string
  email: string
  captchaCode: string
  emailCode: string
}

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof CaptchaField> | null>(null)

const mode = ref<LoginMode>('password')
const submitting = ref(false)
const sendingCode = ref(false)
const captchaId = ref('')
const challengeId = ref('')
const resendSeconds = ref(0)
const notice = ref('')

let countdownTimer: ReturnType<typeof setInterval> | null = null

const form = reactive<LoginForm>({
  username: '',
  password: '',
  email: '',
  captchaCode: '',
  emailCode: '',
})

const rules = computed<FormRules<LoginForm>>(() => {
  if (mode.value === 'password') {
    return {
      username: [
        { required: true, message: '请输入账号', trigger: 'blur' },
        { min: 4, max: 32, message: '账号长度应为 4 至 32 位', trigger: 'blur' },
      ],
      password: [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 8, max: 64, message: '密码长度应为 8 至 64 位', trigger: 'blur' },
      ],
      captchaCode: [
        { required: true, message: '请输入图片验证码', trigger: 'blur' },
      ],
    }
  }

  return {
    email: [
      { required: true, message: '请输入邮箱', trigger: 'blur' },
      { type: 'email', message: '请输入有效邮箱地址', trigger: 'blur' },
    ],
    captchaCode: [
      { required: true, message: '发送验证码前请输入图片验证码', trigger: 'blur' },
    ],
    emailCode: challengeId.value
      ? [
          { required: true, message: '请输入邮件验证码', trigger: 'blur' },
          { pattern: /^\d{6}$/, message: '邮件验证码为 6 位数字', trigger: 'blur' },
        ]
      : [],
  }
})

function setMode(nextMode: LoginMode): void {
  if (mode.value === nextMode) {
    return
  }

  mode.value = nextMode
  challengeId.value = ''
  form.emailCode = ''
  form.captchaCode = ''
  formRef.value?.clearValidate()
  captchaRef.value?.refresh()
}

function startCountdown(): void {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }

  resendSeconds.value = 60
  countdownTimer = setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function sendCode(): Promise<void> {
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.warning('请先输入有效邮箱')
    return
  }
  if (!captchaId.value || !form.captchaCode.trim()) {
    ElMessage.warning('请先输入图片验证码')
    return
  }

  sendingCode.value = true
  try {
    const challenge = await sendEmailVerificationCode({
      email: form.email.trim(),
      scene: 'LOGIN',
      captcha_id: captchaId.value,
      captcha_code: form.captchaCode.trim(),
    })
    challengeId.value = challenge.challenge_id
    form.emailCode = ''
    startCountdown()
    ElMessage.success('若该邮箱可用于登录，验证码已发送')
  } catch (error) {
    challengeId.value = ''
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    await captchaRef.value?.refresh()
  }
}

function redirectTarget(): string {
  const redirect = route.query.redirect
  if (
    typeof redirect === 'string' &&
    redirect.startsWith('/') &&
    !redirect.startsWith('//')
  ) {
    return redirect
  }
  return '/'
}

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  if (mode.value === 'password' && !captchaId.value) {
    ElMessage.warning('图片验证码尚未加载，请刷新后重试')
    return
  }
  if (mode.value === 'email' && !challengeId.value) {
    ElMessage.warning('请先获取邮件验证码')
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'password') {
      await auth.login({
        login_type: 'USERNAME_PASSWORD',
        username: form.username.trim(),
        password: form.password,
        captcha_id: captchaId.value,
        captcha_code: form.captchaCode.trim(),
      })
    } else {
      await auth.login({
        login_type: 'EMAIL_OTP',
        email: form.email.trim(),
        challenge_id: challengeId.value,
        email_code: form.emailCode.trim(),
      })
    }

    ElMessage.success('登录成功')
    await router.replace(redirectTarget())
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
    if (mode.value === 'password') {
      await captchaRef.value?.refresh()
    }
  } finally {
    submitting.value = false
  }
}

watch(challengeId, () => {
  void formRef.value?.validateField('emailCode').catch(() => undefined)
})

onMounted(() => {
  if (typeof route.query.username === 'string') {
    form.username = route.query.username
  }
  if (route.query.mode === 'email') {
    mode.value = 'email'
  }
  if (route.query.registered === '1') {
    notice.value = '注册成功，请使用新账号登录。'
  } else if (route.query.reset === '1') {
    notice.value = '密码已重置，请使用新密码登录。'
  } else if (route.query.reason === 'expired') {
    notice.value = '当前会话已失效，请重新登录。'
  }
})

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<template>
  <AuthLayout
    eyebrow="Welcome back"
    title="登录工作台"
    subtitle="选择账号密码或邮箱验证码，安全进入你的协作空间。"
  >
    <div class="mode-switch" role="tablist" aria-label="登录方式">
      <button
        type="button"
        role="tab"
        :aria-selected="mode === 'password'"
        :class="{ active: mode === 'password' }"
        @click="setMode('password')"
      >
        账号密码
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="mode === 'email'"
        :class="{ active: mode === 'email' }"
        @click="setMode('email')"
      >
        邮箱验证码
      </button>
    </div>

    <div v-if="notice" class="notice">{{ notice }}</div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="submit"
    >
      <template v-if="mode === 'password'">
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="请输入学号或管理员账号"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            autocomplete="email"
            :disabled="Boolean(challengeId)"
            placeholder="name@example.edu"
          />
        </el-form-item>
      </template>

      <el-form-item label="图片验证码" prop="captchaCode">
        <CaptchaField
          ref="captchaRef"
          v-model="form.captchaCode"
          scene="LOGIN"
          @challenge="captchaId = $event"
        />
      </el-form-item>

      <template v-if="mode === 'email'">
        <div class="otp-row">
          <el-input
            v-model="form.emailCode"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            placeholder="6 位邮件验证码"
            @keyup.enter="challengeId && submit()"
          />
          <el-button
            :loading="sendingCode"
            :disabled="resendSeconds > 0"
            @click="sendCode"
          >
            {{ resendSeconds > 0 ? `${resendSeconds}s 后重发` : '发送验证码' }}
          </el-button>
        </div>
      </template>

      <el-button
        class="submit-button"
        type="primary"
        native-type="submit"
        :loading="submitting"
      >
        登录
      </el-button>
    </el-form>

    <div class="form-links">
      <span>还没有账号？<router-link to="/register">立即注册</router-link></span>
      <router-link to="/password-reset">忘记密码</router-link>
    </div>
  </AuthLayout>
</template>

<style scoped>
.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin-bottom: 24px;
  padding: 4px;
  border-radius: 9px;
  background: #eef1eb;
}

.mode-switch button {
  padding: 10px;
  border: 0;
  border-radius: 7px;
  color: #6c7873;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 160ms ease,
    background 160ms ease,
    box-shadow 160ms ease;
}

.mode-switch button.active {
  color: #173a31;
  background: #fff;
  box-shadow: 0 2px 10px rgba(20, 48, 41, 0.08);
}

.notice {
  margin: -6px 0 18px;
  padding: 10px 12px;
  border-left: 3px solid #5d8576;
  color: #345c50;
  background: #edf5f1;
  font-size: 13px;
  line-height: 1.6;
}

.otp-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin: -8px 0 18px;
}

.submit-button {
  width: 100%;
  margin-top: 6px;
  --el-button-bg-color: #173f35;
  --el-button-border-color: #173f35;
  --el-button-hover-bg-color: #215748;
  --el-button-hover-border-color: #215748;
}

.form-links {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 22px;
  color: #7b8782;
  font-size: 12px;
}

.form-links a {
  color: #346f5e;
  font-weight: 600;
  text-decoration: none;
}

.form-links a:hover {
  text-decoration: underline;
}

:deep(.el-form-item__label) {
  color: #354b44;
  font-size: 12px;
  font-weight: 600;
}

:deep(.el-input__wrapper) {
  border-radius: 7px;
  box-shadow: 0 0 0 1px #dce2dc inset;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #4f7f6e inset;
}

@media (max-width: 480px) {
  .form-links {
    flex-direction: column;
    gap: 10px;
  }

  .otp-row {
    grid-template-columns: 1fr;
  }
}
</style>
