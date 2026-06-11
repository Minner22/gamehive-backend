package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.user.dto.AuditLogFilter;
import pl.m22.gamehive.user.model.UserAuditLogEntry;
import pl.m22.gamehive.user.repository.UserAuditLogRepository;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final UserAuditLogRepository userAuditLogRepository;

    @Transactional(readOnly = true)
    public Page<UserAuditLogEntry> search(AuditLogFilter filter, Pageable pageable) {

        return userAuditLogRepository.findAll(UserAuditLogSpecifications.withFilter(filter), pageable);
    }
}
