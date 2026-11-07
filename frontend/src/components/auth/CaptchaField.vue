<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchCaptcha } from '../../api/auth'
import { getApiErrorMessage } from '../../api/request'
import type { CaptchaScene } from '../../types/auth'

const props = defineProps<{
  scene: CaptchaScene
}>()

const emit = defineEmits<{
  challenge: [captchaId: string]
}>()

const code = defineModel<string>({ required: true })

const imageUrl = ref('')
const loading = ref(false)
let requestSequence = 0

function toImageUrl(imageBase64: string): string {
  return imageBase64.startsWith('data:image/')
    ? imageBase64
    : `data:image/png;base64,${imageBase64}`
}

async function refresh(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  emit('challenge', '')
  code.value = ''

  try {
    const challenge = await fetchCaptcha(props.scene)
    if (sequence !== requestSequence) {
      return
    }
    imageUrl.value = toImageUrl(challenge.image_base64)
    emit('challenge', challenge.captcha_id)
  } catch (error) {
    if (sequence === requestSequence) {
      imageUrl.value = ''
      ElMessage.error(getApiErrorMessage(error))
    }
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
    }
  }
}

watch(
  () => props.scene,
  () => {
    void refresh()
  },
  { immediate: true },
)

defineExpose({ refresh })
</script>

<template>
  <div class="captcha-field">
    <el-input
      v-model="code"
      maxlength="4"
      autocomplete="off"
      placeholder="输入图中字符"
      aria-label="图片验证码"
    />
    <button
      type="button"
      class="captcha-image"
      :disabled="loading"
      title="点击刷新验证码"
      @click="refresh"
    >
      <img v-if="imageUrl" :src="imageUrl" alt="图片验证码" />
      <span v-else>{{ loading ? '加载中' : '刷新' }}</span>
    </button>
  </div>
</template>

<style scoped>
.captcha-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  gap: 10px;
  width: 100%;
}

.captcha-image {
  display: grid;
  height: 40px;
  padding: 0;
  overflow: hidden;
  place-items: center;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  color: var(--el-text-color-secondary);
  background: #f3f0e7;
  cursor: pointer;
}

.captcha-image:hover {
  border-color: #5d8576;
}

.captcha-image:disabled {
  cursor: wait;
}

.captcha-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
