<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '../api/adminRbac'
import MenuTree from './MenuTree.vue'
import { useCurrentMenus } from './useCurrentMenus'

const route = useRoute()
const router = useRouter()
const { menuTree, loading, ensureCurrentMenus } = useCurrentMenus()

onMounted(() => {
  ensureCurrentMenus().catch((error: unknown) => {
    ElMessage.error(getErrorMessage(error, '菜单加载失败'))
  })
})
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <div class="admin-brand">学科竞赛管理后台</div>
      <el-menu :default-active="route.path" router class="admin-menu">
        <MenuTree :nodes="menuTree" />
      </el-menu>
    </el-aside>
    <el-main v-loading="loading" class="admin-main">
      <div class="admin-toolbar">
        <el-button plain @click="router.push('/')">返回账号中心</el-button>
      </div>
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-aside {
  background: #ffffff;
  border-right: 1px solid #e4e7ed;
}

.admin-brand {
  height: 56px;
  line-height: 56px;
  padding: 0 20px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #e4e7ed;
}

.admin-menu {
  border-right: none;
}

.admin-main {
  background: #f5f7fa;
}

.admin-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
</style>
