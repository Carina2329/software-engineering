<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage, getMenuTree } from '../../api/adminRbac'
import { MENU_TYPE_LABELS } from '../../types/rbac'
import type { PermissionMenuType, PermissionResource } from '../../types/rbac'
import { useCurrentMenus } from '../../layouts/useCurrentMenus'

const { ensureCurrentMenus } = useCurrentMenus()
const loading = ref(false)
const resources = ref<PermissionResource[]>([])
const treeProps = { label: 'menu_name', children: 'children' }

const menuTypeLabel = (value: PermissionMenuType): string => MENU_TYPE_LABELS[value] ?? '未知'

const menuTypeTag = (value: PermissionMenuType): 'info' | 'primary' | 'warning' => {
  if (value === 'DIRECTORY') return 'info'
  if (value === 'BUTTON') return 'warning'
  return 'primary'
}

const nodeCount = computed(() => {
  const count = (nodes: PermissionResource[]): number =>
    nodes.reduce((total, node) => total + 1 + count(node.children ?? []), 0)
  return count(resources.value)
})

const loadResources = async (): Promise<void> => {
  loading.value = true
  try {
    resources.value = await getMenuTree()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '权限资源加载失败'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadResources()
  void ensureCurrentMenus().catch(() => undefined)
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">权限资源</span>
          <span class="card-hint">共 {{ nodeCount }} 个节点，目录 / 菜单 / 按钮统一由后端维护</span>
        </div>
      </template>

      <el-tree v-loading="loading" :data="resources" node-key="id" :props="treeProps" :default-expand-all="true">
        <template #default="{ data }">
          <div class="tree-node">
            <span class="tree-name">{{ data.menu_name }}</span>
            <el-tag size="small" :type="menuTypeTag(data.menu_type)">{{ menuTypeLabel(data.menu_type) }}</el-tag>
            <span v-if="data.perms" class="tree-perms">{{ data.perms }}</span>
            <el-tag v-if="data.visible === 0" size="small" type="info" effect="plain">隐藏</el-tag>
            <el-tag v-if="data.status === 0" size="small" type="danger" effect="plain">停用</el-tag>
          </div>
        </template>
      </el-tree>

      <el-empty v-if="!loading && resources.length === 0" description="暂无权限资源" />
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-hint {
  font-size: 12px;
  color: #909399;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-name {
  color: #303133;
}

.tree-perms {
  font-size: 12px;
  color: #909399;
}
</style>
