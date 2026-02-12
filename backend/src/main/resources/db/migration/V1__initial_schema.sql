-- SupportPlane initial schema

CREATE TABLE tenants (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(100) NOT NULL UNIQUE,
    active          BOOLEAN DEFAULT TRUE,
    license_tier    VARCHAR(50) DEFAULT 'BASIC',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenants(id),
    keycloak_id     VARCHAR(255) UNIQUE,
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255),
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE clusters (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenants(id),
    cluster_id      VARCHAR(255) NOT NULL,
    name            VARCHAR(255),
    status          VARCHAR(50) DEFAULT 'PENDING',
    otp_validated   BOOLEAN DEFAULT FALSE,
    last_bundle_at  TIMESTAMP,
    metadata        JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cluster_otp (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT REFERENCES clusters(id),
    otp_code        VARCHAR(10) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    used            BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bundles (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT REFERENCES clusters(id),
    bundle_id       VARCHAR(255) UNIQUE,
    filename        VARCHAR(500) NOT NULL,
    filepath        VARCHAR(1000),
    size_bytes      BIGINT DEFAULT 0,
    metadata        JSONB,
    analysis_summary TEXT,
    received_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tickets (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenants(id),
    cluster_id      BIGINT REFERENCES clusters(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    status          VARCHAR(50) DEFAULT 'OPEN',
    priority        VARCHAR(50) DEFAULT 'MEDIUM',
    created_by      BIGINT REFERENCES users(id),
    assigned_to     BIGINT REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ticket_comments (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT REFERENCES tickets(id),
    author_id       BIGINT REFERENCES users(id),
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recommendations (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT REFERENCES clusters(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    severity        VARCHAR(50) DEFAULT 'INFO',
    source          VARCHAR(50) DEFAULT 'OPERATOR',
    status          VARCHAR(50) DEFAULT 'DRAFT',
    created_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenants(id),
    type            VARCHAR(100) NOT NULL,
    channel         VARCHAR(50) NOT NULL DEFAULT 'EMAIL',
    config          JSONB,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE licenses (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenants(id) UNIQUE,
    tier            VARCHAR(50) DEFAULT 'BASIC',
    max_clusters    INTEGER DEFAULT 5,
    max_users       INTEGER DEFAULT 10,
    valid_from      TIMESTAMP,
    valid_until     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_keycloak ON users(keycloak_id);
CREATE INDEX idx_clusters_tenant ON clusters(tenant_id);
CREATE INDEX idx_clusters_cluster_id ON clusters(cluster_id);
CREATE INDEX idx_bundles_cluster ON bundles(cluster_id);
CREATE INDEX idx_bundles_received ON bundles(received_at);
CREATE INDEX idx_tickets_tenant ON tickets(tenant_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_recommendations_cluster ON recommendations(cluster_id);
CREATE INDEX idx_notifications_tenant ON notifications(tenant_id);
