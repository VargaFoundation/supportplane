-- V14: AI refactoring — model artifacts table for persistent ML models

CREATE TABLE model_artifacts (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT NOT NULL REFERENCES clusters(id),
    model_type      VARCHAR(50) NOT NULL,
    model_data      JSONB,
    training_metrics JSONB,
    training_data_size INTEGER DEFAULT 0,
    trained_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_model_artifacts_cluster ON model_artifacts(cluster_id);
CREATE INDEX idx_model_artifacts_type ON model_artifacts(cluster_id, model_type);
