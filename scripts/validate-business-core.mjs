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
assert(openapi.info.version === '1.3.0', 'OpenAPI version must be 1.3.0')
assert(openapi['x-business-baseline']?.source_branch === 'develop@63c2a14', 'business baseline must use PR12 develop')

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
  'student_no',
  'title',
  'version'
].sort()), 'profile patch fields do not match v1.3.0')

const editionCreate = openapi.paths['/contests/{contest_id}/editions'].post.requestBody.content['application/json'].schema.properties
assert(editionCreate.team_size_mode?.enum?.join(',') === 'FIXED,RANGE', 'team_size_mode enum is missing')
assert(editionCreate.max_teams_per_teacher?.default === 3, 'teacher limit default is missing')

const editionDetail = openapi.paths['/editions/{edition_id}'].get.responses['200'].content['application/json'].schema.properties.data.properties
for (const field of ['registration_open', 'submission_open', 'read_only', 'review_start', 'publish_start', 'publish_end']) {
  assert(editionDetail[field], `edition detail is missing ${field}`)
}

const foundationSql = read('docs/sql/2026-11-15-business-core-foundation.sql')
assert(!/CREATE\s+TABLE/i.test(foundationSql), 'business foundation must not create tables')
for (const field of ['team_size_mode', 'max_teams_per_teacher', 'topic_mode', 'topic_change_limit']) {
  assert(foundationSql.includes(field), `foundation SQL is missing ${field}`)
}

const devSeed = read('docs/sql/2026-11-15-business-core-dev-seed.sql')
for (const mode of ['PRESET_UNLIMITED', 'PRESET_LIMITED', 'SELF_PROPOSED', 'NO_TOPIC']) {
  assert(devSeed.includes(mode), `development seed is missing ${mode}`)
}

const summary = read('docs/api/接口汇总.md')
assert(summary.includes('文档版本：v1.3.0'), 'interface summary version is not v1.3.0')
assert(summary.includes('archive/接口汇总-v1.2.1.md'), 'v1.2.1 archive link is missing')

console.log(`business-core contract valid: ${operationIds.size} operations, ${refs.length} refs`)
