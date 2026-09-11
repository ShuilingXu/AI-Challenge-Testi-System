<template>
  <footer v-if="hasContent" class="site-footer">
    <div class="site-footer-inner">
      <div class="footer-copy">
        <span v-if="siteSettings.footerHtml">{{ siteSettings.footerHtml }}</span>
        <small v-if="visibleCopyright">{{ visibleCopyright }}</small>
      </div>
      <iframe
        v-if="siteSettings.footerCode"
        class="footer-code"
        title="站点页脚代码"
        :srcdoc="footerDocument"
        sandbox=""
        referrerpolicy="no-referrer"
      />
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { useSiteSettings } from '../composables/useSiteSettings'

const { siteSettings } = useSiteSettings()
const visibleCopyright = computed(() => (
  siteSettings.copyright && siteSettings.copyright !== siteSettings.footerHtml
    ? siteSettings.copyright
    : ''
))
const hasContent = computed(() => Boolean(
  siteSettings.footerHtml || visibleCopyright.value || siteSettings.footerCode,
))
const footerDocument = computed(() => `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; script-src 'none'; font-src https: data:; base-uri 'none'; form-action 'none'"><style>html,body{margin:0;padding:0;background:transparent;color:#718079;font:12px/1.6 -apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif;text-align:right;overflow-wrap:anywhere}a{color:#176052}img{max-width:100%;max-height:48px;object-fit:contain}</style></head><body>${siteSettings.footerCode}</body></html>`)
</script>

<style scoped>
.site-footer { flex: 0 0 auto; border-top: 1px solid var(--border); background: var(--surface); color: var(--text-muted); }
.site-footer-inner { display: flex; min-width: 0; max-width: 1440px; min-height: 70px; margin: 0 auto; padding: 14px 28px; align-items: center; justify-content: space-between; gap: 24px; }
.footer-copy { display: grid; min-width: 0; gap: 4px; font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; }
.footer-copy small { color: var(--text-muted); font-size: 12px; }
.footer-code { flex: 0 1 520px; width: min(520px, 48vw); height: 48px; border: 0; background: transparent; }
@media (max-width: 700px) {
  .site-footer-inner { align-items: stretch; flex-direction: column; gap: 8px; padding: 14px 18px; }
  .footer-code { flex-basis: 48px; width: 100%; }
}
</style>
