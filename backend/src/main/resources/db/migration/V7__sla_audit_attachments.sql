-- SLA tracking on tickets
ALTER TABLE tickets ADD COLUMN sla_deadline TIMESTAMP;
ALTER TABLE tickets ADD COLUMN sla_breached BOOLEAN DEFAULT FALSE;

-- Audit trail
CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT REFERENCES tenants(id),
    actor           VARCHAR(255),
    actor_role      VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50),
    target_id       VARCHAR(255),
    target_label    VARCHAR(500),
    details         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_audit_tenant ON audit_events(tenant_id);
CREATE INDEX idx_audit_created ON audit_events(created_at DESC);

-- Ticket comment attachments
ALTER TABLE ticket_comments ADD COLUMN attachment_filename VARCHAR(500);
ALTER TABLE ticket_comments ADD COLUMN attachment_filepath VARCHAR(1000);
ALTER TABLE ticket_comments ADD COLUMN attachment_size BIGINT;
