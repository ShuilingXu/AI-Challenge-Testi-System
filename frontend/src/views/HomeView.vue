<template>
  <div class="public-site">
    <header class="site-header">
      <RouterLink class="brand" to="/">
        <BrandMark />
        <span><strong>{{ siteSettings.siteTitle }}</strong><small>人才与组织中心</small></span>
      </RouterLink>
      <nav class="public-nav" aria-label="主导航">
        <a href="#updates">最新信息</a>
        <a href="#jobs">开放岗位</a>
        <RouterLink class="header-login" to="/login">登录工作台</RouterLink>
      </nav>
    </header>

    <main>
      <section class="hero-band">
        <div class="hero-copy">
          <p class="section-kicker">JOIN THE TEAM</p>
          <h1>{{ siteSettings.siteTitle }}</h1>
          <p class="hero-lead">{{ siteSettings.siteSubtitle }}</p>
          <div class="hero-actions">
            <a class="button button-primary" href="#jobs">查看开放岗位 <span>↓</span></a>
            <RouterLink class="button button-quiet" to="/login">进入面试中心 <span>→</span></RouterLink>
          </div>
        </div>
        <div class="hero-aside" aria-label="网站概览">
          <div class="hero-aside-top"><span>人才中心</span><span>2026</span></div>
          <div class="hero-rule"></div>
          <p>把每一次交流，都变成更好的工作体验。</p>
          <div class="hero-stats"><div><strong>{{ jobs.length }}</strong><span>开放岗位</span></div><div><strong>{{ updates.length }}</strong><span>最新信息</span></div></div>
        </div>
      </section>

      <section id="updates" class="content-section">
        <div class="section-heading"><div><p class="section-kicker">NEWS &amp; NOTES</p><h2>最新信息</h2></div><span class="section-note">管理员持续更新中</span></div>
        <div v-if="updates.length" class="update-list">
          <article v-for="item in updates" :key="item.id" class="update-item">
            <div class="update-date">{{ formatDate(item.publishedAt) }}</div>
            <div class="update-body"><h3>{{ item.title }}</h3><p>{{ item.summary || item.content }}</p></div>
            <span class="update-arrow">↗</span>
          </article>
        </div>
        <div v-else class="empty-state">暂时没有新的信息，欢迎稍后回来查看。</div>
      </section>

      <section id="jobs" class="content-section jobs-section">
        <div class="section-heading"><div><p class="section-kicker">OPEN POSITIONS</p><h2>一起做有影响力的事</h2></div><span class="section-note">{{ jobs.length }} 个机会</span></div>
        <div v-if="jobs.length" class="job-grid">
          <article v-for="job in jobs" :key="job.id" class="job-item">
            <div class="job-top"><span>{{ job.departmentName || '人才中心' }}</span><span>{{ job.jobType || '全职' }}</span></div>
            <h3>{{ job.jobTitle }}</h3>
            <p>{{ job.requirements || '欢迎查看岗位详情并提交申请。' }}</p>
            <div class="job-bottom"><span>{{ job.workLocation || '地点待定' }} · {{ job.salaryRange || '薪资面议' }}</span><RouterLink to="/login">申请职位&nbsp;→</RouterLink></div>
          </article>
        </div>
        <div v-else class="empty-state">当前没有开放岗位，请关注最新信息。</div>
      </section>
    </main>

    <footer class="site-footer"><span>{{ siteSettings.footerHtml }}</span><RouterLink to="/login">管理员登录</RouterLink></footer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { useSiteSettings } from '../composables/useSiteSettings'
import { recruitmentApi, siteContentApi } from '../services/api'

const { siteSettings } = useSiteSettings()
const jobs = ref([])
const updates = ref([])
function formatDate(value) { return value ? String(value).slice(0, 10).replaceAll('-', '.') : '—' }
async function loadPage() {
  const [jobResult, updateResult] = await Promise.allSettled([recruitmentApi.listOpenJobs(), siteContentApi.listPublished()])
  if (jobResult.status === 'fulfilled') jobs.value = jobResult.value.data || []
  if (updateResult.status === 'fulfilled') updates.value = updateResult.value.data || []
}
onMounted(loadPage)
</script>

