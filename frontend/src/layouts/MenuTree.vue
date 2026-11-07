<script setup lang="ts">
import type { MenuNode } from './useCurrentMenus'

defineProps<{ nodes: MenuNode[] }>()

const isNavigable = (node: MenuNode): boolean =>
  node.type !== 'BUTTON' && node.visible === 1 && node.status === 1

const visibleChildren = (node: MenuNode): MenuNode[] => node.children.filter(isNavigable)
</script>

<template>
  <template v-for="node in nodes.filter(isNavigable)" :key="node.id">
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
