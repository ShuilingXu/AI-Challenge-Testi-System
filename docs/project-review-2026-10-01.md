# 项目全面审查（2026-10-01）

## 范围与结论

审查当前工作区的前端、认证与权限、考试生命周期和评分、监控录像、配置与文件存储、数据库迁移及发布部署。三个子代理分别审查前端、考试后端和部署配置，主代理核对认证逻辑、触发路径和测试结果。本次未修改业务代码或部署环境。

发现 11 项问题：2 项 P1、9 项 P2。P1 表示应优先处理的发布或迁移阻断；P2 表示在具体条件下发生的功能、安全或可靠性问题。下列结论来自代码路径核对；额外动态验证及未验证的环境见末尾。

## 1. [P1] 初始密码会话拦截了改密页面和静态资源

- 位置：`backend/src/main/java/com/autohr/modules/auth/config/PasswordChangeRequiredFilter.java:35–48`。
- 条件：使用 JAR 内嵌前端的发布版，用户已登录且 `mustChangePassword=1`。
- 触发：刷新 `/change-password`，或直接打开 `/login`。过滤器只放行少数认证 API，对上述 HTML 路由、`/index.html` 和 `/assets/**` 同样返回 JSON 403，前端无法加载。SecurityConfig 的 `permitAll` 无法绕过提前返回的过滤器。前端收到改密要求后也会执行全文档跳转，触发相同问题。
- 影响：强制改密账号在刷新、恢复登录等情形下无法进入改密界面。Vite 单独服务静态资源的开发环境掩盖此问题。
- 建议：将强制改密检查限定于受保护业务 API，允许 SPA 页面、静态资源和所需公开设置加载；保留业务 API 阻断。增加带真实 JWT 的发布路由与资源加载测试。

## 2. [P1] SQLite → PostgreSQL 迁移脚本无法运行

- 位置：`scripts/migrate-sqlite-to-postgres.py:22–30,67–76`。
- 条件：安装 psycopg 后执行任意脚本命令。
- 触发：脚本在模块加载时校验表清单。当前 `schema.sql` 有 26 张表，清单只有 24 张，遗漏 `school_class_teacher` 和 `school_exam_recording`，因此直接抛出 RuntimeError；`--dry-run` 和 `--help` 也无法到达参数解析。
- 影响：现有迁移入口完全阻断。仅修改固定计数不能修复遗漏的数据迁移。
- 建议：补全清单及关系处理，增加对 schema 清单一致性、带教师关联与录像元数据的迁移测试。录像实体文件或对象还应有明确迁移策略。
- 验证：使用相同正则和集合比较确认缺失两表，未执行真实 PostgreSQL 数据迁移。

## 3. [P2] 删除并重建同用户名后，旧 JWT 可绑定到新账号

- 位置：`backend/src/main/java/com/autohr/modules/auth/config/JwtAuthenticationFilter.java:46–58`。
- 条件：旧 JWT 尚未过期、其 tokenVersion 为 0；原账号删除后同用户名被重建，初始化 tokenVersion 也为 0。
- 触发路径：学生登记创建 `student_<学号>` 账号（`SchoolExamService.java:767–781,1097–1099`）；没有考试记录的学生删除时会删除账号（`:195–215`）；重新建档并登记同学号会创建新的用户 ID，但同一用户名。JWT 校验只按用户名查账号并比较 tokenVersion，未比较已签名的 `userId`。
- 影响：原学生保留的有效会话可作为新学生账号通过认证，访问新账号的考试。相同用户名重建的教职工也存在身份复用风险，但其强制改密规则会限制部分操作。
- 建议：必须比较 JWT 的 userId 与数据库用户 ID，并要求有效的版本字段；测试“删除、重建同用户名、使用旧 token”的完整链路。本项为静态路径确认，未运行攻击性 HTTP 复现。

## 4. [P2] 已自然结束的考试执行“继续”后没有题可答

