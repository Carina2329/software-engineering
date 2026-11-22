<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listSchools } from '../../api/organization'
import { getErrorMessage } from '../../api/adminRbac'
import type { SchoolItem, SchoolListQuery } from '../../types/organization'
import { useContextualReturn } from '../../composables/useContextualReturn'

const { returnLabel, handleReturn } = useContextualReturn({
  defaultLabel: '返回账号中心',
  defaultFallback: '/',
  matchesPrevious: (path) => path === '/',
})
const loading = ref(false)
const schools = ref<SchoolItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({
  keyword: '',
  province_code: '',
})

const loadSchools = async (): Promise<void> => {
  loading.value = true
  try {
    const query: SchoolListQuery = { page: page.value, size: size.value }
    if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
    if (filters.province_code.trim()) query.province_code = filters.province_code.trim()
    const data = await listSchools(query)
    schools.value = data.items
    total.value = data.total
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '学校列表加载失败'))
  } finally {
    loading.value = false
  }
}

const handleSearch = (): void => {
  page.value = 1
  void loadSchools()
}

onMounted(loadSchools)
</script>

<template>
  <main class="school-page">
    <header class="school-header">
      <div>
        <h1>学校目录</h1>
        <p>仅展示已启用且省份有效的学校</p>
      </div>
      <el-button plain @click="handleReturn">← {{ returnLabel }}</el-button>
    </header>

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="省份编码">
          <el-input v-model="filters.province_code" placeholder="如 510000" clearable />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="学校名称或编码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="schools" border row-key="id">
        <el-table-column prop="school_code" label="学校编码" min-width="160" />
        <el-table-column prop="school_name" label="学校名称" min-width="220" />
        <el-table-column prop="province_code" label="省份编码" width="120" />
        <el-table-column prop="province_name" label="省份" min-width="130" />
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadSchools"
          @size-change="handleSearch"
        />
      </div>
    </el-card>
  </main>
</template>

<style scoped>
.school-page {
  min-height: 100vh;
  padding: 28px;
  background: #f5f7fa;
}

.school-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1180px;
  margin: 0 auto 18px;
}

.school-header h1 {
  margin: 0;
  color: #303133;
  font-size: 24px;
}

.school-header p {
  margin: 6px 0 0;
  color: #909399;
  font-size: 13px;
}

.filter-form {
  margin-bottom: 4px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
