<template>
  <AdminNav />
  <main class="console-main">
      <section v-if="activeTab === 'dashboard'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Dashboard</p>
            <h2>HR 数据总览</h2>
          </div>
          <el-button type="primary" @click="loadAll">刷新</el-button>
        </div>
        <div class="metric-grid">
          <article v-for="item in metrics" :key="item.label" class="metric">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>
      </section>

      <section v-if="activeTab === 'audit'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Audit</p>
            <h2>审计日志</h2>
          </div>
          <el-button @click="loadAuditLogs">刷新</el-button>
        </div>
        <el-form :model="auditFilter" label-position="top" class="filter-grid">
          <el-form-item label="日志分类"><el-select v-model="auditFilter.moduleCode" clearable><el-option label="后台管理日志" value="ADMIN" /><el-option label="面试流程日志" value="INTERVIEW" /><el-option label="报名者投递日志" value="RECRUITMENT" /></el-select></el-form-item>
          <el-form-item label="动作"><el-input v-model="auditFilter.actionCode" placeholder="如 审批AI / 投递报名 / 重置密码" /></el-form-item>
          <el-form-item label="关键词"><el-input v-model="auditFilter.keyword" placeholder="操作人 / 对象ID / 详情" /></el-form-item>
          <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadAuditLogs">查询</el-button><el-button @click="resetAuditFilter">重置</el-button></div></el-form-item>
        </el-form>
        <div class="audit-grid">
          <article v-for="group in auditGroups" :key="group.key" class="audit-panel">
            <div class="audit-panel-head">
              <h3>{{ group.title }}</h3>
              <span>{{ group.items.length }} 条</span>
            </div>
            <el-table :data="group.items" stripe class="data-table compact-table" max-height="520">
              <el-table-column prop="operatorUsername" label="操作人" min-width="100" />
              <el-table-column label="角色" min-width="90"><template #default="scope">{{ localizeRole(scope.row.operatorRoleCode) }}</template></el-table-column>
              <el-table-column label="动作" min-width="150"><template #default="scope">{{ localizeAction(scope.row.actionCode) }}</template></el-table-column>
              <el-table-column label="对象类型" min-width="130"><template #default="scope">{{ localizeTarget(scope.row.targetType) }}</template></el-table-column>
              <el-table-column prop="targetId" label="对象ID" width="90" />
              <el-table-column label="详情" min-width="180"><template #default="scope">{{ localizeDetail(scope.row.detail) }}</template></el-table-column>
              <el-table-column prop="createdAt" label="时间" min-width="165" />
            </el-table>
          </article>
        </div>
      </section>

      <section v-if="activeTab === 'users'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Users</p>
            <h2>用户管理中心</h2>
          </div>
          <el-button @click="loadUsers">刷新</el-button>
        </div>
        <el-table :data="users" stripe class="data-table" @row-click="openUser">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="displayName" label="姓名" />
          <el-table-column prop="roleCode" label="角色" />
          <el-table-column prop="mobilePhone" label="电话" />
          <el-table-column prop="status" label="状态"><template #default="scope">{{ scope.row.status === 1 ? '启用' : '停用' }}</template></el-table-column>
          <el-table-column label="操作" width="90"><template #default="scope"><el-button text type="danger" @click.stop="deleteUser(scope.row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-form :model="userForm" label-position="top" class="form-grid">
          <el-form-item label="用户名"><el-input v-model="userForm.username" disabled /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item>
          <el-form-item label="角色"><el-select v-model="userForm.roleCode"><el-option v-if="isItAdmin" label="IT管理员" value="IT_ADMIN" /><el-option v-if="isItAdmin" label="HR管理员" value="HR_ADMIN" /><el-option label="HR用户" value="HR_USER" /><el-option label="面试者" value="INTERVIEWEE" /></el-select></el-form-item>
          <el-form-item label="状态"><el-select v-model="userForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
          <el-form-item label="手机号"><el-input v-model="userForm.mobilePhone" /></el-form-item>
          <el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="userForm.newPassword" type="password" show-password placeholder="至少8位，含字母和数字" /></el-form-item>
        </el-form>
        <div class="action-row"><el-button type="primary" @click="saveUser">保存用户</el-button><el-button type="warning" @click="resetUserPassword">重置密码</el-button></div>
      </section>

      <section v-if="activeTab === 'departments'" class="page-card">
        <div class="topline">
          <div>
            <p class="page-eyebrow">Departments</p>
            <h2>部门管理</h2>
          </div>
          <el-button @click="loadDepartments">刷新</el-button>
        </div>
        <div class="sub-tabs">
          <button :class="{ active: departmentMode === 'create' }" @click="showCreateDepartment">新增部门</button>
          <button :class="{ active: departmentMode === 'query' }" @click="departmentMode = 'query'">查询部门</button>
          <button :class="{ active: departmentMode === 'edit' }" @click="departmentMode = 'edit'">修改部门</button>
        </div>
        <template v-if="departmentMode === 'create' || departmentMode === 'edit'">
          <el-form :model="departmentForm" label-position="top" class="form-grid">
            <el-form-item label="部门名称"><el-input v-model="departmentForm.departmentName" /></el-form-item>
            <el-form-item label="部门编码"><el-input v-model="departmentForm.departmentCode" /></el-form-item>
            <el-form-item label="上级部门"><el-select v-model="departmentForm.parentDepartmentId" clearable placeholder="不能选择本部门"><el-option v-for="item in availableParentDepartments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="部门负责人"><el-select v-model="departmentForm.managerEmployeeId" clearable filterable placeholder="从员工列表选择负责人"><el-option v-for="item in employees" :key="item.id" :label="item.fullName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="部门状态"><el-select v-model="departmentForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item label="排序"><el-input-number v-model="departmentForm.sortOrder" :min="0" /></el-form-item>
            <el-form-item label="部门职能简介" class="wide"><el-input v-model="departmentForm.description" type="textarea" :rows="3" /></el-form-item>
          </el-form>
          <div class="action-row"><el-button type="primary" @click="saveDepartment">{{ departmentMode === 'create' ? '新增部门' : '保存修改' }}</el-button><el-button @click="resetDepartmentForm">清空</el-button></div>
        </template>
        <template v-if="departmentMode === 'query'">
          <el-form :model="departmentFilter" label-position="top" class="filter-grid">
            <el-form-item label="上级部门"><el-select v-model="departmentFilter.parentDepartmentId" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="部门状态"><el-select v-model="departmentFilter.status" clearable><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="departmentFilter.keyword" placeholder="部门名称 / 编码 / 简介" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadDepartments">查询</el-button><el-button @click="resetDepartmentFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="departments" stripe class="data-table" @row-click="openDepartment">
            <el-table-column prop="departmentName" label="部门名称" min-width="140" />
            <el-table-column prop="departmentCode" label="部门编码" min-width="120" />
            <el-table-column prop="parentDepartmentName" label="上级部门" min-width="120" />
            <el-table-column prop="managerEmployeeName" label="部门负责人" min-width="120" />
            <el-table-column prop="description" label="部门职能简介" min-width="180" />
            <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteDepartment(scope.row.id)">删除</el-button></template></el-table-column>
          </el-table>
        </template>
      </section>

      <section v-if="activeTab === 'employees'" class="page-card">
        <div class="topline"><div><p class="page-eyebrow">Employees</p><h2>员工管理</h2></div><el-button @click="loadEmployees">刷新</el-button></div>
        <div class="sub-tabs"><button :class="{ active: employeeMode === 'create' }" @click="showCreateEmployee">新增员工</button><button :class="{ active: employeeMode === 'query' }" @click="employeeMode = 'query'">查询员工</button><button :class="{ active: employeeMode === 'edit' }" @click="employeeMode = 'edit'">修改员工</button></div>
        <template v-if="employeeMode === 'create' || employeeMode === 'edit'">
          <el-form :model="employeeForm" label-position="top" class="form-grid">
            <el-form-item label="工号"><el-input v-model="employeeForm.employeeCode" placeholder="新增时可留空；修改请从列表进入" /></el-form-item>
            <el-form-item label="姓名"><el-input v-model="employeeForm.fullName" /></el-form-item>
            <el-form-item label="身份证号"><el-input v-model="employeeForm.idCardNo" /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="employeeForm.mobilePhone" /></el-form-item>
            <el-form-item label="招聘专业"><el-input v-model="employeeForm.recruitmentMajor" /></el-form-item>
            <el-form-item label="岗位"><el-select v-model="employeeForm.jobId" filterable><el-option v-for="item in jobs" :key="item.id" :label="`${item.jobCode} · ${item.jobTitle}`" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="基本薪资（元/月）"><el-input-number v-model="employeeForm.baseSalary" :min="employeeMode === 'create' ? 0.01 : 0" :precision="2" /></el-form-item>
            <el-form-item v-if="employeeMode === 'edit'" label="薪资调整原因"><el-input v-model="employeeForm.salaryChangeReason" maxlength="500" /></el-form-item>
            <el-form-item label="个人加班单价"><el-input-number v-model="employeeForm.overtimeRate" :min="0" :precision="2" /></el-form-item>
            <el-form-item label="直属部门"><el-select v-model="employeeForm.departmentId"><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="员工状态"><el-select v-model="employeeForm.employmentStatus"><el-option label="待入职" :value="0" /><el-option label="已入职" :value="1" /><el-option label="停用" :value="2" /><el-option label="已离职" :value="3" /></el-select></el-form-item>
            <el-form-item label="银行卡号"><el-input v-model="employeeForm.bankAccountNo" /></el-form-item>
            <el-form-item label="开户银行"><el-input v-model="employeeForm.bankName" /></el-form-item>
            <el-form-item label="辞退原因"><el-select v-model="employeeForm.dismissalReason" clearable><el-option v-for="reason in ['试用期不合格','违纪辞退','组织调整','协商解除','其他']" :key="reason" :label="reason" :value="reason" /></el-select></el-form-item>
            <el-form-item label="辞退日期"><el-date-picker v-model="employeeForm.dismissalDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          </el-form>
          <div class="action-row"><el-button type="primary" @click="saveEmployee">{{ employeeMode === 'create' ? '新增员工' : '保存修改' }}</el-button><el-button @click="resetEmployeeForm">清空</el-button></div>
        </template>
        <template v-if="employeeMode === 'query'">
          <el-form :model="employeeFilter" label-position="top" class="filter-grid">
            <el-form-item label="直属部门"><el-select v-model="employeeFilter.departmentId" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="员工状态"><el-select v-model="employeeFilter.employmentStatus" clearable><el-option label="待入职" :value="0" /><el-option label="已入职" :value="1" /><el-option label="停用" :value="2" /><el-option label="已离职" :value="3" /></el-select></el-form-item>
            <el-form-item label="姓名"><el-input v-model="employeeFilter.name" /></el-form-item><el-form-item label="工号"><el-input v-model="employeeFilter.employeeCode" /></el-form-item><el-form-item label="联系方式"><el-input v-model="employeeFilter.mobilePhone" /></el-form-item>
            <el-form-item label="联系方式匹配"><el-radio-group v-model="employeeFilter.mobileExact"><el-radio-button :value="false">模糊</el-radio-button><el-radio-button :value="true">精确</el-radio-button></el-radio-group></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadEmployees">查询</el-button><el-button @click="resetEmployeeFilter">重置</el-button></div></el-form-item>
          </el-form>
          <div class="action-row"><el-button @click="downloadEmployeeTemplate">下载导入模板</el-button><el-upload :show-file-list="false" accept=".xlsx" :http-request="importEmployees"><el-button>批量导入</el-button></el-upload></div><el-table :data="employees" stripe class="data-table" @row-click="openEmployee"><el-table-column prop="employeeCode" label="工号" /><el-table-column prop="fullName" label="姓名" /><el-table-column prop="departmentName" label="部门" /><el-table-column prop="positionName" label="岗位" /><el-table-column prop="baseSalary" label="基本薪资" /><el-table-column prop="mobilePhone" label="电话" /><el-table-column label="身份证号"><template #default="{ row }">{{ mask(row.idCardNo) }}</template></el-table-column><el-table-column label="银行卡号"><template #default="{ row }">{{ mask(row.bankAccountNo) }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteEmployee(scope.row.id)">删除</el-button></template></el-table-column></el-table>
        </template>
      </section>

      <section v-if="activeTab === 'content'" class="page-card content-page">
        <div class="topline"><div><p class="page-eyebrow">Site editor</p><h2>站点内容</h2><p class="page-subtitle">管理公开站点品牌信息与首页发布内容。</p></div><el-button @click="loadContent">刷新</el-button></div>
        <section v-if="isItAdmin" class="site-settings-section" aria-labelledby="site-settings-title">
          <div class="section-intro"><div><h3 id="site-settings-title">站点外观</h3><p>保存后立即同步到公开首页、登录页、浏览器标题和后台导航。</p></div></div>
          <div class="site-settings-layout">
            <el-form :model="siteSettingsForm" label-position="top" class="site-settings-form">
              <el-form-item label="Logo 地址"><el-input v-model="siteSettingsForm.logoUrl" maxlength="500" placeholder="/logo.svg 或 https://cdn.example.com/logo.png" /></el-form-item>
              <el-form-item label="站点标题"><el-input v-model="siteSettingsForm.siteTitle" maxlength="120" show-word-limit /></el-form-item>
              <el-form-item label="站点副标题"><el-input v-model="siteSettingsForm.siteSubtitle" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
              <el-form-item label="页脚文本"><el-input v-model="siteSettingsForm.footerHtml" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="按纯文本展示，不解析 HTML" /></el-form-item>
              <div class="action-row"><el-button type="primary" :loading="siteSettingsSaving" @click="saveSiteSettings">保存站点设置</el-button></div>
            </el-form>
            <aside class="site-settings-preview" aria-label="站点外观预览">
              <div class="preview-brand">
                <span class="preview-brand-mark" aria-hidden="true">
                  <img v-if="siteSettingsPreview.logoUrl && !siteLogoPreviewFailed" :src="siteSettingsPreview.logoUrl" alt="" @error="siteLogoPreviewFailed = true" />
                  <span v-else>{{ siteSettingsInitials }}</span>
                </span>
                <strong>{{ siteSettingsPreview.siteTitle }}</strong>
              </div>
              <p class="site-settings-subtitle">{{ siteSettingsPreview.siteSubtitle }}</p>
              <p class="site-settings-footer">{{ siteSettingsPreview.footerHtml }}</p>
            </aside>
          </div>
        </section>
        <section class="announcement-section" aria-labelledby="announcement-editor-title">
          <div class="section-intro"><div><h3 id="announcement-editor-title">首页信息发布</h3><p>保存为草稿后，只有切换为发布状态的内容才会显示在公开首页。</p></div></div>
          <div class="content-editor-layout">
          <el-form :model="contentForm" label-position="top" class="content-form">
            <el-form-item label="标题"><el-input v-model="contentForm.title" placeholder="例如：春季招聘开放" /></el-form-item>
            <el-form-item label="摘要"><el-input v-model="contentForm.summary" maxlength="140" show-word-limit placeholder="首页列表中展示的一句话" /></el-form-item>
            <el-form-item label="正文"><el-input v-model="contentForm.content" type="textarea" :rows="9" placeholder="支持纯文本，建议分段书写" /></el-form-item>
            <div class="form-row"><el-form-item label="内容类型"><el-radio-group v-model="contentForm.type" class="content-type-options" aria-label="内容类型"><el-radio-button value="announcement">公告</el-radio-button><el-radio-button value="story">团队动态</el-radio-button><el-radio-button value="guide">招聘说明</el-radio-button></el-radio-group></el-form-item><el-form-item label="发布时间"><el-input v-model="contentForm.publishedAt" placeholder="2026-08-13 09:00" /></el-form-item></div>
            <el-form-item><el-checkbox v-model="contentForm.published">发布到首页</el-checkbox></el-form-item>
            <div class="action-row"><el-button type="primary" @click="saveContent">保存内容</el-button><el-button @click="resetContentForm">新建内容</el-button></div>
          </el-form>
          <aside class="content-preview"><div class="preview-label">首页预览</div><p class="preview-date">{{ contentForm.publishedAt || '未设置日期' }}</p><h3>{{ contentForm.title || '内容标题' }}</h3><p>{{ contentForm.summary || contentForm.content || '这里会显示首页信息摘要。' }}</p><span :class="['publish-state', { published: contentForm.published }]">{{ contentForm.published ? '已发布' : '草稿' }}</span></aside>
          </div>
          <div class="content-table-head"><h3>已保存内容</h3><span>{{ contentItems.length }} 条</span></div>
          <el-table :data="contentItems" stripe class="data-table" @row-click="editContent"><el-table-column prop="title" label="标题" min-width="200" /><el-table-column prop="type" label="类型" width="110" /><el-table-column prop="publishedAt" label="时间" width="160" /><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.published ? 'success' : 'info'">{{ scope.row.published ? '已发布' : '草稿' }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteContent(scope.row.id)">删除</el-button></template></el-table-column></el-table>
        </section>
      </section>

      <section v-if="activeTab === 'recruitment'" class="page-card">
        <div class="topline"><div><p class="page-eyebrow">Recruitment</p><h2>招聘后台</h2></div><el-button @click="loadRecruitment">刷新</el-button></div>
        <div class="sub-tabs"><button :class="{ active: recruitmentMode === 'jobCreate' }" @click="showCreateJob">新增岗位</button><button :class="{ active: recruitmentMode === 'jobQuery' }" @click="recruitmentMode = 'jobQuery'">查询岗位</button><button :class="{ active: recruitmentMode === 'jobEdit' }" @click="recruitmentMode = 'jobEdit'">修改岗位</button><button :class="{ active: recruitmentMode === 'candidates' }" @click="recruitmentMode = 'candidates'">候选人信息</button></div>
        <template v-if="recruitmentMode === 'jobCreate' || recruitmentMode === 'jobEdit'">
          <el-form :model="jobForm" label-position="top" class="form-grid"><el-form-item label="岗位名称"><el-input v-model="jobForm.jobTitle" /></el-form-item><el-form-item label="岗位编码"><el-input v-model="jobForm.jobCode" /></el-form-item><el-form-item label="招聘部门"><el-select v-model="jobForm.departmentId" filterable placeholder="从数据库部门中选择" @change="syncJobDepartmentName"><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" /></el-select></el-form-item><el-form-item label="工作地点"><el-input v-model="jobForm.workLocation" /></el-form-item><el-form-item label="岗位类型"><el-input v-model="jobForm.jobType" /></el-form-item><el-form-item label="招聘人数"><el-input-number v-model="jobForm.headcount" :min="1" /></el-form-item><el-form-item label="薪资范围"><el-input v-model="jobForm.salaryRange" /></el-form-item><el-form-item label="默认加班单价（元/小时）"><el-input-number v-model="jobForm.defaultOvertimeRate" :min="0" :precision="2" /></el-form-item><el-form-item label="状态"><el-select v-model="jobForm.status"><el-option label="开放" :value="1" /><el-option label="关闭" :value="0" /></el-select></el-form-item><el-form-item label="岗位职责" class="wide"><el-input v-model="jobForm.responsibilities" type="textarea" :rows="3" /></el-form-item><el-form-item label="任职要求" class="wide"><el-input v-model="jobForm.requirements" type="textarea" :rows="3" /></el-form-item></el-form>
          <div class="action-row"><el-button type="primary" @click="saveJob">{{ recruitmentMode === 'jobCreate' ? '新增岗位' : '保存修改' }}</el-button><el-button @click="resetJobForm">清空</el-button></div>
        </template>
        <template v-if="recruitmentMode === 'jobQuery'">
          <el-form :model="jobFilter" label-position="top" class="filter-grid">
            <el-form-item label="招聘部门"><el-select v-model="jobFilter.departmentName" clearable><el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.departmentName" /></el-select></el-form-item>
            <el-form-item label="岗位类型"><el-input v-model="jobFilter.jobType" placeholder="全职 / 实习 / 外包" /></el-form-item>
            <el-form-item label="岗位状态"><el-select v-model="jobFilter.status" clearable><el-option label="开放" :value="1" /><el-option label="关闭" :value="0" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="jobFilter.keyword" placeholder="岗位 / 编码 / 地点 / 薪资" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadJobs">查询</el-button><el-button @click="resetJobFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="jobs" stripe class="data-table" @row-click="openJob"><el-table-column prop="jobTitle" label="岗位" min-width="140" /><el-table-column prop="departmentName" label="部门" min-width="120" /><el-table-column prop="headcount" label="人数" width="80" /><el-table-column prop="status" label="状态" width="90"><template #default="scope">{{ scope.row.status === 1 ? '开放' : '关闭' }}</template></el-table-column><el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteJob(scope.row.id)">删除</el-button></template></el-table-column></el-table>
        </template>
        <template v-if="recruitmentMode === 'candidates'">
          <el-form :model="candidateFilter" label-position="top" class="filter-grid">
            <el-form-item label="岗位"><el-select v-model="candidateFilter.jobId" clearable><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
            <el-form-item label="投递状态"><el-select v-model="candidateFilter.status" clearable><el-option label="已投递" value="SUBMITTED" /><el-option label="面试中" value="INTERVIEWING" /></el-select></el-form-item>
            <el-form-item label="面试阶段"><el-select v-model="candidateFilter.interviewStageStatus" clearable><el-option label="简历待查" value="简历待查" /><el-option label="AI面" value="AI面" /><el-option label="AI待审批" value="AI待审批" /><el-option label="视频面" value="视频面" /><el-option label="视频待审批" value="视频待审批" /><el-option label="线下面" value="线下面" /><el-option label="已通过" value="已通过" /><el-option label="已拒绝" value="已拒绝" /><el-option label="已终止" value="已终止" /></el-select></el-form-item>
            <el-form-item label="关键词"><el-input v-model="candidateFilter.keyword" placeholder="姓名 / 手机 / 专业 / 学校" /></el-form-item>
            <el-form-item label="操作"><div class="filter-actions"><el-button type="primary" @click="loadCandidates">查询</el-button><el-button @click="resetCandidateFilter">重置</el-button></div></el-form-item>
          </el-form>
          <el-table :data="candidates" stripe class="data-table" @row-click="openCandidate"><el-table-column prop="id" label="候选人ID" min-width="100" /><el-table-column prop="fullName" label="报名者姓名" min-width="120" /><el-table-column prop="mobilePhone" label="联系电话" min-width="130" /><el-table-column prop="jobTitle" label="岗位" min-width="140" /><el-table-column prop="interviewStageStatus" label="面试状态" min-width="120" /><el-table-column label="LLM简历评分" min-width="120"><template #default="scope"><span>{{ scope.row.resumeLlmScore ?? resumeLlmStatusLabel(scope.row.resumeLlmStatus) }}</span></template></el-table-column><el-table-column prop="interviewProcessId" label="流程流水号" min-width="120" /><el-table-column label="简历" min-width="150"><template #default="scope"><el-button v-if="scope.row.resumeFileId" text class="resume-link" @click.stop="openResume(scope.row.resumeFileId)">{{ scope.row.resumeFileName || '查看简历' }}</el-button><span v-else>未上传</span></template></el-table-column><el-table-column label="操作" width="460"><template #default="scope"><el-button text @click.stop="openCandidate(scope.row)">查看详情</el-button><el-button text :disabled="!canReevaluateResumeLlm(scope.row)" @click.stop="reevaluateResumeLlm(scope.row.id)">{{ resumeLlmReevaluateLabel(scope.row) }}</el-button><el-button text @click.stop="startCandidateInterview(scope.row)">发起面试</el-button><el-button text type="danger" @click.stop="rejectCandidateResume(scope.row.id)">简历拒绝</el-button><el-button text type="danger" @click.stop="deleteCandidate(scope.row.id)">删除候选人</el-button></template></el-table-column><el-table-column prop="applicationStatus" label="状态" width="110" /></el-table>
        </template>
      </section>
  </main>
    <el-dialog v-model="templateDialogVisible" title="选择面试流程" width="min(560px, calc(100vw - 32px))" destroy-on-close>
      <p class="dialog-intro">选择流程模板后，系统会为该候选人创建独立的面试阶段快照；留空则沿用旧流程。</p>
      <el-form label-position="top"><el-form-item label="流程模板（可选）"><el-select v-model="selectedTemplateId" clearable placeholder="不选择则沿用旧流程"><el-option v-for="item in enabledProcessTemplates" :key="item.id" :label="item.templateName" :value="item.id"><span>{{ item.templateName }}</span><small class="template-option-detail">{{ templateStageSummary(item) }}</small></el-option></el-select></el-form-item></el-form>
      <div v-if="selectedProcessTemplate" class="template-preview"><strong>{{ selectedProcessTemplate.templateName }}</strong><span>{{ templateStageSummary(selectedProcessTemplate) }}</span></div>
      <template #footer><el-button @click="templateDialogVisible = false">取消</el-button><el-button type="primary" :loading="startingInterview" @click="confirmStartCandidateInterview">发起面试</el-button></template>
    </el-dialog>
    <el-dialog v-model="employeeImportVisible" title="员工导入结果" width="min(680px, calc(100vw - 32px))">
      <el-table :data="employeeImportRows" max-height="420">
        <el-table-column prop="row" label="行号" width="80" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="message" label="明细" min-width="260" />
      </el-table>
    </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminNav from '../components/AdminNav.vue'
