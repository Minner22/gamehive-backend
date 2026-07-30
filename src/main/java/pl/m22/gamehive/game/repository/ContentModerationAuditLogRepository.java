package pl.m22.gamehive.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.game.model.ContentModerationAuditLog;

import java.util.List;

@Repository
public interface ContentModerationAuditLogRepository extends JpaRepository<ContentModerationAuditLog, Long> {

    List<ContentModerationAuditLog> findByTargetId(Long targetId);
}
