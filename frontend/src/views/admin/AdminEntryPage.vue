<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const contestManagePermissions = [
  'contest:create',
  'contest:update',
  'edition:create',
  'edition:update',
  'edition:publish',
  'edition:milestone:create',
  'edition:milestone:update',
]

onMounted(async () => {
  await auth.ensureInitialized()
  if (auth.can('system:user:list')) {
    await router.replace('/admin/users')
    return
  }
  if (auth.can('school:affiliation:list')) {
    await router.replace('/admin/school-affiliations')
    return
  }
  if (auth.can('organization:list')) {
    await router.replace('/admin/organizations')
    return
  }
  if (auth.can('system:role:list')) {
    await router.replace('/admin/roles')
    return
  }
  if (auth.can('system:menu:list')) {
    await router.replace('/admin/menus')
    return
  }
  if (contestManagePermissions.some((permission) => auth.can(permission))) {
    await router.replace('/admin/contests')
    return
  }
  if (auth.hasRole('ADMIN')
    && (auth.can('team:approve') || auth.can('team:eligibility:manage'))) {
    await router.replace('/admin/contests')
    return
  }
  await router.replace('/403')
})
</script>

<template>
  <div class="entry-page">
    <el-skeleton :rows="4" animated />
    <p>正在进入管理后台...</p>
  </div>
</template>

<style scoped>
.entry-page {
  max-width: 720px;
  margin: 60px auto;
  color: #909399;
  text-align: center;
}
</style>