import { authApi, downloadBlob, hrApi, interviewApi, recruitmentApi, siteContentApi, siteSettingsApi } from '../services/api'
import { applyLoadedSiteSettings } from '../composables/useSiteSettings'
import { readSessionUser, writeSessionUser } from '../utils/session'
import { isStrongPassword, strongPasswordMessage } from '../utils/password'
import { DEFAULT_SITE_SETTINGS, normalizeSiteSettings, safeBrandAssetUrl, siteInitials } from '../utils/siteSettings'

const router = useRouter()
const route = useRoute()
const sessionUser = ref(readSessionUser())
const isItAdmin = computed(() => sessionUser.value?.roleCode === 'IT_ADMIN')
const isHrAdmin = computed(() => sessionUser.value?.roleCode === 'HR_ADMIN')
const activeTab = computed(() => route.meta.consoleTab || 'dashboard')
const departmentMode = ref('create')
const employeeMode = ref('create')
const recruitmentMode = ref(route.meta.recruitmentMode || 'jobCreate')
const dashboard = reactive({ departmentCount: 0, employeeCount: 0, activeEmployeeCount: 0, pendingOnboardingCount: 0, recruitmentBindingCount: 0, performanceBindingCount: 0 })
const departments = ref([])
const employees = ref([])
const contentItems = ref([])
const jobs = ref([])
const candidates = ref([])
const users = ref([])
const auditLogs = ref([])
const processTemplates = ref([])
const templateDialogVisible = ref(false)
const employeeImportVisible = ref(false)
const employeeImportRows = ref([])
const selectedTemplateId = ref(null)
const selectedInterviewCandidate = ref(null)
const startingInterview = ref(false)
const consoleReady = ref(false)
const siteSettingsSaving = ref(false)
const siteLogoPreviewFailed = ref(false)

