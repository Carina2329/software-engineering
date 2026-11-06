<script setup lang="ts">
import type { MenuNode } from './useCurrentMenus'

defineProps<{ nodes: MenuNode[] }>()

const visibleChildren = (node: MenuNode): MenuNode[] => node.children.filter((child) => child.type !== 'BUTTON')
</script>

<template>
  <template v-for="node in nodes" :key="node.id">
    <el-sub-menu v-if="node.type === 'DIRECTORY' && visibleChildren(node).length > 0" :index="node.id">
      <template #title>
        <span>{{ node.name }}</span>
      </template>
      <MenuTree :nodes="visibleChildren(node)" />
    </el-sub-menu>
    <el-menu-item v-else-if="node.type === 'MENU'" :index="node.path ?? node.id">
      <span>{{ node.name }}</span>
    </el-menu-item>
  </template>
</template>
