-- GH-64: trwały log audytu zmian na kontach użytkowników (kto/komu/co/kiedy).
-- Bez FK do application_users — wpis musi przetrwać DELETE użytkownika.
CREATE TABLE IF NOT EXISTS user_audit_log (
                                              id BIGSERIAL PRIMARY KEY,
                                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                              updated_at TIMESTAMP WITH TIME ZONE,
                                              action VARCHAR(40) NOT NULL,
                                              target_id uuid NOT NULL,
                                              target_email VARCHAR(255) NOT NULL,
                                              actor VARCHAR(255) NOT NULL,
                                              details TEXT,
                                              correlation_id VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_user_audit_log_target_id ON user_audit_log (target_id);
CREATE INDEX IF NOT EXISTS idx_user_audit_log_actor ON user_audit_log (actor);