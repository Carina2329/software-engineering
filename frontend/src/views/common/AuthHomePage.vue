<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getApiErrorMessage } from '../../api/request'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loggingOut = ref(false)

const profileInitial = computed(
  () => auth.displayName.trim().slice(0, 1).toUpperCase() || '用',
)

async function handleLogout(): Promise<void> {
  loggingOut.value = true
  try {
    await auth.logout()
    ElMessage.success('已安全退出')
  } catch (error) {
    ElMessage.warning(`本地会话已清除；${getApiErrorMessage(error)}`)
  } finally {
    loggingOut.value = false
    await router.replace({ name: 'login' })
  }
}
</script>

<template>
  <main class="account-page">
    <header class="account-header">
      <div class="brand">
        <span>竞</span>
        <div>
          <strong>Campus Contest</strong>
          <small>学科竞赛协作平台</small>
        </div>
      </div>
      <el-button :loading="loggingOut" plain @click="handleLogout">退出登录</el-button>
    </header>

    <section class="welcome">
      <div class="identity-card">
        <div class="avatar">{{ profileInitial }}</div>
        <div>
          <p class="eyebrow">Authenticated session</p>
          <h1>{{ auth.displayName }}，欢迎回来</h1>
          <p class="username">@{{ auth.user?.username }}</p>
        </div>
      </div>

      <div class="session-note">
        <span class="pulse" />
        当前会话已受保护
      </div>
    </section>

    <section class="dashboard-grid">
      <article class="summary-card">
        <p class="card-label">角色</p>
        <div class="tag-list">
          <el-tag
            v-for="role in auth.roles"
            :key="role"
            effect="plain"
            type="success"
          >
            {{ role }}
          </el-tag>
          <span v-if="auth.roles.length === 0" class="empty">暂无角色信息</span>
        </div>
      </article>

      <article class="summary-card wide">
        <div class="card-heading">
          <p class="card-label">权限摘要</p>
          <span>{{ auth.permissions.length }} 项</span>
        </div>
        <div class="permission-cloud">
          <code v-for="permission in auth.permissions" :key="permission">
            {{ permission }}
          </code>
          <span v-if="auth.permissions.length === 0" class="empty">
            后端返回权限后将在这里展示
          </span>
        </div>
      </article>

      <article class="summary-card">
        <p class="card-label">账号资料</p>
        <dl>
          <div>
            <dt>账号</dt>
            <dd>{{ auth.user?.username || '-' }}</dd>
          </div>
          <div>
            <dt>姓名</dt>
            <dd>{{ auth.user?.real_name || '-' }}</dd>
          </div>
          <div>
            <dt>用户类型</dt>
            <dd>{{ auth.user?.user_type || '-' }}</dd>
          </div>
          <div>
            <dt>邮箱</dt>
            <dd>{{ auth.user?.email || '-' }}</dd>
          </div>
        </dl>
      </article>

      <article class="summary-card wide integration-card">
        <p class="eyebrow">Identity foundation ready</p>
        <h2>认证基础已就绪</h2>
        <p>
          访问令牌仅保存在当前页面内存中；刷新页面时会通过 HttpOnly
          Cookie 恢复会话。业务页面可复用当前 Auth Store 的角色、权限判断和统一
          Axios 客户端。
        </p>
      </article>
    </section>
  </main>
</template>

<style scoped>
.account-page {
  min-height: 100vh;
  padding-bottom: 64px;
  color: #18332c;
  background:
    radial-gradient(circle at 100% 0, rgba(216, 186, 112, 0.22), transparent 27rem),
    #f3f6f3;
}

.account-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 28px;
  border-bottom: 1px solid rgba(23, 63, 53, 0.11);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand > span {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 11px 11px 11px 3px;
  color: #173f35;
  background: #d8ba70;
  font-family: Georgia, "Songti SC", serif;
  font-size: 23px;
  font-weight: 700;
}

.brand strong,
.brand small {
  display: block;
}

.brand strong {
  font-family: Georgia, "Songti SC", serif;
  font-size: 17px;
}

.brand small {
  margin-top: 3px;
  color: #7c8984;
  font-size: 11px;
  letter-spacing: 0.1em;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1180px;
  margin: 0 auto;
  padding: 70px 28px 42px;
}

.identity-card {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar {
  display: grid;
  width: 82px;
  height: 82px;
  place-items: center;
  border: 1px solid rgba(23, 63, 53, 0.14);
  border-radius: 50%;
  color: #173f35;
  background: #e8dfc5;
  font-family: Georgia, "Songti SC", serif;
  font-size: 32px;
}

.eyebrow,
.card-label {
  margin: 0;
  color: #7f8c87;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.identity-card h1 {
  margin: 8px 0 4px;
  font-family: Georgia, "Songti SC", serif;
  font-size: clamp(31px, 4vw, 48px);
  font-weight: 500;
  letter-spacing: -0.04em;
}

.username {
  margin: 0;
  color: #70807a;
  font-size: 13px;
}

.session-note {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 14px;
  border: 1px solid #d4e0da;
  border-radius: 999px;
  color: #487265;
  background: rgba(255, 255, 255, 0.68);
  font-size: 12px;
}

.pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3d8a70;
  box-shadow: 0 0 0 5px rgba(61, 138, 112, 0.13);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 0.75fr 1.25fr;
  gap: 16px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 28px;
}

.summary-card {
  min-height: 170px;
  padding: 24px;
  border: 1px solid rgba(23, 63, 53, 0.1);
  border-radius: 15px 15px 15px 4px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 14px 36px rgba(22, 52, 44, 0.045);
}

.tag-list,
.permission-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-heading span {
  color: #87928e;
  font-size: 11px;
}

.permission-cloud code {
  padding: 7px 9px;
  border: 1px solid #dce4df;
  border-radius: 6px;
  color: #345b50;
  background: #f5f8f6;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.empty {
  color: #8d9894;
  font-size: 12px;
}

dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  margin: 20px 0 0;
}

dl div {
  min-width: 0;
}

dt {
  color: #8b9692;
  font-size: 11px;
}

dd {
  margin: 5px 0 0;
  overflow: hidden;
  color: #28483f;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.integration-card {
  color: #eff4ed;
  background:
    linear-gradient(135deg, rgba(10, 46, 38, 0.97), rgba(24, 67, 55, 0.94)),
    #173f35;
}

.integration-card .eyebrow {
  color: #d8ba70;
}

.integration-card h2 {
  margin: 12px 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: 27px;
  font-weight: 500;
}

.integration-card p {
  max-width: 640px;
  margin: 0;
  color: rgba(239, 244, 237, 0.66);
  font-size: 13px;
  line-height: 1.85;
}

@media (max-width: 760px) {
  .account-header,
  .welcome {
    padding-right: 18px;
    padding-left: 18px;
  }

  .welcome {
    align-items: flex-start;
    padding-top: 44px;
  }

  .avatar {
    width: 64px;
    height: 64px;
    font-size: 26px;
  }

  .session-note {
    display: none;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
    padding: 0 18px;
  }
}

@media (max-width: 480px) {
  .brand small {
    display: none;
  }

  .identity-card {
    gap: 16px;
  }

  .identity-card h1 {
    font-size: 29px;
  }
}
</style>
