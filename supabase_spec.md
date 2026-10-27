# Studdy — Existing Supabase Backend Connection Specification

> **Purpose:** connect a new native Android Java/XML client to the **same deployed Supabase project**. This is not a schema proposal and does not recreate the database.
>
> **Verification date:** 4 July 2026. Connection/project details, schema, constraints, RLS, Realtime publication, Storage, functions, and Auth settings were read from the live project. Client query patterns were audited from the Flutter repository.
>
> **Scope:** the five application tables in the deployed `public` schema, the application Storage bucket/policies, Auth configuration, application/public functions, and client-facing Realtime behavior. Supabase-owned internal tables in `auth`, `storage`, `realtime`, and other platform schemas are managed infrastructure and are not application tables to reproduce.

## 1. Supabase Project Connection Details

| Setting | Exact value |
|---|---|
| Project name | `studdy` |
| Project URL | `https://odbycjunebfncpkkbbew.supabase.co` |
| Project ID / reference | `odbycjunebfncpkkbbew` |
| Region | `ap-south-1` |
| Database host | `db.odbycjunebfncpkkbbew.supabase.co` |
| Database engine | PostgreSQL 17 (`17.6.1.084` reported by project metadata) |
| Project status at verification | `ACTIVE_HEALTHY` |

### Client API keys

Both enabled client-safe keys below address the same project. Prefer the modern publishable key for a new Android client; the legacy anon key remains enabled for compatibility with the Flutter client.

**Modern publishable key (recommended for new client):**

```text
<redacted-publishable-key>
```

**Legacy anon key:**

```text
<redacted-anon-jwt>
```

No `service_role`, `sb_secret_…`, or `SUPABASE_SERVICE_ROLE_KEY` value was found in the repository. A service-role key is therefore **not included**. It must never be embedded in an Android application.

### Provenance caveat

The working-tree `.env` does **not** contain the live values; it currently contains literal placeholders:

```dotenv
SUPABASE_URL=your_supabase_url
SUPABASE_ANON_KEY=your_supabase_anon_key
```

The exact live values above were verified through the connected Supabase project named `studdy`, whose reference also matches its URL and legacy anon JWT claim.

### Required request headers for direct REST

For an unauthenticated/student request:

```http
apikey: <publishable-or-anon-key>
Authorization: Bearer <same-publishable-or-anon-key>
Content-Type: application/json
```

For an authenticated teacher request, retain the `apikey` header and replace the bearer value with the teacher's current Auth access token:

```http
apikey: <publishable-or-anon-key>
Authorization: Bearer <teacher-access-token>
```

Base endpoints:

- Data API: `https://odbycjunebfncpkkbbew.supabase.co/rest/v1`
- Auth: `https://odbycjunebfncpkkbbew.supabase.co/auth/v1`
- Storage: `https://odbycjunebfncpkkbbew.supabase.co/storage/v1`
- Realtime WebSocket: `wss://odbycjunebfncpkkbbew.supabase.co/realtime/v1/websocket`

## 2. Complete Deployed Application Database Schema

### Schema-wide facts

- Application schema: `public`.
- Application tables: `exams`, `students`, `results`, `exam_sessions`, `generated_papers`.
- All five tables have RLS enabled and `FORCE ROW LEVEL SECURITY` disabled.
- There are no deployed foreign-key constraints on these tables.
- There are no application-defined enum types in `public`; all categorical values are plain `text`.
- There are no deployed check or exclusion constraints on these tables.
- At verification time all five application tables contained zero rows. The Auth system contained one user/email identity.

### 2.1 `public.exams`

| Position | Column | Exact type | Nullable | Default / identity |
|---:|---|---|---:|---|
| 1 | `id` | `uuid` | No | `gen_random_uuid()` |
| 2 | `code` | `text` | Yes | none |
| 3 | `title` | `text` | Yes | none |
| 4 | `questions` | `jsonb` | Yes | none |
| 5 | `created_at` | `timestamp with time zone` (`timestamptz`) | No | `now()` |
| 6 | `result_mode` | `text` | No | `'instant'::text` |
| 7 | `results_published` | `boolean` | No | `false` |
| 8 | `timer_mode` | `text` | Yes | `'none'::text` |
| 9 | `duration_minutes` | `integer` (`int4`) | Yes | none |
| 10 | `window_start` | `timestamp with time zone` (`timestamptz`) | Yes | none |
| 11 | `window_end` | `timestamp with time zone` (`timestamptz`) | Yes | none |
| 12 | `teacher_id` | `text` | Yes | none |

Constraints and indexes:

- Primary key constraint `exams_pkey`: `PRIMARY KEY (id)`.
- Index `exams_pkey`: `CREATE UNIQUE INDEX exams_pkey ON public.exams USING btree (id)`.
- No unique constraint/index on `code`.
- No foreign keys.

Values used by the app:

- `result_mode`: `instant` or `manual`.
- `timer_mode`: `none`, `duration`, or `window`.
- `teacher_id`: string form of `auth.uid()` for teacher-created rows.
- `questions`: ordered JSON array:

```json
[
  {
    "id": "0",
    "questionText": "Question text",
    "options": ["A", "B", "C", "D"],
    "correctIndex": 0
  }
]
```

### 2.2 `public.students`

| Position | Column | Exact type | Nullable | Default / identity |
|---:|---|---|---:|---|
| 1 | `id` | `uuid` | No | `gen_random_uuid()` |
| 2 | `enrollment_number` | `text` | No | none |
| 3 | `name` | `text` | Yes | none |
| 4 | `created_at` | `timestamp with time zone` (`timestamptz`) | No | `now()` |

Constraints and indexes:

- Primary key `students_pkey`: `PRIMARY KEY (id)`.
- Unique constraint `students_enrollment_number_key`: `UNIQUE (enrollment_number)`.
- Indexes:
  - `CREATE UNIQUE INDEX students_pkey ON public.students USING btree (id)`.
  - `CREATE UNIQUE INDEX students_enrollment_number_key ON public.students USING btree (enrollment_number)`.
- No foreign keys.

### 2.3 `public.results`

| Position | Column | Exact type | Nullable | Default / identity |
|---:|---|---|---:|---|
| 1 | `id` | `uuid` | No | `gen_random_uuid()` |