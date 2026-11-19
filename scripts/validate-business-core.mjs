import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const fail = (message) => {
  throw new Error(message)
}
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8')
const assert = (condition, message) => {
  if (!condition) fail(message)
}

const openapi = JSON.parse(read('docs/api/openapi-v1.json'))
assert(openapi.info.version === '1.4.2', 'OpenAPI version must be 1.4.2')
assert(openapi['x-business-baseline']?.source_branch === 'develop@63c2a14', 'business baseline must use PR12 develop')
assert(openapi['x-database-tables'] === 27, 'OpenAPI database table count must be 27')
assert(openapi['x-endpoint-count'] === 93, 'OpenAPI endpoint count must be 93')

const operationIds = new Set()
const endpointIds = new Set()
const refs = []
const visit = (value) => {
  if (Array.isArray(value)) {
    value.forEach(visit)
    return
  }
  if (!value || typeof value !== 'object') return
  if (typeof value.$ref === 'string') refs.push(value.$ref)
  visit(Object.values(value))
}
visit(openapi)

for (const item of Object.values(openapi.paths)) {
  for (const operation of Object.values(item)) {
    if (!operation || typeof operation !== 'object') continue
    if (operation.operationId) {
      assert(!operationIds.has(operation.operationId), `duplicate operationId: ${operation.operationId}`)
      operationIds.add(operation.operationId)
    }
    if (operation['x-endpoint-id']) {
      assert(!endpointIds.has(operation['x-endpoint-id']), `duplicate endpoint id: ${operation['x-endpoint-id']}`)
      endpointIds.add(operation['x-endpoint-id'])
    }
  }
}

for (const ref of refs) {
  if (!ref.startsWith('#/')) fail(`external ref is not expected in baseline: ${ref}`)
  const target = ref.slice(2).split('/').reduce((current, part) => current?.[part.replaceAll('~1', '/').replaceAll('~0', '~')], openapi)
  assert(target !== undefined, `unresolved ref: ${ref}`)
}

const profilePatch = openapi.paths['/users/me'].patch.requestBody.content['application/json'].schema.properties
assert(JSON.stringify(Object.keys(profilePatch).sort()) === JSON.stringify([
  'college',
  'grade',
  'major',
  'real_name',
  'school_id',
  'student_no',
  'title',
  'version'
].sort()), 'profile patch fields do not match v1.4.0')

const profileGet = openapi.paths['/users/me'].get.responses['200'].content['application/json'].schema.properties.data.properties
for (const field of ['school_id', 'school_name', 'province_code', 'school_verification_status', 'school_review_reason']) {
  assert(profileGet[field], `profile response is missing ${field}`)
}

const contestCreate = openapi.paths['/contests'].post.requestBody.content['application/json'].schema
assert(contestCreate.properties.series_code, 'contest series_code is missing')

const editionCreate = openapi.paths['/contests/{contest_id}/editions'].post.requestBody.content['application/json'].schema.properties
assert(editionCreate.team_size_mode?.enum?.join(',') === 'FIXED,RANGE', 'team_size_mode enum is missing')
assert(editionCreate.max_teams_per_teacher?.default === 3, 'teacher limit default is missing')
for (const field of ['owner_org_id', 'level', 'school_id', 'province_code', 'visibility', 'entry_mode', 'cross_school_team_allowed']) {
  assert(editionCreate[field], `edition create is missing ${field}`)
}

const editionDetail = openapi.paths['/editions/{edition_id}'].get.responses['200'].content['application/json'].schema.properties.data.properties
for (const field of [
  'registration_open', 'submission_open', 'read_only', 'review_start', 'publish_start', 'publish_end',
  'owner_org_id', 'owner_org_name', 'level', 'school_id', 'province_code', 'visibility', 'entry_mode',
  'cross_school_team_allowed'
]) {
  assert(editionDetail[field], `edition detail is missing ${field}`)
}

