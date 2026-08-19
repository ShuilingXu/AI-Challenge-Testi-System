# AI School Examination System

A web application for class-scoped AI examinations. Teachers maintain the class roster, knowledge bases, and exam templates; students enter with their class, name, and student number to take an AI-generated written examination and review their learning analysis.

## What It Does

- Maintains classes and student rosters, including `.xlsx` imports.
- Maintains knowledge bases and reusable AI examination templates.
- Lets templates assign a knowledge point to each answer round.
- Publishes a time-bounded exam to one class or all active students.
- Lets a student enter without a pre-issued password using a verified roster record.
- Generates questions, scores direct written answers, and completes the exam after the configured final round.
- Reports each student's score rate, loss rate, completed rounds, knowledge-point performance, and AI learning summary.
- Aggregates completed attempts into class score/loss rates, knowledge-point analytics, student rows, and an AI class summary.

The former HR, recruiting, resume, and video-interview routes are not exposed by the current security policy. Some internal interview-process entities remain as the execution engine for the school examination lifecycle.

## Roles

| Role | School use |
| --- | --- |
| `IT_ADMIN` | School system administrator and teacher workspace administrator |
| `HR_ADMIN` | Teacher workspace administrator |
| `HR_USER` | Teacher workspace user |
| `INTERVIEWEE` | Student examination account created during roster-verified entry |

The existing role codes are retained for data compatibility. In the school UI, the first three roles are teacher/administrator roles and `INTERVIEWEE` is used only for the student examination experience.

## Teacher Workflow

1. Sign in at `/login` and change the initial password when prompted.
2. Create or import classes at `/admin/classes`.
3. Create or import students at `/admin/students`.
4. Maintain knowledge bases and optional per-round templates at `/admin/knowledge`.
5. Create an exam at `/admin/exams`, select its class, knowledge base or template, question-round count, pass score, and publication window, then set it to `PUBLISHED`.
6. Monitor completed attempts and mastery analytics at `/admin/analytics`.

IT administrators can configure the OpenAI-compatible endpoint, model, default prompt, and per-function prompt overrides at `/admin/settings`. The function overrides cover question/follow-up generation, answer scoring, and learning summaries; leave an override blank to inherit the default prompt. Changes are written to `.env` and take effect after restarting the backend.

Class import columns, after the header row: `majorName`, `className`, `classCode`, `description`.

Student import columns, after the header row: `studentNo`, `fullName`, `classCode`. Keep student numbers formatted as text in Excel.

## Student Workflow

1. Open `/student/register`.
2. Select the class and enter the exact roster name and student number.
3. Open an available exam from `/student`.
4. Answer each AI question directly in the browser. The configured final round automatically completes the exam; no teacher approval step is required.
5. Review the score rate, loss rate, knowledge-point performance, and AI learning summary in the examination history.

Each student can have only one attempt for an exam. Reopening an existing attempt resumes it rather than starting a second one.

## AI Configuration

Set these variables in a local `.env` file or the deployment environment. The real key must never be committed.

```dotenv
JWT_SECRET=replace-with-a-unique-secret-of-at-least-32-characters
SCHOOL_LLM_BASE_URL=https://your-openai-compatible-provider/v1
SCHOOL_LLM_MODEL=your-model-name
SCHOOL_LLM_API_KEY=your-api-key
```

The provider must expose an OpenAI-compatible `/chat/completions` endpoint. The school LLM configuration is used for question generation, answer evaluation, student summaries, and class summaries. When legacy LLM records are absent, the school configuration is used as the execution fallback.

For production, configure Redis as well. CAPTCHA issuance, login verification, and rate limiting require it.

```dotenv
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
DB_TYPE=pgsql
DB_URL=jdbc:postgresql://127.0.0.1:5432/school_exam
DB_USERNAME=school_exam
DB_PASSWORD=replace-this-password
DB_FALLBACK_ENABLED=false
```

See `.env.example` for all supported settings.

## Local Development

Requirements:

- JDK 17 or later
- Maven 3.8 or later
- Node.js 20.19 or later
- npm 9 or later

Start the backend:

```bash
cd backend
mvn spring-boot:run
```

Start the frontend in another terminal:

```bash
cd frontend
npm install
npm run dev
```

The backend runs on `http://localhost:8081` and the Vite frontend runs on `http://localhost:3000`. The development profile uses SQLite when a database URL is not supplied; schema migrations create the school tables automatically.

Default bootstrap administrator accounts are `itadmin`, `hradmin`, and `hruser`, each initially using `123456`. They must change the initial password unless explicitly exempted through deployment configuration.

## Key API Areas

| Area | Endpoint prefix |
| --- | --- |
| Public class list and student entry | `/api/exams/classes`, `/api/exams/student-registration` |
| Teacher class, student, exam, and analytics management | `/api/exams/admin/**` |
| Student exams, attempts, and analysis | `/api/exams/student/**` |
| Knowledge bases and examination templates | `/api/interview/hr/knowledge-bases`, `/api/interview/hr/process-templates` |
| Student question and answer execution | `/api/interview/interviewee/**` |

All state-changing API calls use the `AUTOHR_CSRF` double-submit token. Authentication is JWT-backed and carried by a secure session cookie in production.

## Verification

```bash
cd backend
mvn test

cd ../frontend
npm run build
```

The backend test suite includes school service and controller security coverage, including roster registration, Excel import validation, score/loss-rate analysis, low passing-score behavior, school LLM fallback, final-round completion, and per-round template knowledge-point persistence.
