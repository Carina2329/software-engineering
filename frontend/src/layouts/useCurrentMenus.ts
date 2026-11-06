import { ref } from 'vue'
import { getCurrentMenus } from '../api/adminRbac'
import type { PermissionMenuType, PermissionResource } from '../types/rbac'

export interface MenuNode {
  id: string
  parentId: string | null
  name: string
  type: PermissionMenuType
  path: string | null
  perms: string | null
  sortNo: number
  children: MenuNode[]
}

const menuTree = ref<MenuNode[]>([])
const permissions = ref<string[]>([])
const loading = ref(false)

let loaded = false
let loadingPromise: Promise<void> | null = null

const sortTree = (nodes: MenuNode[]): void => {
  nodes.sort((left, right) => left.sortNo - right.sortNo || left.name.localeCompare(right.name))
  for (const node of nodes) sortTree(node.children)
}

const collectPermissions = (nodes: MenuNode[], output: string[] = []): string[] => {
  for (const node of nodes) {
    if (node.perms && !output.includes(node.perms)) output.push(node.perms)
    collectPermissions(node.children, output)
  }
  return output
}

/**
 * EP-008 与 EP-076 已统一为嵌套 children: PermissionResource[]，menu_type 为字符串。
 * 按钮节点（menu_type = BUTTON, visible = 0）保留用于 deriving 按钮权限，导航渲染时由 MenuTree 过滤。
 */
export const normalizeCurrentMenus = (nodes: PermissionResource[]): MenuNode[] => {
  const result: MenuNode[] = nodes.map((node) => ({
    id: node.id,
    parentId: node.parent_id ?? null,
    name: node.menu_name,
    type: node.menu_type,
    path: node.path ?? null,
    perms: node.perms ?? null,
    sortNo: node.sort_no ?? 0,
    children: normalizeCurrentMenus(node.children ?? []),
  }))
  sortTree(result)
  return result
}

export const ensureCurrentMenus = async (force = false): Promise<void> => {
  if (loaded && !force) return
  if (loadingPromise) return loadingPromise
  loading.value = true
  loadingPromise = getCurrentMenus()
    .then((nodes) => {
      menuTree.value = normalizeCurrentMenus(nodes)
      permissions.value = collectPermissions(menuTree.value)
      loaded = true
    })
    .finally(() => {
      loading.value = false
      loadingPromise = null
    })
  return loadingPromise
}

/** 菜单与按钮只做展示控制，真实授权仍由后端 @PreAuthorize 完成 */
export const hasPermission = (perm: string): boolean => permissions.value.includes(perm)

export const useCurrentMenus = () => ({
  menuTree,
  permissions,
  loading,
  ensureCurrentMenus,
  hasPermission,
})
