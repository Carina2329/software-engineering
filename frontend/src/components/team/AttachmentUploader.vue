<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTeamErrorMessage, uploadRegistrationAttachments } from '../../api/team'
import type { RegistrationAttachment } from '../../types/team'

interface UploadedItem { file_id: string; file_name: string; file_type: string | null }
const props = withDefaults(defineProps<{ modelValue: string[]; attachments?: RegistrationAttachment[]; disabled?: boolean }>(), {
  attachments: () => [], disabled: false,
})
const emit = defineEmits<{ 'update:modelValue': [fileIds: string[]] }>()
const inputRef = ref<HTMLInputElement>()
const uploading = ref(false)
const items = ref<UploadedItem[]>([])
const fileIds = computed(() => items.value.map((item) => item.file_id))

watch([() => props.modelValue, () => props.attachments], () => {
  const names = new Map(props.attachments.map((item) => [item.file_id, item]))
  items.value = props.modelValue.map((fileId) => {
    const existing = names.get(fileId)
    return existing ? { file_id: existing.file_id, file_name: existing.file_name, file_type: existing.file_type }
      : { file_id: fileId, file_name: `文件 ${fileId}`, file_type: null }
  })
}, { immediate: true, deep: true })

function chooseFiles(): void { if (!props.disabled && !uploading.value) inputRef.value?.click() }
async function handleFiles(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  uploading.value = true
  try {
    const response = await uploadRegistrationAttachments(files)
    const success = response.results.filter((item) => item.success)
    const failed = response.results.filter((item) => !item.success)
    for (const item of success) {
      if (!items.value.some((current) => current.file_id === item.file_id)) {
        items.value.push({ file_id: item.file_id, file_name: item.file_name, file_type: null })
      }
    }
    emit('update:modelValue', fileIds.value)
    if (success.length) ElMessage.success(`已上传 ${success.length} 个附件`)
    if (failed.length) ElMessage.warning(failed.map((item) => `${item.file_name}：${item.error || '上传失败'}`).join('；'))
  } catch (error) { ElMessage.error(getTeamErrorMessage(error, '附件上传失败')) }
  finally { uploading.value = false }
}
function remove(fileId: string): void {
  if (props.disabled) return
  items.value = items.value.filter((item) => item.file_id !== fileId)
  emit('update:modelValue', fileIds.value)
}
</script>

<template>
  <section class="uploader">
    <div class="uploader-heading">
      <div><span class="eyebrow">REGISTRATION FILES</span><h3>报名附件</h3></div>
      <el-button type="primary" plain :loading="uploading" :disabled="disabled" @click="chooseFiles">选择文件</el-button>
      <input ref="inputRef" class="native-input" type="file" multiple @change="handleFiles" />
    </div>
    <div v-if="items.length" class="file-list">
      <div v-for="item in items" :key="item.file_id" class="file-row">
        <div><strong>{{ item.file_name }}</strong><small>FILE ID {{ item.file_id }}</small></div>
        <el-button link type="danger" :disabled="disabled" @click="remove(item.file_id)">移除</el-button>
      </div>
    </div>
    <el-empty v-else description="请上传报名材料" :image-size="56" />
    <p class="tip">只保存上传成功后的 file_id，提交报名时会再次由服务端校验归属、类型和大小。</p>
  </section>
</template>

<style scoped>
.uploader { display: grid; gap: 12px; }
.uploader-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.eyebrow { color: #6d8f84; font-size: 10px; letter-spacing: .16em; }
h3 { margin: 4px 0 0; font-size: 20px; color: #18352e; }
.native-input { display: none; }
.file-list { display: grid; gap: 8px; }
.file-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border: 1px solid #e1e9e5; border-radius: 11px; background: #fbfdfc; }
.file-row div { display: grid; gap: 3px; }
.file-row small { color: #8a9a94; font-family: Consolas, monospace; font-size: 10px; }
.tip { margin: 0; color: #778980; font-size: 12px; line-height: 1.6; }
</style>