const userForm = reactive({ id: null, username: '', displayName: '', roleCode: 'HR_USER', status: 1, mobilePhone: '', email: '', newPassword: '' })
const departmentForm = reactive({ id: null, departmentName: '', departmentCode: '', parentDepartmentId: null, managerEmployeeId: null, description: '', sortOrder: 0, status: 1 })
const employeeForm = reactive({ id: null, employeeCode: '', fullName: '', idCardNo: '', mobilePhone: '', recruitmentMajor: '', positionName: '', jobId: null, baseSalary: null, salaryChangeReason: '', overtimeRate: null, departmentId: null, employmentStatus: 1, bankAccountNo: '', bankName: '', dismissalReason: null, dismissalDate: null })
const jobForm = reactive({ id: null, jobTitle: '', jobCode: '', departmentId: null, departmentName: '', workLocation: '', jobType: '全职', headcount: 1, requirements: '', responsibilities: '', salaryRange: '', defaultOvertimeRate: 0, status: 1 })
const auditFilter = reactive({ moduleCode: '', actionCode: '', keyword: '' })
const departmentFilter = reactive({ parentDepartmentId: null, status: null, keyword: '' })
const employeeFilter = reactive({ departmentId: null, employmentStatus: null, name: '', employeeCode: '', mobilePhone: '', mobileExact: false, keyword: '' })
const jobFilter = reactive({ status: null, departmentName: '', jobType: '', keyword: '' })
const candidateFilter = reactive({ jobId: null, status: '', interviewStageStatus: '', keyword: '' })
const contentForm = reactive({ id: null, type: 'announcement', title: '', summary: '', content: '', cover: '', published: false, publishedAt: '' })
const siteSettingsForm = reactive({ ...DEFAULT_SITE_SETTINGS })
const siteSettingsPreview = computed(() => normalizeSiteSettings(siteSettingsForm))
const siteSettingsInitials = computed(() => siteInitials(siteSettingsPreview.value.siteTitle))

