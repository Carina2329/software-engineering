<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { MENU_TYPE_LABELS } from '../../../types/rbac'
import type { PermissionMenuType, PermissionResource } from '../../../types/rbac'

const props = defineProps<{ modelValue: string[]; resources: PermissionResource[]; readonly?: boolean }>()

interface TreeInstance {
  setCheckedKeys: (keys: string[]) => void
  getCheckedKeys: (leafOnly?: boolean) => string[]
  getHalfCheckedKeys: () => string[]
}

const treeRef = ref<TreeInstance | null>(null)
const checkStrictly = ref(true)

const treeProps = computed(() => ({
  label: 'menu_name',
  children: 'children',
  disabled: () => props.readonly === true,
}))

const menuTypeLabel = (value: PermissionMenuType): string => MENU_TYPE_LABELS[value] ?? '未知'

const collectResourceMap = (nodes: PermissionResource[], map: Map<string, PermissionResource>): void => {
  for (const node of nodes) {
    map.set(node.id, node)
    collectResourceMap(node.children ?? [], map)
  }
}

const isFullyGranted = (node: PermissionResource, granted: Set<string>, map: Map<string, PermissionResource>): boolean => {
  if (!granted.has(node.id)) return false
  return (node.children ?? []).every((child) => isFullyGranted(map.get(child.id) ?? child, granted, map))
}

/**
 * 回显时只设置叶子节点或整棵子树都授权的父节点，避免父节点 ID 自动勾选未授权子节点。
 * 用户交互时恢复父子联动。保存时提交“选中 + 半选”节点。
 */
const safeCheckedKeys = (keys: string[]): string[] => {
  const map = new Map<string, PermissionResource>()
  collectResourceMap(props.resources, map)
  const granted = new Set(keys)
  return keys.filter((id) => {
    const node = map.get(id)
    if (!node) return true
    const children = node.children ?? []
    if (children.length === 0) return true
    return children.every((child) => isFullyGranted(child, granted, map))
  })
}

const syncChecked = async (keys: string[]): Promise<void> => {
  if (!treeRef.value) return
  checkStrictly.value = true
  await nextTick()
  treeRef.value.setCheckedKeys(safeCheckedKeys(keys))
  await nextTick()
  checkStrictly.value = false
}

const getSubmitKeys = (): string[] => {
  const checked = treeRef.value?.getCheckedKeys(false) ?? []
  const halfChecked = treeRef.value?.getHalfCheckedKeys() ?? []
  return Array.from(new Set([...checked, ...halfChecked]))
}

defineExpose({ getSubmitKeys })

onMounted(() => {
  void syncChecked(props.modelValue)
})

watch(
  () => props.modelValue,
  (value) => {
    void syncChecked(value)
  },
)

watch(
  () => props.resources,
  () => {
    void syncChecked(props.modelValue)
  },
  { deep: true },
)
</script>

<template>
  <el-tree
    ref="treeRef"
    :data="resources"
    node-key="id"
    :props="treeProps"
    show-checkbox
    :check-strictly="checkStrictly"
    :default-expand-all="true"
  >
    <template #default="{ data }">
      <div class="tree-node">
        <span class="tree-name">{{ data.menu_name }}</span>
        <el-tag size="small" type="info">{{ menuTypeLabel(data.menu_type) }}</el-tag>
        <span v-if="data.perms" class="tree-perms">{{ data.perms }}</span>
      </div>
    </template>
  </el-tree>
</template>

<style scoped>
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
