-- V13: AI Engine — metric history for time series analysis, AI analysis results

CREATE TABLE metric_history (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT NOT NULL REFERENCES clusters(id),
    snapshot_data   JSONB NOT NULL,
    collected_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ai_analysis_results (
    id              BIGSERIAL PRIMARY KEY,
    cluster_id      BIGINT NOT NULL REFERENCES clusters(id),
    analysis_type   VARCHAR(50) NOT NULL,
    results         JSONB NOT NULL,
    triggered_by    BIGINT REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metric_history_cluster ON metric_history(cluster_id);
CREATE INDEX idx_metric_history_collected ON metric_history(collected_at);
CREATE INDEX idx_ai_results_cluster ON ai_analysis_results(cluster_id);
CREATE INDEX idx_ai_results_type ON ai_analysis_results(analysis_type);
