-- GH-118: trwały log audytu decyzji moderacyjnych na treści (kto/co/kiedy + powód/korelacja).
-- Bez FK do games — wpis musi przetrwać hard-delete gry (#119). Wzorzec: V4 user_audit_log.
CREATE TABLE IF NOT EXISTS content_moderation_audit_log (
                                              id BIGSERIAL PRIMARY KEY,
                                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                              updated_at TIMESTAMP WITH TIME ZONE,
                                              action VARCHAR(40) NOT NULL,
                                              target_type VARCHAR(20) NOT NULL,
                                              target_id BIGINT NOT NULL,
                                              actor VARCHAR(255) NOT NULL,
                                              details TEXT,
                                              correlation_id VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_cma_target_id ON content_moderation_audit_log (target_id);
CREATE INDEX IF NOT EXISTS idx_cma_actor ON content_moderation_audit_log (actor);
CREATE INDEX IF NOT EXISTS idx_cma_action ON content_moderation_audit_log (action);
