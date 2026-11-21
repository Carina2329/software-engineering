const SHANGHAI_TIME_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

export function formatShanghaiTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }

  const time = new Date(value)
  if (Number.isNaN(time.getTime())) {
    return value
  }

  return SHANGHAI_TIME_FORMATTER.format(time).replace(/\//g, '-')
}