- 位置：`backend/src/main/java/com/autohr/modules/interview/service/impl/InterviewServiceImpl.java:1952,1998–2001,2767–2776`。
- 条件：自然及格或到达最大轮数而结束的考试，全部题目已评分完成。
- 触发：管理页对非进行中考试显示“继续考试”（`SchoolAdminView.vue:74`），对应 `/api/exams/admin/attempts/{processId}/continue`。后端将流程恢复为 IN_PROGRESS，并调用初始出题函数；该函数发现第一条历史题存在便返回，不生成新题。后续取题只查未答或失败题，始终返回 null。
- 影响：学生一直停留在准备下一题，已结束记录被变成无法完成的进行中记录。
- 建议：继续操作只允许有恢复断点的中止记录；自然完成记录应拒绝继续或走明确的追加题流程，同时处理轮数上限。增加无未答题的继续测试。

## 5. [P2] 多阶段考试复核分数后不更新阶段和终局结论

- 位置：`backend/src/main/java/com/autohr/modules/school/service/SchoolExamService.java:691–709`。
- 条件：合法的多个 AI 阶段模板考试，教师人工复核改变阶段是否及格。
- 触发：分数和流程平均分会更新，但只有 `stageCount <= 1` 才更新及格状态。多阶段考试最后阶段被改至不及格后仍保留 COMPLETED/PASSED；原先早期阶段不及格的记录改至及格后也保留 REJECTED。
- 影响：复核后的分数与考试结论不一致，影响教师判断和学生结果显示。
- 建议：按被复核题所在阶段重算结果，并依据阶段流程规则同步终局；早期失败阶段改为及格时明确后续阶段如何恢复。不要简单按全流程均分替代阶段规则。

## 6. [P2] 结束时录像上传失败被吞掉，无法在页面重试

- 位置：`frontend/src/composables/useExamMonitoring.js:83–86`；`frontend/src/views/ExamTakeView.vue:10,34–35`。
- 条件：考试结束，最终片段上传连续三次失败。
- 触发：上传队列 catch 仅设置 error，`stop()` 仍正常 resolve。结束状态会隐藏唯一显示该错误和授权按钮的门禁；授权函数禁止已结束考试重试。待上传 Blob 只在内存中。
- 影响：页面表现为考试成功结束，学生离开或刷新后未上传的最后片段丢失，录像证据不完整。
- 建议：将完成答题与完成录像上传状态分开；结束页保留上传状态和重试入口，必要时持久保存待上传片段，并明确清理策略。
- 验证：子代理用 Node stub 执行原 composable，全部上传失败时得到 `start=true, stopResolved=true, uploadAttempts=3`。

## 7. [P2] 列表固定读取前 200 条，后续记录无法操作

- 位置：`frontend/src/services/api.js:94–102`；`frontend/src/views/StaffManagementView.vue:52`；`frontend/src/views/KnowledgeTemplateView.vue:77–79`。
- 条件：可见教职工、知识库、模板或某知识库条目超过 200 条。
- 触发：默认请求 page=1、pageSize=200；调用者仅使用 data 数组，没有翻页，也不使用返回的 pagination。教职工页没有服务器搜索入口，知识库和模板下拉同样遗漏后续记录。
- 影响：第 201 条及之后的教职工不能在页面编辑或重置密码，后续知识内容、模板无法正常维护或选择。
- 建议：表格提供服务器分页和搜索；选择器提供搜索分页或按需加载。后端最大 pageSize 为 200，单纯增大参数无效。

## 8. [P2] 保存单题复核会覆盖其他题未保存的草稿

- 位置：`frontend/src/views/ScoreReviewView.vue:53–55,64–66`。
- 触发：教师先编辑两题的分数或注释，再保存其中一题。保存接口返回整个 attempt，setDetail 重建所有题目的 drafts，另一题的编辑被服务端旧值覆盖。
- 影响：无提示丢失教师输入，可能误以为其他修改仍保留。
- 建议：只同步已保存题目的草稿，保留其他 dirty 草稿；或者提供明确的整体保存方式。

## 9. [P2] systemd 环境变量使部分配置保存后继续使用旧值

