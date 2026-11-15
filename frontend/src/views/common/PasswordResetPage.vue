<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AuthLayout from '../../components/auth/AuthLayout.vue'
import CaptchaField from '../../components/auth/CaptchaField.vue'
import {
  confirmPasswordReset,
  requestPasswordReset,
} from '../../api/auth'
import { getApiErrorMessage } from '../../api/request'
import { useAuthStore } from '../../stores/auth'

interface ResetForm {
  email: string
  captchaCode: string
  emailCode: string
  newPassword: string
  confirmPassword: string
}

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof CaptchaField> | null>(null)

const form = reactive<ResetForm>({
  email: '',
  captchaCode: '',
  emailCode: '',
  newPassword: '',
  confirmPassword: '',
})

const captchaId = ref('')
const challengeId = ref('')
const requesting = ref(false)
const confirming = ref(false)
const resendSeconds = ref(0)
const requested = computed(() => Boolean(challengeId.value))

let countdownTimer: ReturnType<typeof setInterval> | null = null

const rules = computed<FormRules<ResetForm>>(() => ({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效邮箱地址', trigger: 'blur' },
  ],
  captchaCode: [
    { required: true, message: '请输入图片验证码', trigger: 'blur' },
  ],
  emailCode: requested.value
    ? [
        { required: true, message: '请输入邮件验证码', trigger: 'blur' },
        { pattern: /^\d{6}$/, message: '邮件验证码为 6 位数字', trigger: 'blur' },
      ]
    : [],
  newPassword: requested.value
    ? [
        { required: true, message: '请设置新密码', trigger: 'blur' },
        { min: 8, max: 64, message: '密码长度应为 8 至 64 位', trigger: 'blur' },
      ]
    : [],
  confirmPassword: requested.value
    ? [{ required: true, message: '请再次输入新密码', trigger: 'blur' }]
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

async function requestCode(): Promise<void> {
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    ElMessage.warning('请先输入有效邮箱')
    return
  }
  if (!captchaId.value || !form.captchaCode.trim()) {
    ElMessage.warning('请先输入图片验证码')
    return
  }

  requesting.value = true
  try {
    const challenge = await requestPasswordReset({
      email: form.email.trim(),
      captcha_id: captchaId.value,
      captcha_code: form.captchaCode.trim(),
    })
    challengeId.value = challenge.challenge_id
    form.emailCode = ''
    startCountdown()
    ElMessage.success('请求已受理，请稍后查收；若账号存在，请填写收到的重置验证码')
  } catch (error) {
    challengeId.value = ''
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    await captchaRef.value?.refresh()
  }
}

async function confirmReset(): Promise<void> {
  if (!formRef.value || !challengeId.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  confirming.value = true
  try {
    await confirmPasswordReset({
      email: form.email.trim(),
      challenge_id: challengeId.value,
      email_code: form.emailCode.trim(),
      new_password: form.newPassword,
    })
    auth.clearSessionState()
    ElMessage.success('密码已重置，请重新登录')
    await router.replace({
      name: 'login',
      query: {
        username: '',
        reset: '1',
      },
    })
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    confirming.value = false
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
    eyebrow="Account recovery"
    title="找回密码"
    subtitle="通过邮箱验证码确认身份。重置成功后，其他已登录会话会一并失效。"
  >
    <div class="step-line">
      <span class="step active">1</span>
      <span>验证邮箱</span>
      <i />
      <span class="step" :class="{ active: requested }">2</span>
      <span>设置新密码</span>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      @submit.prevent="confirmReset"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model="form.email"
          autocomplete="email"
          :disabled="requested"
          placeholder="请输入注册邮箱"
        />
      </el-form-item>

      <template v-if="!requested">
        <el-form-item label="图片验证码" prop="captchaCode">
          <CaptchaField
            ref="captchaRef"
            v-model="form.captchaCode"
            scene="RESET_PASSWORD"
            @challenge="captchaId = $event"
          />
        </el-form-item>

        <el-button
          class="submit-button"
          type="primary"
          :loading="requesting"
          @click="requestCode"
        >
          发送重置验证码
        </el-button>
      </template>

      <template v-else>
        <div class="sent-note">
          请求已受理，请稍后查收。若该邮箱已注册，请填写收到的 6 位验证码；受理不代表邮件已经送达。
          <button
            v-if="resendSeconds <= 0"
            type="button"
            @click="challengeId = ''; formRef?.clearValidate()"
          >
            重新申请
          </button>
          <span v-else>（{{ resendSeconds }}s 后可重新申请）</span>
        </div>

        <el-form-item label="邮件验证码" prop="emailCode">
          <el-input
            v-model="form.emailCode"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
            placeholder="6 位验证码"
          />
        </el-form-item>

        <div class="password-grid">
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="至少 8 位"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="再次输入新密码"
            />
          </el-form-item>
        </div>

        <el-button
          class="submit-button"
          type="primary"
          native-type="submit"
          :loading="confirming"
        >
          确认重置
        </el-button>
      </template>
    </el-form>

    <p class="login-link">
      想起密码了？<router-link to="/login">返回登录</router-link>
    </p>
  </AuthLayout>
</template>

<style scoped>
.step-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -4px 0 24px;
  color: #87918d;
  font-size: 12px;
}

.step {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  border: 1px solid #d3dcd7;
  border-radius: 50%;
  color: #8a9691;
  font-size: 11px;
}

.step.active {
  border-color: #2f6a58;
  color: #fff;
  background: #2f6a58;
}

.step-line i {
  width: 34px;
  height: 1px;
  background: #d8dfdb;
}

.sent-note {
  margin: -4px 0 18px;
  padding: 10px 12px;
  border-left: 3px solid #d8ba70;
  color: #63583a;
  background: #faf5e7;
  font-size: 12px;
  line-height: 1.7;
}

.sent-note button {
  display: block;
  margin-top: 4px;
  padding: 0;
  border: 0;
  color: #356e5e;
  background: transparent;
  font-weight: 600;
  cursor: pointer;
}

.password-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
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
  .password-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
