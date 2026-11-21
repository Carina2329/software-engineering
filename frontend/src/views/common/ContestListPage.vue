<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getContest,
  getContestErrorMessage,
  listContestEditions,
  listContests,
} from '../../api/contest'
import {
  CONTEST_LEVEL_LABELS,
  CONTEST_LEVEL_OPTIONS,
  EDITION_STATUS_LABELS,
  EDITION_VISIBILITY_LABELS,
} from '../../types/contest'
import type {
  ContestDetail,
  ContestItem,
  ContestLevel,
  ContestListQuery,
  EditionItem,
} from '../../types/contest'

const router = useRouter()
const loading = ref(false)
const contests = ref<ContestItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(9)
const filters = reactive<{ keyword: string; level: ContestLevel | '' }>({
  keyword: '',
  level: '',
})

const drawerVisible = ref(false)
const drawerLoading = ref(false)
const selectedContest = ref<ContestDetail | null>(null)
const selectedEditions = ref<EditionItem[]>([])

const loadContests = async (): Promise<void> => {
  loading.value = true
  try {
    const query: ContestListQuery = {
      page: page.value,
      size: size.value,
      sort: '-created_at',
    }
    if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
    if (filters.level) query.level = filters.level
    const data = await listContests(query)
    contests.value = data.items
    total.value = data.total
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '竞赛列表加载失败'))
  } finally {
    loading.value = false
  }
}

const openEditions = async (contestItem: ContestItem): Promise<void> => {
  drawerVisible.value = true
  drawerLoading.value = true
  selectedContest.value = null
  selectedEditions.value = []
  try {
    const [detail, editions] = await Promise.all([
      getContest(contestItem.id),
      listContestEditions(contestItem.id, { page: 1, size: 100, sort: '-year' }),
    ])
    selectedContest.value = detail
    selectedEditions.value = editions.items
  } catch (error) {
    ElMessage.error(getContestErrorMessage(error, '届次列表加载失败'))
    drawerVisible.value = false
  } finally {
    drawerLoading.value = false
  }
}

const openEdition = (edition: EditionItem): void => {
  void router.push(`/contests/${edition.id}`)
}

const search = (): void => {
  page.value = 1
  void loadContests()
}

onMounted(loadContests)
</script>

<template>
  <main class="contest-list-page">
    <header class="catalog-header">
      <div>
        <p class="eyebrow">Competition catalog</p>
        <h1>竞赛中心</h1>
        <p>查看已发布赛事与届次，确认当前报名和作品提交窗口。</p>
      </div>
      <div class="header-side">
        <el-button class="back-button" plain @click="router.push('/')">返回账号中心</el-button>
        <div class="filter-panel">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="搜索竞赛名称或主办方"
            @keyup.enter="search"
          />
          <el-select v-model="filters.level" clearable placeholder="全部级别">
            <el-option
              v-for="option in CONTEST_LEVEL_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </div>
    </header>

    <section v-loading="loading" class="contest-grid">
      <article v-for="contestItem in contests" :key="contestItem.id" class="contest-card">
        <div class="card-topline">
          <el-tag effect="plain">{{ CONTEST_LEVEL_LABELS[contestItem.level] }}</el-tag>
          <span>{{ contestItem.organizer || '主办方待补充' }}</span>
        </div>
        <h2>{{ contestItem.contest_name }}</h2>
        <p class="series-code notranslate" translate="no">{{ contestItem.series_code }}</p>
        <p class="contest-note">
          竞赛主档可包含多个历史或当前届次，进入后查看每一届独立赛程。
        </p>
        <el-button type="primary" plain @click="openEditions(contestItem)">
          查看届次
        </el-button>
      </article>
      <el-empty v-if="!loading && contests.length === 0" description="暂无已发布竞赛" />
    </section>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[9, 18, 36]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="loadContests"
        @size-change="search"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="selectedContest?.contest_name || '竞赛届次'" size="500px">
      <div v-loading="drawerLoading" class="edition-drawer">
        <p v-if="selectedContest?.intro" class="contest-intro">{{ selectedContest.intro }}</p>
        <div class="edition-list">
          <button
            v-for="edition in selectedEditions"
            :key="edition.id"
            type="button"
            class="edition-row"
            @click="openEdition(edition)"
          >
            <span>
              <strong>{{ edition.edition_name }}</strong>
              <small>
                {{ edition.year }} 年 · {{ edition.owner_org_name || '主办组织' }}
                · {{ EDITION_VISIBILITY_LABELS[edition.visibility] }}
              </small>
            </span>
            <el-tag :type="edition.status === 'DRAFT' ? 'info' : 'success'">
              {{ EDITION_STATUS_LABELS[edition.status] }}
            </el-tag>
          </button>
          <el-empty v-if="!drawerLoading && selectedEditions.length === 0" description="暂无可见届次" />
        </div>
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.contest-list-page {
  min-height: 100vh;
  padding: 30px max(24px, calc((100vw - 1180px) / 2));
  color: #173f35;
  background:
    radial-gradient(circle at 100% 0, rgba(216, 186, 112, 0.24), transparent 28rem),
    #f3f6f3;
}

