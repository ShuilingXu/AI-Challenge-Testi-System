<template>
  <main class="site-page"><AdminNav /><section class="site-layout"><el-form class="tool-panel" label-position="top"><h1>站点信息</h1><p class="hint">修改后会立即应用到登录页、学生端和管理端。</p><el-form-item label="站点名称"><el-input v-model="form.siteTitle" maxlength="120" /></el-form-item><el-form-item label="Logo 地址"><el-input v-model="form.logoUrl" maxlength="500" placeholder="https://... 或 /assets/logo.png" /></el-form-item><el-form-item label="站点副标题"><el-input v-model="form.siteSubtitle" maxlength="500" /></el-form-item><el-form-item label="页脚信息"><el-input v-model="form.footerHtml" type="textarea" :rows="3" maxlength="500" /></el-form-item><el-form-item label="版权内容"><el-input v-model="form.copyright" maxlength="500" /></el-form-item><el-form-item label="页脚代码"><el-input v-model="form.footerCode" type="textarea" :rows="6" maxlength="5000" placeholder="支持备案链接等 HTML；脚本不会执行" /></el-form-item><div class="actions"><el-button type="primary" :loading="saving" @click="save">保存站点信息</el-button><el-button :loading="loading" @click="load">刷新</el-button></div></el-form><aside class="preview"><p class="page-eyebrow">预览</p><div class="preview-brand"><BrandMark /><strong>{{ form.siteTitle || '站点名称' }}</strong></div><p>{{ form.siteSubtitle }}</p><footer><span>{{ form.footerHtml }}</span><small v-if="form.copyright && form.copyright !== form.footerHtml">{{ form.copyright }}</small><code v-if="form.footerCode">页脚代码已配置</code></footer></aside></section></main>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import BrandMark from '../components/BrandMark.vue'
import { siteSettingsApi } from '../services/api'
import { applyLoadedSiteSettings } from '../composables/useSiteSettings'
const form = reactive({ logoUrl: '', siteTitle: '', siteSubtitle: '', footerHtml: '', footerCode: '', copyright: '' }); const loading = ref(false); const saving = ref(false)
function apply(value) { Object.assign(form, value || {}) }
async function load() { loading.value = true; try { const response = await siteSettingsApi.getAdmin(); apply(response.data); applyLoadedSiteSettings(response.data) } catch (error) { ElMessage.error(error.message || '站点信息加载失败') } finally { loading.value = false } }
async function save() { saving.value = true; try { const response = await siteSettingsApi.save({ ...form }); apply(response.data); applyLoadedSiteSettings(response.data); ElMessage.success('站点信息已保存') } catch (error) { ElMessage.error(error.message || '站点信息保存失败') } finally { saving.value = false } }
onMounted(load)
</script>
<style scoped>
.site-page{min-height:100vh}.site-layout{max-width:1080px;margin:0 auto;padding:30px;display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:24px}.tool-panel,.preview{padding:24px;border:1px solid var(--border);border-radius:var(--radius-sm);background:var(--surface)}.tool-panel h1{margin:0 0 4px}.hint{color:var(--text-muted);font-size:13px;line-height:1.6}.actions{display:flex;gap:8px}.preview-brand{display:flex;align-items:center;gap:10px;font-size:18px}.preview>p:not(.page-eyebrow){color:var(--text-muted);line-height:1.6}.preview footer{display:grid;gap:5px;margin-top:70px;padding-top:14px;border-top:1px solid var(--border);color:var(--text-muted);font-size:13px;overflow-wrap:anywhere}.preview footer small{font-size:12px}.preview footer code{color:var(--primary);font:12px inherit}@media(max-width:800px){.site-layout{grid-template-columns:1fr;padding:18px}}
.site-layout>*,.preview-brand>*{min-width:0}
.preview-brand strong,.preview>p{overflow-wrap:anywhere}
.actions{flex-wrap:wrap}
@media(max-width:800px){.site-layout{grid-template-columns:minmax(0,1fr)}}
</style>
