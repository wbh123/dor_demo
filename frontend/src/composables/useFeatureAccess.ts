import { computed, ref } from 'vue'

const enabledFeatures = ref<Set<string>>(new Set())
const serviceOperationAllowed = ref(true)
const serviceMessage = ref('')
const quotaAlerts = ref<Array<Record<string, unknown>>>([])

export interface BusinessEntitlementProjection {
  features?: string[]
  serviceOperationAllowed?: boolean
  serviceMessage?: string
  quotaAlerts?: Array<Record<string, unknown>>
}

export function applyBusinessEntitlements(projection: BusinessEntitlementProjection): void {
  enabledFeatures.value = new Set(projection.features ?? [])
  serviceOperationAllowed.value = projection.serviceOperationAllowed ?? true
  serviceMessage.value = projection.serviceMessage ?? ''
  quotaAlerts.value = projection.quotaAlerts ?? []
}

export function useFeatureAccess() {
  function hasFeature(code: string): boolean {
    return enabledFeatures.value.has(code)
  }

  return {
    hasFeature,
    features: computed(() => [...enabledFeatures.value]),
    serviceOperationAllowed: computed(() => serviceOperationAllowed.value),
    serviceMessage: computed(() => serviceMessage.value),
    quotaAlerts: computed(() => quotaAlerts.value),
  }
}
