import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

interface ContextualReturnOptions {
  defaultLabel: string
  defaultFallback: string
  matchesPrevious?: (path: string) => boolean
}

/**
 * 保留用户进入页面前的上下文：
 * - 从后台进入时统一显示“返回后台管理”并回到来源页；
 * - 其他已知上下文使用页面提供的返回文案；
 * - 直接打开页面时使用安全兜底地址。
 */
export function useContextualReturn(options: ContextualReturnOptions) {
  const router = useRouter()
  const previousPath = ref<string | null>(
    typeof window.history.state?.back === 'string' ? window.history.state.back : null,
  )
  const fromAdmin = computed(() => previousPath.value?.startsWith('/admin') ?? false)
  const hasKnownPrevious = computed(
    () => previousPath.value !== null && (options.matchesPrevious?.(previousPath.value) ?? false),
  )
  const returnLabel = computed(
    () => (fromAdmin.value ? '返回后台管理' : options.defaultLabel),
  )

  function handleReturn(): void {
    const currentBack = window.history.state?.back
    if (
      typeof currentBack === 'string'
      && currentBack !== router.currentRoute.value.fullPath
      && (fromAdmin.value || hasKnownPrevious.value)
    ) {
      router.back()
      return
    }
    void router.push(fromAdmin.value ? '/admin' : options.defaultFallback)
  }

  return {
    previousPath,
    fromAdmin,
    returnLabel,
    handleReturn,
  }
}
