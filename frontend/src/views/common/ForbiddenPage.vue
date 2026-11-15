<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = (): void => {
  void router.push('/')
}

const goBack = (): void => {
  router.back()
}
</script>

<template>
  <div class="forbidden-page">
    <el-result icon="error" title="403" sub-title="当前账号无权访问该页面，如需开通请联系管理员。">
      <template #extra>
        <el-button @click="goBack">返回上一页</el-button>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}
</style>
