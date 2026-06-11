package pl.m22.gamehive.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pl.m22.gamehive.user.model.UserAuditLogEntry;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAuditLogRepository extends JpaRepository<UserAuditLogEntry, Long>, JpaSpecificationExecutor<UserAuditLogEntry> {

    List<UserAuditLogEntry> findByTargetId(UUID targetId);

}
