<script setup lang="ts">
import { ref } from 'vue'
import { fetchPing } from '../../api/test'

const msg = ref('还没请求')
const loading = ref(false)

const ping = async () => {
  loading.value = true
  try {
    msg.value = await fetchPing()
  } catch (e) {
    msg.value = '请求失败：' + (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="test-page">
    <h1>contest-agent-platform 脚手架</h1>
    <el-button type="primary" :loading="loading" @click="ping">测试后端连通性</el-button>
    <p>{{ msg }}</p>
  </div>
</template>

<style scoped>
.test-page {
  max-width: 480px;
  margin: 80px auto;
  text-align: center;
}
</style>
