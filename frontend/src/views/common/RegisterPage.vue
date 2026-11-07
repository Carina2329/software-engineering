<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthLayout from '../../components/auth/AuthLayout.vue'
import CaptchaField from '../../components/auth/CaptchaField.vue'
import {
  register,
  sendEmailVerificationCode,
} from '../../api/auth'
import { getApiErrorMessage } from '../../api/request'

interface RegisterForm {
  username: string
  realName: string
  password: string
  confirmPassword: string
  email: string
  captchaCode: string
  emailCode: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof CaptchaField> | null>(null)

const form = reactive<RegisterForm>({
  username: '',
  realName: '',
  password: '',
  confirmPassword: '',
  email: '',
  captchaCode: '',
  emailCode: '',
})

const captchaId = ref('')
const challengeId = ref('')
const sendingCode = ref(false)
const submitting = ref(false)
const resendSeconds = ref(0)

let countdownTimer: ReturnType<typeof setInterval> | null = null

const rules = computed<FormRules<RegisterForm>>(() => ({
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{4,32}$/,
      message: '账号需为 4 至 32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { max: 32, message: '姓名不能超过 32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度应为 8 至 64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效邮箱地址', trigger: 'blur' },
  ],
  captchaCode: challengeId.value
    ? []
    : [
        { required: true, message: '发送验证码前请输入图片验证码', trigger: 'blur' },
      ],
  emailCode: challengeId.value
    ? [
        { required: true, message: '请输入邮件验证码', trigger: 'blur' },
        { pattern: /^\d{6}$/, message: '邮件验证码为 6 位数字', trigger: 'blur' },
      ]
    : [],
}))

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
      scene: 'REGISTER',
      captcha_id: captchaId.value,
      captcha_code: form.captchaCode.trim(),
    })
    challengeId.value = challenge.challenge_id
    form.emailCode = ''
    startCountdown()
    ElMessage.success('若该邮箱可注册，验证码已发送')
  } catch (error) {
    challengeId.value = ''
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    if (!challengeId.value) {
      await captchaRef.value?.refresh()
    }
  }
}

async function prepareResend(): Promise<void> {
  challengeId.value = ''
  form.emailCode = ''
  await nextTick()
  await captchaRef.value?.refresh()
}

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (!challengeId.value) {
    ElMessage.warning('请先获取并填写邮件验证码')
    return
  }

  submitting.value = true
  try {
    const result = await register({
      username: form.username.trim(),
      real_name: form.realName.trim(),
      password: form.password,
      email: form.email.trim(),
      challenge_id: challengeId.value,
      email_code: form.emailCode.trim(),
    })
    ElMessage.success('注册成功')
    await router.replace({
      name: 'login',
      query: {
        username: result.username,
        registered: '1',
      },
    })
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<template>
  <AuthLayout
    eyebrow="Create account"
    title="创建参赛账号"
    subtitle="完成邮箱验证后即可加入团队。新账号默认使用学生角色。"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="submit"
    >
      <div class="field-grid">
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="4-32 位字母、数字或下划线"
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="form.realName"
            autocomplete="name"
            placeholder="请输入真实姓名"
          />
        </el-form-item>
      </div>

      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="form.email"
          autocomplete="email"
          :disabled="Boolean(challengeId)"
          placeholder="name@example.edu"
        />
      </el-form-item>

      <div class="field-grid">
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="至少 8 位"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="再次输入密码"
          />
        </el-form-item>
      </div>

      <el-form-item v-if="!challengeId" label="图片验证码" prop="captchaCode">
        <CaptchaField
          ref="captchaRef"
          v-model="form.captchaCode"
          scene="REGISTER"
          @challenge="captchaId = $event"
        />
      </el-form-item>

      <div class="otp-row">
        <el-form-item label="邮件验证码" prop="emailCode">
          <el-input
            v-model="form.emailCode"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            placeholder="发送后填写 6 位验证码"
          />
        </el-form-item>
        <el-button
          v-if="!challengeId"
          class="send-button"
          :loading="sendingCode"
          :disabled="resendSeconds > 0"
          @click="sendCode"
        >
          {{ resendSeconds > 0 ? `${resendSeconds}s 后发送` : '发送验证码' }}
        </el-button>
        <el-button
          v-else
          class="send-button"
          :disabled="resendSeconds > 0"
          @click="prepareResend"
        >
          {{ resendSeconds > 0 ? `${resendSeconds}s 后重发` : '重新获取' }}
        </el-button>
      </div>

      <el-button
        class="submit-button"
        type="primary"
        native-type="submit"
        :loading="submitting"
      >
        注册账号
      </el-button>
    </el-form>

    <p class="login-link">
      已有账号？<router-link to="/login">返回登录</router-link>
    </p>
  </AuthLayout>
</template>

<style scoped>
.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.otp-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px;
}

.otp-row .send-button {
  margin-bottom: 18px;
}

.submit-button {
  width: 100%;
  margin-top: 4px;
  --el-button-bg-color: #173f35;
  --el-button-border-color: #173f35;
  --el-button-hover-bg-color: #215748;
  --el-button-hover-border-color: #215748;
}

.login-link {
  margin: 22px 0 0;
  color: #7b8782;
  font-size: 12px;
  text-align: center;
}

.login-link a {
  color: #346f5e;
  font-weight: 600;
  text-decoration: none;
}

.login-link a:hover {
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

@media (max-width: 560px) {
  .field-grid,
  .otp-row {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .otp-row .send-button {
    width: 100%;
    margin-bottom: 18px;
  }
}
</style>