- 位置：`backend/src/main/java/com/autohr/modules/system/service/SystemConfigService.java:45–58`；`scripts/auto-hr.service:13`；`SystemConfigController.java:88–91`。
- 条件：使用 systemd 部署，`.env` 已有 S3/SMTP 等配置，在配置 API 中更新已有值。
- 触发：服务将 `.env` 注入进程环境，而 loadConfig 优先读进程环境。保存只修改文件；POST 响应拼入新值并声称立即重新加载，但再次 GET 和仍使用 loadConfig 的组件读到旧值。学校 LLM 使用 loadFileConfig 的专门重载，未覆盖其他组件。
- 影响：可达配置 API 的保存结果与实际运行状态不一致；S3 等组件在使用时保持旧配置。当前前端只展示学校 LLM 配置，其他配置主要涉及保留 API，优先级低于主考试流程。
- 建议：明确热更新项与需重启项，统一有效配置来源；无法立即生效的配置应准确返回重启要求。测试继承环境变量存在时保存后读取行为。

## 10. [P2] systemd 安装目录不支持默认 SQLite 写入

- 位置：`scripts/install-release.sh:72–75`；`scripts/auto-hr.service:12`；`backend/src/main/resources/application.yml:130`。
- 条件：设置 DB_TYPE=sqlite，DB_URL 不填或使用默认相对路径。
- 触发：服务工作目录 `/opt/auto-hr` 为 root 所有、0750，autohr 用户不能写；默认数据库 school_exam.db 位于该目录。新安装无法创建数据库；旧文件即使归 autohr 所有，也无法创建同目录 journal/WAL。
- 影响：SQLite 发布部署无法正常初始化或执行写事务。显式使用可写目录的绝对 DB_URL 可规避。
- 建议：为数据库建立专用可写目录并配置绝对路径，升级时迁移或保留已有数据库，验证数据库文件及日志文件的创建权限。

## 11. [P2] Ubuntu 首次部署存在两个独立启动阻断点

- DB_TYPE 位置：`deploy-ubuntu.sh:303,432–435,548`；`application-prod.yml:10`。首次复制的模板中 DB_TYPE 为注释；脚本仅在局部变量中默认 sqlite，未写入文件或导出，却启动要求 `${DB_TYPE}` 必填的 prod 配置。即使其他依赖通过，数据库类型仍无法绑定。建议持久写入默认值或要求部署前填写。
- Redis 位置：`deploy-ubuntu.sh:409–428`。写入新的 requirepass 后仅执行 `systemctl enable --now`，已经运行的 Redis 不重启。apt 安装自动启动或现有无密码服务的情况下，Redis 仍使用旧配置，接着用新密码 PING 失败，脚本退出。建议明确重启并等待健康检查。
- 此项包含同一部署入口的两个独立修复点；二者均经脚本逻辑核对，未在真实 Linux 服务器执行。

## 验证与审查边界

- 后端：`mvn -Dmaven.repo.local=C:/Users/Admin/.m2/repository -o test -q` 通过；Surefire 汇总 185 tests、0 failures、0 errors、0 skipped。
- 前端：`npm test` 17/17 通过；`npm run build` 通过。
- 后端现有并发测试包含 60 学生、每人两轮、模拟 LLM。它验证当前测试环境下的并发路径，不能代表真实外部模型、生产数据库、网络和录像负载。
- 已排除流式分数泄露疑点：虽然旧 stream 实现发送原始 LLM token，但独立 `/ai-answer/stream` 路由目前被 denyAll 阻断，不列为当前可利用缺陷。
- 已排除学生停用账户通过登记恢复登录疑点：已有账号登记路径明确检查 user.status。
- 未执行真实 PostgreSQL/MySQL、Linux/systemd 安装或外部模型/S3端到端验证；未进行依赖漏洞数据库扫描，因此没有按依赖版本号推断漏洞。
- 测试缺口主要在发布版浏览器恢复、账号删除重建后的会话、自然结束后继续、多阶段复核、结束时录像上传失败、超过200条的列表和部署脚本。建议将相应触发场景作为修复验收条件。

