<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useSiteSettings } from './composables/useSiteSettings'

const { loadSiteSettings } = useSiteSettings()
onMounted(() => { void loadSiteSettings() })
</script>