<style scoped>
.public-site { min-height: 100vh; color: #17211f; background: #f6f7f4; }
.site-header, main, .site-footer { max-width: 1240px; margin: auto; padding-inline: 32px; }
.site-header { min-height: 82px; height: auto; padding-block: 16px; box-sizing: border-box; display: flex; justify-content: space-between; align-items: center; gap: 24px; }
.brand { --site-brand-background: #175c50; display: inline-flex; min-width: 0; max-width: min(52vw, 520px); align-items: center; gap: 11px; color: inherit; text-decoration: none; }
.brand > span:last-child { min-width: 0; }.brand strong, .brand small { display: block; overflow-wrap: anywhere; }.brand strong { font-size: 15px; }.brand small { color: #75827d; font-size: 11px; margin-top: 2px; }
.public-nav { display: flex; align-items: center; gap: 28px; color: #65716d; font-size: 14px; }.public-nav a { color: inherit; text-decoration: none; }.public-nav a:hover { color: #175c50; }
.header-login { padding: 10px 16px; border: 1px solid #c8d1cd; border-radius: 6px; color: #175c50 !important; }
.hero-band { min-height: 485px; display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(280px, .75fr); gap: 48px; align-items: end; padding: 74px 0 68px; border-top: 1px solid #dde4df; border-bottom: 1px solid #dde4df; }
.section-kicker { margin: 0 0 15px; color: #175c50; font-size: 11px; font-weight: 800; letter-spacing: .14em; }.hero-copy h1 { max-width: 700px; margin: 0; font-size: clamp(40px, 6vw, 78px); line-height: 1.03; letter-spacing: -.045em; font-weight: 700; overflow-wrap: anywhere; }.hero-lead { max-width: 560px; margin: 26px 0 0; color: #63716b; font-size: 17px; line-height: 1.75; overflow-wrap: anywhere; }
.hero-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 34px; }.button { display: inline-flex; align-items: center; gap: 18px; padding: 13px 17px; border-radius: 6px; text-decoration: none; font-weight: 700; font-size: 14px; }.button-primary { color: #fff; background: #175c50; }.button-primary:hover { background: #12483e; }.button-quiet { color: #175c50; border: 1px solid #c8d1cd; background: transparent; }
.hero-aside { align-self: stretch; display: flex; flex-direction: column; justify-content: flex-end; padding: 24px; border-left: 1px solid #cbd7d1; }.hero-aside-top, .hero-stats { display: flex; justify-content: space-between; color: #718079; font-size: 12px; }.hero-rule { height: 1px; margin: 18px 0 38px; background: #cbd7d1; }.hero-aside p { max-width: 260px; margin: 0 0 34px; font-size: 23px; line-height: 1.3; font-weight: 650; }.hero-stats { justify-content: flex-start; gap: 42px; }.hero-stats strong, .hero-stats span { display: block; }.hero-stats strong { color: #175c50; font-size: 28px; }.hero-stats span { margin-top: 4px; }
.content-section { padding: 82px 0 30px; }.jobs-section { padding-bottom: 90px; }.section-heading { display: flex; justify-content: space-between; align-items: end; gap: 20px; margin-bottom: 28px; }.section-heading h2 { margin: 0; font-size: 32px; letter-spacing: -.03em; }.section-note { color: #7b8781; font-size: 13px; }.update-list { border-top: 1px solid #cad5cf; }.update-item { display: grid; grid-template-columns: 130px minmax(0, 1fr) 24px; gap: 20px; align-items: start; padding: 23px 0; border-bottom: 1px solid #cad5cf; }.update-date { color: #7b8781; font-size: 13px; padding-top: 3px; }.update-body h3 { margin: 0 0 8px; font-size: 18px; }.update-body p { margin: 0; color: #68766f; line-height: 1.65; }.update-arrow { color: #175c50; font-size: 21px; }.job-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }.job-item { padding: 24px; border: 1px solid #cad5cf; background: #fbfcfa; border-radius: 7px; }.job-item:hover { border-color: #86a99e; }.job-top, .job-bottom { display: flex; justify-content: space-between; gap: 15px; color: #77857e; font-size: 12px; }.job-item h3 { margin: 22px 0 10px; font-size: 21px; }.job-item p { min-height: 48px; margin: 0; color: #69766f; line-height: 1.6; }.job-bottom { align-items: end; margin-top: 27px; }.job-bottom a { color: #175c50; font-weight: 700; text-decoration: none; }.empty-state { padding: 25px; color: #7b8781; border: 1px dashed #bdc9c2; border-radius: 7px; text-align: center; }.site-footer { display: flex; justify-content: space-between; gap: 20px; padding-top: 22px; padding-bottom: 30px; color: #849089; border-top: 1px solid #dde4df; font-size: 12px; }.site-footer a { color: inherit; }
@media (max-width: 760px) { .site-header, main, .site-footer { padding-inline: 18px; }.public-nav { gap: 12px; }.public-nav > a:not(.header-login) { display: none; }.hero-band { grid-template-columns: 1fr; gap: 30px; padding: 55px 0 48px; }.hero-aside { min-height: 210px; border-left: 0; border-top: 1px solid #cbd7d1; padding: 24px 0 0; }.content-section { padding-top: 58px; }.section-heading { align-items: flex-start; flex-direction: column; gap: 8px; }.job-grid { grid-template-columns: 1fr; }.update-item { grid-template-columns: 82px minmax(0, 1fr) 18px; gap: 10px; }.site-footer { flex-direction: column; gap: 8px; } }
</style>

<style scoped>
.site-footer { min-width: 0; }
.site-footer > span { min-width: 0; overflow-wrap: anywhere; word-break: break-word; }
.site-footer a { flex: 0 0 auto; }
</style>
