<template>
  <span class="site-brand-mark" aria-hidden="true">
    <img v-if="siteSettings.logoUrl && !imageFailed" :src="siteSettings.logoUrl" alt="" @error="imageFailed = true" />
    <span v-else>{{ initials }}</span>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useSiteSettings } from '../composables/useSiteSettings'
import { siteInitials } from '../utils/siteSettings'

const { siteSettings } = useSiteSettings()
const imageFailed = ref(false)
const initials = computed(() => siteInitials(siteSettings.siteTitle))
watch(() => siteSettings.logoUrl, () => { imageFailed.value = false })
</script>

<style scoped>
.site-brand-mark { display: inline-grid; flex: 0 0 auto; width: var(--site-brand-size, 34px); height: var(--site-brand-size, 34px); place-items: center; overflow: hidden; border-radius: var(--site-brand-radius, 8px); background: var(--site-brand-background, var(--primary)); color: var(--site-brand-color, #fff); font-size: var(--site-brand-font-size, 11px); font-weight: 800; line-height: 1; }
.site-brand-mark img { display: block; width: 100%; height: 100%; padding: 2px; object-fit: contain; }
</style>