const roleLabels = {
  IT_ADMIN: 'IT管理员',
  HR_ADMIN: 'HR管理员',
  HR_USER: 'HR用户',
  INTERVIEWEE: '面试者',
}
const actionLabels = {
  UPDATE_USER: '更新用户',
  RESET_USER_PASSWORD: '重置用户密码',
  CREATE_DEPARTMENT: '新增部门',
  UPDATE_DEPARTMENT: '修改部门',
  DELETE_DEPARTMENT: '删除部门',
  CREATE_EMPLOYEE: '新增员工',
  UPDATE_EMPLOYEE: '修改员工',
  DELETE_EMPLOYEE: '删除员工',
  CREATE_RECRUITMENT_JOB: '新增招聘岗位',
  UPDATE_RECRUITMENT_JOB: '修改招聘岗位',
  DELETE_RECRUITMENT_JOB: '删除招聘岗位',
  DELETE_RECRUITMENT_CANDIDATE: '删除候选人',
  REJECT_RESUME: '简历面试拒绝',
  RETRY_RESUME_LLM: '重试简历评分',
  REEVALUATE_RESUME_LLM: 'AI简历重评',
  APPLY_CANDIDATE: '投递报名',
  UPLOAD_RESUME: '上传简历',
  CREATE_VIDEO_SESSION: '创建视频面试任务',
  INTERVIEWEE_JOIN_VIDEO: '面试者加入视频',
  HR_JOIN_VIDEO: 'HR加入视频',
  APPROVE_AI: '审批AI面试',
  APPROVE_VIDEO: '审批视频面试',
  APPROVE_ONSITE: '审批线下面试',
  TERMINATE_PROCESS: '终止面试流程',
  PUBLISH_VIDEO_OFFER: '发布视频连接邀请',
  SUBMIT_VIDEO_ANSWER: '提交视频连接应答',
  ADD_HR_ICE: '添加HR网络候选',
  ADD_INTERVIEWEE_ICE: '添加面试者网络候选',
  UPLOAD_RECORDING: '上传面试录制',
  VIDEO_RECORDING_TIMEOUT_WARNING: '录像缺失超时警告',
}
const targetLabels = {
  SYS_USER: '系统用户',
  HR_DEPARTMENT: '部门',
  HR_EMPLOYEE: '员工',
  RECRUITMENT_JOB: '招聘岗位',
  RECRUITMENT_CANDIDATE: '候选人',
  RECRUITMENT_RESUME: '简历文件',
  INTERVIEW_PROCESS: '面试流程',
  VIDEO_SESSION: '视频面试会话',
}
const detailLabels = {
  INTERVIEWING: '面试中',
  SUBMITTED: '已投递',
  REJECTED: '已拒绝',
  PASSED: '已通过',
  TERMINATED: '已终止',
  ice: '网络候选信息',
}

