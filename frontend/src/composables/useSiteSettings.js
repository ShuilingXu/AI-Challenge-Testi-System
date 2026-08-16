import { reactive, readonly } from 'vue'
import { siteSettingsApi } from '../services/api'
import { applySiteMetadata, DEFAULT_SITE_SETTINGS, normalizeSiteSettings } from '../utils/siteSettings'

const siteSettings = reactive({ ...DEFAULT_SITE_SETTINGS })
let loadingPromise = null

export function applyLoadedSiteSettings(value) {
  Object.assign(siteSettings, normalizeSiteSettings(value))
  applySiteMetadata(siteSettings)
  return siteSettings
}

export function loadSiteSettings(force = false) {
  if (loadingPromise && !force) return loadingPromise
  loadingPromise = siteSettingsApi.getPublic()
    .then((response) => applyLoadedSiteSettings(response.data))
    .catch(() => applyLoadedSiteSettings(DEFAULT_SITE_SETTINGS))
    .finally(() => { loadingPromise = null })
  return loadingPromise
}

export function useSiteSettings() {
  return { siteSettings: readonly(siteSettings), loadSiteSettings }
}