## 建议修复顺序

先处理登录恢复和迁移阻断，再修复 JWT 身份绑定、考试继续与复核状态、录像最终上传；随后修复分页及草稿保留，并补齐部署配置和权限验证。配置 API 的保留功能可在主考试流程修复后处理。

## 修复记录（2026-10-01）

以上问题已落实到当前工作区，未执行生产部署：

1. 强制改密检查限定业务 API，放行页面、资源及公开站点设置；新增真实签名 Cookie 的页面/静态资源回归验证。
2. 迁移清单补齐到26表，并用当前 schema 自动核对。
3. JWT 同时校验用户 ID 和会话版本，拒绝缺字段和同用户名重建后的旧 token。
4. 自然结束且无断点的考试拒绝继续；有未答题的中断可恢复，包括被取消的出题任务。
5. 多阶段复核按各阶段均分和开考时阈值更新结论；早期阶段复核通过后可继续后续阶段；后续阶段结束不会覆盖此前复核的不及格结果。
6. 录像片段立即存入 IndexedDB，成功上传才删除；结束页支持重试，学生主页提供自己的考试录像补传入口，刷新后恢复段号。
7. 现有集合列表及选择器自动读取全部分页，显式传 page 的请求保留服务器分页行为。
8. 单题保存只重置该题草稿，保留其他未保存修改。
9. 配置成功保存后覆盖当前进程继承的旧值，未修改项仍保留环境优先；数据库及其他启动设置准确提示重启要求。
10. 默认 SQLite 使用服务用户可写的 data 目录；升级通过 backup API 复制旧库及 WAL 数据，保留旧库并拒绝双库冲突；自定义 URL 保留。
11. Ubuntu 默认数据库类型写入配置文件；Redis 配置修改后明确重启。

新增回归涵盖上述认证、考试、前端和SQLite迁移路径。真实 Linux/systemd 部署及 PostgreSQL 导入仍需在对应环境验收。

修复后验证：后端全量测试通过；最终多阶段结论保护追加后重跑考试服务回归，Surefire 合计197项、0失败、0错误。前端22项测试及生产构建通过；5项Python脚本回归和3个Bash脚本语法检查通过，git diff --check通过。

### Docker Linux 与真实 PostgreSQL 补充验证

Docker Desktop 本次启动遇到 Inference/Secrets Engine 残留 AF_UNIX socket 错误。仅在进程停止后将两个运行目录重命名备份并重建，未清理容器数据、镜像或磁盘。Docker Linux 引擎恢复可用。这类错误也见 Docker 官方反馈库：https://github.com/docker/desktop-feedback/issues/531 。

使用三个独立测试容器（PostgreSQL16、Redis7、Linux/JDK21），工作区挂载只读：

- 当前可执行 JAR 在 Linux prod 配置下启动，对真实 PostgreSQL 执行应用 schema 初始化；Redis-backed CAPTCHA HTTP200。
- `scripts/test-postgres-migration.py` 对应用初始化后的空测试数据库验证26表迁移、教师关联、录像元数据、中文、序列续号、非法外键事务全回滚及非空数据库拒绝重复导入，均通过。测试入口要求显式 POSTGRES_TEST_DSN，且不会自行清空目标库。
- Linux 内重跑5项SQLite脚本回归及Bash语法检查，均通过。
- `scripts/test-linux-release-permissions.sh` 在一次性容器内使用真实autohr服务用户和root只读应用目录；默认库迁移保留旧数据，data目录可创建SQLite WAL；production release可启动并提供内嵌登录页及CAPTCHA。systemd unit通过systemd-analyze verify。
- 容器不以systemd为PID1，因此尚未验证真实systemctl服务启停及Ubuntu安装器的完整软件包安装过程。录像文件实体搬运及真实外部LLM/S3仍不属于这些测试覆盖。

测试结束后仅清理本次创建的容器、匿名数据卷和网络，Docker引擎保持运行。