const metrics = computed(() => [
  { label: '部门', value: dashboard.departmentCount },
  { label: '员工', value: dashboard.employeeCount },
  { label: '在职', value: dashboard.activeEmployeeCount },
  { label: '待入职', value: dashboard.pendingOnboardingCount },
  { label: '开放岗位', value: jobs.value.length },
  { label: '首页信息', value: contentItems.value.length },
])
const availableParentDepartments = computed(() => departments.value.filter((item) => item.id !== departmentForm.id))
const enabledProcessTemplates = computed(() => processTemplates.value.filter((item) => item.status === 1))
const selectedProcessTemplate = computed(() => enabledProcessTemplates.value.find((item) => item.id === selectedTemplateId.value) || null)
const auditGroups = computed(() => [
  { key: 'admin', title: '后台管理日志', items: auditLogs.value.filter((item) => !['INTERVIEW', 'RECRUITMENT'].includes(item.moduleCode)) },
  { key: 'interview', title: '面试流程日志', items: auditLogs.value.filter((item) => item.moduleCode === 'INTERVIEW') },
  { key: 'recruitment', title: '报名者投递日志', items: auditLogs.value.filter((item) => item.moduleCode === 'RECRUITMENT') },
].filter((group) => !auditFilter.moduleCode || group.key === auditFilter.moduleCode.toLowerCase()))