.catalog-header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 28px;
  margin-bottom: 28px;
  padding: 34px;
  border-radius: 20px 20px 20px 6px;
  color: #f0f5f1;
  background:
    linear-gradient(135deg, rgba(14, 50, 41, 0.98), rgba(39, 91, 75, 0.95)),
    #173f35;
}

.eyebrow {
  margin: 0;
  color: #d8ba70;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.17em;
  text-transform: uppercase;
}

.catalog-header h1 {
  margin: 8px 0;
  font-family: Georgia, "Songti SC", serif;
  font-size: clamp(35px, 5vw, 54px);
  font-weight: 500;
}

.catalog-header p:not(.eyebrow) {
  margin: 0;
  color: rgba(240, 245, 241, 0.7);
}

.header-side {
  display: flex;
  width: min(100%, 510px);
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.back-button {
  color: #e7eee9;
  border-color: rgba(231, 238, 233, 0.34);
  background: rgba(255, 255, 255, 0.06);
}

.filter-panel {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 130px 80px;
  gap: 8px;
  width: 100%;
  min-width: min(100%, 510px);
}

.contest-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-height: 220px;
}

.contest-card {
  display: flex;
  min-height: 230px;
  padding: 24px;
  flex-direction: column;
  border: 1px solid rgba(23, 63, 53, 0.1);
  border-radius: 16px 16px 16px 5px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 30px rgba(22, 52, 44, 0.05);
}

.card-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #7e8d87;
  font-size: 11px;
}

.contest-card h2 {
  margin: 28px 0 10px;
  font-family: Georgia, "Songti SC", serif;
  font-size: 25px;
  font-weight: 500;
}

.contest-note {
  margin: 0 0 24px;
  color: #7d8b86;
  font-size: 13px;
  line-height: 1.7;
}

.series-code {
  margin: -2px 0 10px;
  color: #9a8770;
  font-family: Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.contest-card .el-button {
  align-self: flex-start;
  margin-top: auto;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 24px;
}

.edition-drawer {
  min-height: 200px;
}

.contest-intro {
  margin: 0 0 18px;
  padding: 14px;
  border-radius: 10px;
  color: #64736d;
  background: #f5f8f6;
  font-size: 13px;
  line-height: 1.7;
}

.edition-list {
  display: grid;
  gap: 10px;
}

.edition-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid #e1e8e4;
  border-radius: 10px;
  color: #173f35;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.edition-row:hover {
  border-color: #8fb4a7;
  background: #f7faf8;
}

.edition-row span {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.edition-row small {
  color: #8a9792;
}

@media (max-width: 900px) {
  .catalog-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-side {
    align-items: stretch;
  }

  .back-button {
    align-self: flex-start;
  }

  .contest-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 620px) {
  .contest-grid {
    grid-template-columns: 1fr;
  }

  .filter-panel {
    grid-template-columns: 1fr;
  }
}
</style>
