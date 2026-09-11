<template>
  <div class="app-shell">
    <div class="app-route"><router-view /></div>
    <SiteFooter />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import SiteFooter from './components/SiteFooter.vue'
import { useSiteSettings } from './composables/useSiteSettings'

const { loadSiteSettings } = useSiteSettings()
onMounted(() => { void loadSiteSettings() })
</script>

<style scoped>
.app-shell { display: flex; min-height: 100vh; flex-direction: column; }
.app-route { flex: 1 0 auto; min-width: 0; }
</style>