function fail(error) { ElMessage.error(error.message || '请求失败') }
function localizeRole(value) { return roleLabels[value] || value || '-' }
function localizeAction(value) { return actionLabels[value] || value || '-' }
function localizeTarget(value) { return targetLabels[value] || value || '-' }
function localizeDetail(value) {
  if (!value) return '-'
  const labels = { ...roleLabels, ...actionLabels, ...targetLabels, ...detailLabels }
  return labels[String(value)] || String(value)
}
async function loadSession() { try { const response = await authApi.getSession(); sessionUser.value = response.data; writeSessionUser(response.data) } catch (error) { fail(error); router.push('/login') } }
async function loadDashboard() { try { Object.assign(dashboard, (await hrApi.getDashboard()).data) } catch (error) { fail(error) } }
async function loadDepartments() { try { departments.value = (await hrApi.listAllDepartments(cleanParams(departmentFilter))).data } catch (error) { fail(error) } }
async function loadEmployees() { try { employees.value = (await hrApi.listAllEmployees(cleanParams(employeeFilter))).data } catch (error) { fail(error) } }
async function loadContentItems() {
  try { contentItems.value = (await siteContentApi.listAdmin()).data || [] } catch (error) { fail(error) }
}
async function loadContent() {
  await loadContentItems()
  if (isItAdmin.value) await loadAdminSiteSettings()
}
async function loadAdminSiteSettings() {
  try { Object.assign(siteSettingsForm, normalizeSiteSettings((await siteSettingsApi.getAdmin()).data)) } catch (error) { fail(error) }
}
async function loadJobs() { try { jobs.value = (await recruitmentApi.listAllAdminJobs(cleanParams(jobFilter))).data } catch (error) { fail(error) } }
async function loadCandidates() { try { candidates.value = (await recruitmentApi.listCandidates(cleanParams(candidateFilter))).data } catch (error) { fail(error) } }
async function loadRecruitment() { await Promise.all([loadJobs(), loadCandidates()]) }
async function loadUsers() { if (!(isItAdmin.value || isHrAdmin.value)) return; try { users.value = (await authApi.listUsers()).data } catch (error) { fail(error) } }
async function loadAuditLogs() { if (!(isItAdmin.value || isHrAdmin.value)) return; try { const params = cleanParams({ ...auditFilter, actionCode: resolveActionCode(auditFilter.actionCode) }); auditLogs.value = (await authApi.listAuditLogs(params)).data } catch (error) { fail(error) } }
async function loadAll() { await Promise.all([loadSession(), loadDashboard(), loadDepartments(), loadEmployees(), loadRecruitment(), loadContent(), loadProcessTemplates()]); if (isItAdmin.value || isHrAdmin.value) { await Promise.all([loadUsers(), loadAuditLogs()]) } syncRouteState() }
async function loadCurrentTab() {
  switch (activeTab.value) {
    case 'dashboard':
      await Promise.all([loadDashboard(), loadJobs(), loadContent()])
      break
    case 'audit':
      await loadAuditLogs()
      break
    case 'users':
      await loadUsers()
      break
    case 'departments':
      await Promise.all([loadDepartments(), loadEmployees()])
      break
    case 'employees':
      await Promise.all([loadEmployees(), loadDepartments(), loadJobs()])
      break
    case 'content':
      await loadContent()
      break
    case 'recruitment':
      await Promise.all([loadRecruitment(), loadDepartments()])
      break
  }
  syncRouteState()
}
async function loadProcessTemplates() { try { processTemplates.value = (await interviewApi.listProcessTemplates({ status: 1 })).data } catch (error) { fail(error) } }
async function saveDepartment() { try { await hrApi.saveDepartment({ ...departmentForm }); ElMessage.success('部门已保存'); await loadAll() } catch (error) { fail(error) } }
async function saveEmployee() { try { await hrApi.saveEmployee({ ...employeeForm }); ElMessage.success('员工已保存'); await loadAll() } catch (error) { fail(error) } }
async function downloadEmployeeTemplate() { try { downloadBlob(await hrApi.employeeTemplate(), 'employee-import-template.xlsx') } catch (error) { fail(error) } }
async function importEmployees({ file }) { try { const result = (await hrApi.importEmployees(file)).data; employeeImportRows.value = result.rows || []; employeeImportVisible.value = true; ElMessage.success(`导入完成：成功 ${result.successCount} 行，失败 ${result.failureCount} 行`); await loadEmployees() } catch (error) { fail(error) } }
function mask(value) { if (!value) return '-'; const text = String(value); return text.length <= 4 ? '****' : `${text.slice(0, 3)}****${text.slice(-4)}` }
async function saveContent() { try { const saved = (await siteContentApi.save({ ...contentForm })).data; Object.assign(contentForm, saved); ElMessage.success(contentForm.published ? '内容已发布' : '草稿已保存'); await loadContentItems() } catch (error) { fail(error) } }
async function saveSiteSettings() {
  const logoUrl = siteSettingsForm.logoUrl.trim()
  if (logoUrl && !safeBrandAssetUrl(logoUrl)) {
    ElMessage.warning('Logo 地址只允许站内绝对路径或 HTTPS 地址')
    return
  }
  siteSettingsSaving.value = true
  try {
    const saved = normalizeSiteSettings((await siteSettingsApi.save(normalizeSiteSettings(siteSettingsForm))).data)
    Object.assign(siteSettingsForm, saved)
    applyLoadedSiteSettings(saved)
    ElMessage.success('站点设置已保存')
  } catch (error) {
    fail(error)
  } finally {
    siteSettingsSaving.value = false
  }
}
async function deleteContent(id) { try { await siteContentApi.remove(id); ElMessage.success('内容已删除'); if (contentForm.id === id) resetContentForm(); await loadContentItems() } catch (error) { fail(error) } }
function resetContentForm() { Object.assign(contentForm, { id: null, type: 'announcement', title: '', summary: '', content: '', cover: '', published: false, publishedAt: '' }) }
function editContent(row) { Object.assign(contentForm, row) }
async function deleteDepartment(id) { try { await hrApi.deleteDepartment(id); ElMessage.success('部门已删除'); if (departmentForm.id === id) resetDepartmentForm(); await loadAll() } catch (error) { fail(error) } }
async function deleteEmployee(id) { try { await hrApi.deleteEmployee(id); ElMessage.success('员工已删除'); if (employeeForm.id === id) resetEmployeeForm(); await loadAll() } catch (error) { fail(error) } }
function resetJobForm() { Object.assign(jobForm, { id: null, jobTitle: '', jobCode: '', departmentId: null, departmentName: '', workLocation: '', jobType: '全职', headcount: 1, requirements: '', responsibilities: '', salaryRange: '', defaultOvertimeRate: 0, status: 1 }) }
function showCreateJob() { resetJobForm(); recruitmentMode.value = 'jobCreate' }
function syncJobDepartmentName(departmentId) { jobForm.departmentName = departments.value.find((item) => item.id === departmentId)?.departmentName || '' }
function editJob(row) { Object.assign(jobForm, row); if (!jobForm.departmentId) { jobForm.departmentId = departments.value.find((item) => item.departmentName === row.departmentName)?.id || null } syncJobDepartmentName(jobForm.departmentId); recruitmentMode.value = 'jobEdit' }
async function saveJob() { try { syncJobDepartmentName(jobForm.departmentId); await recruitmentApi.saveJob({ ...jobForm }); ElMessage.success('招聘岗位已保存'); resetJobForm(); await loadRecruitment() } catch (error) { fail(error) } }
async function deleteJob(id) { try { await recruitmentApi.deleteJob(id); ElMessage.success('岗位已删除'); resetJobForm(); await loadRecruitment() } catch (error) { fail(error) } }
function resetAuditFilter() { Object.assign(auditFilter, { moduleCode: '', actionCode: '', keyword: '' }); loadAuditLogs() }
function resetDepartmentFilter() { Object.assign(departmentFilter, { parentDepartmentId: null, status: null, keyword: '' }); loadDepartments() }
function resetEmployeeFilter() { Object.assign(employeeFilter, { departmentId: null, employmentStatus: null, name: '', employeeCode: '', mobilePhone: '', mobileExact: false, keyword: '' }); loadEmployees() }
function resetJobFilter() { Object.assign(jobFilter, { status: null, departmentName: '', jobType: '', keyword: '' }); loadJobs() }
function resetCandidateFilter() { Object.assign(candidateFilter, { jobId: null, status: '', interviewStageStatus: '', keyword: '' }); loadCandidates() }
function resetDepartmentForm() { Object.assign(departmentForm, { id: null, departmentName: '', departmentCode: '', parentDepartmentId: null, managerEmployeeId: null, description: '', sortOrder: 0, status: 1 }) }
function showCreateDepartment() { resetDepartmentForm(); departmentMode.value = 'create' }
function editDepartment(row) { Object.assign(departmentForm, row); if (departmentForm.parentDepartmentId === departmentForm.id) departmentForm.parentDepartmentId = null; departmentMode.value = 'edit' }
function resetEmployeeForm() { Object.assign(employeeForm, { id: null, employeeCode: '', fullName: '', idCardNo: '', mobilePhone: '', recruitmentMajor: '', positionName: '', jobId: null, baseSalary: null, salaryChangeReason: '', overtimeRate: null, departmentId: null, employmentStatus: 1, bankAccountNo: '', bankName: '', dismissalReason: null, dismissalDate: null }) }
function showCreateEmployee() { resetEmployeeForm(); employeeMode.value = 'create' }
function editEmployee(row) { Object.assign(employeeForm, row); employeeMode.value = 'edit' }
function openUser(row) { router.push(`/admin/users/${row.id}`) }
function openDepartment(row) { router.push(`/admin/departments/${row.id}`) }
function openEmployee(row) { router.push(`/admin/employees/${row.id}`) }
function openJob(row) { router.push(`/admin/recruitment/jobs/${row.id}`) }
function openCandidate(row) { router.push(`/admin/recruitment/candidates/${row.id}`) }
async function openResume(id) { try { await recruitmentApi.openResume(id) } catch (error) { fail(error) } }
function resumeLlmStatusLabel(status) { return ({ PENDING: '评分中', COMPLETED: '已完成', FAILED: '评分失败' })[status] || '-' }
function canReevaluateResumeLlm(candidate) { return candidate?.resumeLlmStatus !== 'PENDING' }
function resumeLlmReevaluateLabel(candidate) { return canReevaluateResumeLlm(candidate) ? 'AI简历重评' : '评分中不可重评' }
async function startCandidateInterview(candidate) {
  if (!enabledProcessTemplates.value.length) {
    await loadProcessTemplates()
  }
  selectedInterviewCandidate.value = candidate
  selectedTemplateId.value = null
  templateDialogVisible.value = true
}
function templateStageSummary(template) { return (template?.stages || []).map((stage) => stage.stageName || (stage.stageType === 'AI' ? 'AI 面试' : '视频面试')).join(' -> ') || '暂无阶段' }
async function confirmStartCandidateInterview() {
  const candidate = selectedInterviewCandidate.value
  if (!candidate) return
  startingInterview.value = true
  try {
    const userList = (await authApi.listUsers({ roleCode: 'INTERVIEWEE', keyword: candidate.mobilePhone })).data
    const interviewee = userList.find((item) => item.mobilePhone === candidate.mobilePhone)
    if (!interviewee) {
      ElMessage.warning('未找到对应面试者账号，请先注册并完善资料')
      return
    }
    await interviewApi.startProcess({ recruitmentCandidateId: candidate.id, intervieweeUserId: interviewee.id, jobId: candidate.jobId, templateId: selectedTemplateId.value, aiThresholdScore: 70, aiFollowUpThreshold: 70, aiMinQuestionRounds: 5, aiMaxQuestionRounds: 10, antiCheatSwitchLimit: 5 })
    ElMessage.success('面试流程已发起')
    templateDialogVisible.value = false
    await loadRecruitment()
  } catch (error) { fail(error) } finally { startingInterview.value = false }
}
async function rejectCandidateResume(id) {
  try {
    await recruitmentApi.rejectCandidateResume(id)
    ElMessage.success('已拒绝该报名者简历面试')
    await loadRecruitment()
  } catch (error) { fail(error) }
}
async function reevaluateResumeLlm(id) {
  try {
    await recruitmentApi.reevaluateResumeLlm(id)
    ElMessage.success('已提交AI简历重评')
    await loadRecruitment()
  } catch (error) { fail(error) }
}
async function deleteCandidate(id) {
  try {
    await recruitmentApi.deleteCandidate(id)
    ElMessage.success('候选人已删除')
    await loadRecruitment()
  } catch (error) { fail(error) }
}
function editUser(row) { Object.assign(userForm, row, { newPassword: '' }) }
async function saveUser() { try { await authApi.updateUser(userForm.id, { roleCode: userForm.roleCode, status: userForm.status, displayName: userForm.displayName, mobilePhone: userForm.mobilePhone, email: userForm.email }); ElMessage.success('用户已保存'); await loadUsers() } catch (error) { fail(error) } }
async function resetUserPassword() { try { if (!userForm.id) { ElMessage.warning('请先选择用户'); return } if (!isStrongPassword(userForm.newPassword)) { ElMessage.warning(strongPasswordMessage); return } await authApi.updateUser(userForm.id, { newPassword: userForm.newPassword }); userForm.newPassword = ''; ElMessage.success('密码已重置'); await loadUsers() } catch (error) { fail(error) } }
async function deleteUser(row) {
  try {
    await ElMessageBox.confirm(`删除用户“${row.username}”后无法恢复。`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await authApi.deleteUser(row.id)
    if (userForm.id === row.id) Object.assign(userForm, { id: null, username: '', displayName: '', roleCode: 'HR_USER', status: 1, mobilePhone: '', email: '', newPassword: '' })
    ElMessage.success('用户已删除')
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') fail(error)
  }
}
function cleanParams(source) { return Object.fromEntries(Object.entries(source).filter(([, value]) => value !== '' && value !== null && value !== undefined)) }
function resolveActionCode(value) {
  if (!value) return ''
  return Object.entries(actionLabels).find(([key, label]) => key === value || label.includes(value))?.[0] || value
}

onMounted(async () => {
  await loadSession()
  consoleReady.value = true
  await loadCurrentTab()
})
watch(() => route.fullPath, () => {
  if (consoleReady.value) {
    void loadCurrentTab()
  } else {
    syncRouteState()
  }
}, { immediate: true })
watch(() => siteSettingsForm.logoUrl, () => { siteLogoPreviewFailed.value = false })

function syncRouteState() {
  recruitmentMode.value = route.meta.recruitmentMode || recruitmentMode.value
  const id = Number(route.params.id)
  if (!id) return
  if (activeTab.value === 'departments') {
    const row = departments.value.find((item) => item.id === id)
    if (row) editDepartment(row)
  } else if (activeTab.value === 'employees') {
    void hrApi.getEmployee(id).then((response) => editEmployee(response.data)).catch(fail)
  } else if (activeTab.value === 'users') {
    const row = users.value.find((item) => item.id === id)
    if (row) editUser(row)
  } else if (route.name === 'admin-recruitment-job-detail') {
    const row = jobs.value.find((item) => item.id === id)
    if (row) editJob(row)
  }
}
</script>

<style scoped>
.console-main { min-width: 0; min-height: calc(100vh - 61px); display: grid; gap: 18px; max-width: 1440px; margin: 0 auto; padding: 30px 28px 48px; }
.console-main > .page-card { width: 100%; }
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 20px; }
.topline h2 { margin: 6px 0 0; }
.metric-grid { display: grid; grid-template-columns: repeat(3, minmax(180px, 1fr)); gap: 14px; }
.metric { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 20px; box-shadow: 0 1px 2px rgba(23,33,31,0.04); }
.metric span { display: block; color: var(--text-muted); font-size: 13px; margin-bottom: 8px; }
.metric strong { font-size: 28px; font-weight: 800; color: var(--ink); }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; margin-top: 18px; }
.wide { grid-column: 1 / -1; }
.sub-tabs { margin-bottom: 18px; }
.action-row { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 4px; }
.resume-link { color: var(--primary); font-weight: 600; text-decoration: none; }
.candidate-detail { margin-top: 18px; padding: 18px; border-radius: var(--radius-md); background: var(--surface-soft); border: 1px solid var(--border); }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.detail-grid div, .intro-box { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 12px; }
.detail-grid span, .intro-box span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 5px; }
.intro-box { margin: 12px 0; }
.intro-box p { margin: 0; line-height: 1.7; }
.data-table { margin-top: 18px; }
.audit-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.audit-panel { min-width: 0; overflow: hidden; padding: 14px; border-radius: var(--radius-md); background: var(--surface-soft); border: 1px solid var(--border); }
.audit-panel-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.audit-panel-head h3 { margin: 0; }
.audit-panel-head span { color: var(--text-muted); font-weight: 600; font-size: 13px; }
.compact-table { margin-top: 12px; }
.site-settings-section, .announcement-section { padding-top: 24px; border-top: 1px solid var(--border); }
.announcement-section { margin-top: 30px; }
.section-intro { display: flex; align-items: start; justify-content: space-between; gap: 16px; }
.section-intro h3 { margin: 0; font-size: 18px; }
.section-intro p { margin: 7px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.65; }
.site-settings-layout { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(260px, .75fr); gap: 28px; margin-top: 22px; }
.site-settings-form { min-width: 0; }
.site-settings-preview { min-width: 0; padding: 22px 0 0 24px; border-left: 1px solid var(--border); }
.preview-brand { display: flex; align-items: center; gap: 11px; }
.preview-brand-mark { display: inline-grid; flex: 0 0 auto; width: 36px; height: 36px; place-items: center; overflow: hidden; border-radius: var(--radius-sm); background: var(--primary); color: #fff; font-size: 11px; font-weight: 800; }
.preview-brand-mark img { display: block; width: 100%; height: 100%; padding: 2px; object-fit: contain; }
.preview-brand strong { min-width: 0; overflow-wrap: anywhere; }
.site-settings-subtitle { margin: 28px 0; color: var(--ink-soft); font-size: 17px; line-height: 1.65; overflow-wrap: anywhere; }
.site-settings-footer { margin: 0; padding-top: 16px; border-top: 1px solid var(--border); color: var(--text-muted); font-size: 12px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
.content-editor-layout { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(240px, .75fr); gap: 22px; margin-top: 24px; }
.content-type-options { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); width: 100%; }
.content-type-options :deep(.el-radio-button) { width: 100%; }
.content-type-options :deep(.el-radio-button__inner) { display: flex; align-items: center; justify-content: center; width: 100%; min-height: 38px; padding: 8px 10px; border-color: var(--border); border-radius: 0; background: var(--surface); color: var(--ink-soft); font: inherit; font-size: 14px; font-weight: 600; letter-spacing: .01em; box-shadow: none; transition: background-color .15s ease, border-color .15s ease, color .15s ease; }
.content-type-options :deep(.el-radio-button:first-child .el-radio-button__inner) { border-radius: var(--radius-sm) 0 0 var(--radius-sm); }
.content-type-options :deep(.el-radio-button:last-child .el-radio-button__inner) { border-radius: 0 var(--radius-sm) var(--radius-sm) 0; }
.content-type-options :deep(.el-radio-button__inner:hover) { color: var(--primary); background: var(--primary-soft); }
.content-type-options :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--primary); background: var(--primary); color: #ffffff; box-shadow: -1px 0 0 0 var(--primary); }
.content-type-options :deep(.el-radio-button__original-radio:focus-visible + .el-radio-button__inner) { outline: 2px solid var(--primary-ring); outline-offset: 2px; }
.content-form { min-width: 0; padding: 20px; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface-soft); }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.content-preview { min-width: 0; min-height: 280px; padding: 22px; border: 1px solid #cbd7d1; border-radius: var(--radius-md); background: #f5f8f5; }
.preview-label { color: #175c50; font-size: 11px; font-weight: 800; letter-spacing: .13em; text-transform: uppercase; }.preview-date { margin: 34px 0 12px; color: #7b8781; font-size: 12px; }.content-preview h3 { margin: 0 0 11px; font-size: 22px; }.content-preview p:not(.preview-date) { color: #68766f; line-height: 1.7; }.publish-state { display: inline-flex; margin-top: 22px; padding: 5px 9px; border-radius: 999px; background: #e9edf0; color: #66737c; font-size: 12px; }.publish-state.published { color: #175c50; background: #dceee7; }
.content-table-head { display: flex; align-items: center; justify-content: space-between; margin-top: 30px; }.content-table-head h3 { margin: 0; }.content-table-head span { color: var(--text-muted); font-size: 13px; }
.dialog-intro { margin: 0 0 18px; color: var(--text-muted); line-height: 1.7; }.template-preview { display: grid; gap: 6px; padding: 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface-soft); }.template-preview span, .template-option-detail { color: var(--text-muted); font-size: 12px; }.template-option-detail { display: block; margin-top: 3px; }
@media (max-width: 1200px) { .audit-grid { grid-template-columns: 1fr; } }
@media (max-width: 1200px) { .metric-grid { grid-template-columns: repeat(2, minmax(180px, 1fr)); } }
@media (max-width: 980px) { .form-grid, .content-editor-layout, .site-settings-layout { grid-template-columns: 1fr; } .site-settings-preview { padding: 22px 0 0; border-top: 1px solid var(--border); border-left: 0; } }
@media (max-width: 900px) { .console-main { padding: 22px 14px 36px; } }
@media (max-width: 640px) { .form-row { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .metric-grid { grid-template-columns: 1fr; } }
</style>