const contestDetail = openapi.paths['/contests/{contest_id}'].get.responses['200'].content['application/json'].schema.properties.data
assert(contestDetail.properties.version?.type === 'integer', 'contest detail is missing version')
assert(contestDetail.required?.includes('version'), 'contest detail version must be required')

const milestoneItem = openapi.paths['/editions/{edition_id}/milestones'].get.responses['200'].content['application/json'].schema.properties.data.items
assert(milestoneItem.properties.version?.type === 'integer', 'milestone item is missing version')
assert(milestoneItem.required?.includes('version'), 'milestone version must be required')

const foundationSql = read('docs/sql/2026-11-15-business-core-foundation.sql')
assert(!/CREATE\s+TABLE/i.test(foundationSql), 'business foundation must not create tables')
for (const field of ['team_size_mode', 'max_teams_per_teacher', 'topic_mode', 'topic_change_limit']) {
  assert(foundationSql.includes(field), `foundation SQL is missing ${field}`)
}

const devSeed = read('docs/sql/2026-11-15-business-core-dev-seed.sql')
for (const mode of ['PRESET_UNLIMITED', 'PRESET_LIMITED', 'SELF_PROPOSED', 'NO_TOPIC']) {
  assert(devSeed.includes(mode), `development seed is missing ${mode}`)
}

const multitenantSql = read('docs/sql/2026-11-18-multitenant-foundation.sql')
for (const token of [
  'CREATE TABLE organization',
  'school_id',
  'scope_type',
  'scope_id',
  'school_verification_status',
  'school_review_reason',
  'school_reviewed_by',
  'school_reviewed_at',
  'owner_org_id',
  'province_code',
  'visibility',
  'entry_mode'
]) {
  assert(multitenantSql.includes(token), `multitenant SQL is missing ${token}`)
}

const multitenantSeed = read('docs/sql/2026-11-18-multitenant-dev-seed.sql')
for (const token of ['SC-SCHOOL-A', 'SC-SCHOOL-B', 'HN-SCHOOL-D', 'SERIES-STAT-MODEL', '2026年四川省统计建模省赛']) {
  assert(multitenantSeed.includes(token), `multitenant dev seed is missing ${token}`)
}

const provinceSeed = read('docs/sql/2026-11-19-cn-province-dictionary-seed.sql')
assert(provinceSeed.includes("'CN_PROVINCE'"), 'province dictionary type is missing')
assert(provinceSeed.includes("'510000'"), 'Sichuan province code is missing')
assert(provinceSeed.includes("'430000'"), 'Hunan province code is missing')

for (const path of [
  '/schools',
  '/organizations',
  '/organizations/{organization_id}',
  '/role-assignments',
  '/role-assignments/{assignment_id}',
  '/school-affiliations',
  '/school-affiliations/{user_id}'
]) {
  assert(openapi.paths[path], `OpenAPI path is missing ${path}`)
}

assert(
  openapi.paths['/organizations/{organization_id}'].patch.responses['409'],
  'EP-089 must declare 409 conflict'
)

const authContract = read('docs/auth-rbac/contract.md')
assert(authContract.includes('文档版本：0.3.1'), 'auth contract version is not 0.3.1')
assert(
  authContract.includes('`scope_type=GLOBAL`、`scope_id=0` 的基础角色'),
  'EP-007 base-role boundary is missing'
)

const summary = read('docs/api/接口汇总.md')
assert(summary.includes('文档版本：v1.4.2'), 'interface summary version is not v1.4.2')
assert(summary.includes('archive/接口汇总-v1.4.0.md'), 'v1.4.0 archive link is missing')
assert(summary.includes('archive/接口汇总-v1.3.1.md'), 'v1.3.1 archive link is missing')
assert(summary.includes('archive/接口汇总-v1.3.0.md'), 'v1.3.0 archive link is missing')
assert(summary.includes('archive/接口汇总-v1.2.1.md'), 'v1.2.1 archive link is missing')

console.log(`business-core contract valid: ${operationIds.size} operations, ${refs.length} refs`)
