CREATE TABLE users (
 id uuid PRIMARY KEY, display_name varchar(100) NOT NULL, email varchar(254) NOT NULL,
 password_hash varchar(512) NOT NULL, company varchar(120) NOT NULL DEFAULT '', bio varchar(500) NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, deleted_at timestamptz,
 lock_version bigint NOT NULL DEFAULT 0,
 CONSTRAINT users_email_normalized CHECK (email = lower(trim(email)))
);
CREATE UNIQUE INDEX users_email_unique ON users(email);
CREATE TABLE user_sessions (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), digest varchar(64) NOT NULL UNIQUE,
 created_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, remember_me boolean NOT NULL,
 device varchar(240) NOT NULL, kind varchar(16) NOT NULL DEFAULT 'WEB', scope_snapshot_id varchar(80)
);
CREATE INDEX sessions_user_expiry ON user_sessions(user_id, expires_at);
CREATE TABLE snapshots (
 id varchar(80) PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id),
 metadata jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL,
 UNIQUE (id, owner_id)
);
CREATE TABLE projects (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id), name varchar(160) NOT NULL,
 description varchar(2000) NOT NULL DEFAULT '', framework varchar(8) NOT NULL DEFAULT 'REACT',
 snapshot_id varchar(80), metadata jsonb NOT NULL DEFAULT '{}',
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, opened_at timestamptz,
 deleted_at timestamptz, lock_version bigint NOT NULL DEFAULT 0,
 CONSTRAINT projects_framework CHECK (framework IN ('HTML','REACT')),
 FOREIGN KEY (snapshot_id, owner_id) REFERENCES snapshots(id, owner_id),
 UNIQUE (id, owner_id)
);
CREATE INDEX projects_owner_updated ON projects(owner_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE TABLE project_versions (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES projects(id), snapshot_id varchar(80) NOT NULL REFERENCES snapshots(id),
 revision_id varchar(80), design_node jsonb NOT NULL, metadata jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL
);
CREATE INDEX versions_project_created ON project_versions(project_id, created_at DESC);
CREATE TABLE project_assets (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES projects(id), snapshot_id varchar(80) NOT NULL REFERENCES snapshots(id),
 path varchar(512) NOT NULL, byte_size bigint NOT NULL, sha256 varchar(64) NOT NULL, metadata jsonb NOT NULL DEFAULT '{}',
 UNIQUE(project_id, snapshot_id, path)
);
CREATE TABLE exports (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES projects(id), snapshot_id varchar(80) NOT NULL REFERENCES snapshots(id),
 revision_id varchar(80), format varchar(8) NOT NULL, byte_size bigint NOT NULL, created_at timestamptz NOT NULL,
 metadata jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX exports_project_created ON exports(project_id, created_at DESC);
CREATE TABLE password_reset_tokens (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), digest varchar(64) NOT NULL UNIQUE,
 expires_at timestamptz NOT NULL, used_at timestamptz
);
CREATE TABLE ai_conversations (
 id uuid PRIMARY KEY, snapshot_id varchar(80) NOT NULL REFERENCES snapshots(id),
 created_at timestamptz NOT NULL, metadata jsonb NOT NULL DEFAULT '{}'
);
CREATE TABLE ai_messages (
 id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES ai_conversations(id),
 role varchar(16) NOT NULL CHECK (role IN ('user','assistant','system','tool')),
 content jsonb NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX messages_conversation_created ON ai_messages(conversation_id, created_at);
CREATE TABLE legacy_imports (
 source varchar(512) PRIMARY KEY, sha256 varchar(64) NOT NULL, imported_at timestamptz NOT NULL,
 metadata jsonb NOT NULL DEFAULT '{}'
);
