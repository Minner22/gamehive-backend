package pl.m22.gamehive.user.mapper;

import org.mapstruct.Mapper;
import pl.m22.gamehive.user.dto.AuditLogResponseDto;
import pl.m22.gamehive.user.model.UserAuditLogEntry;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponseDto toAuditLogResponseDto(UserAuditLogEntry entry);
}
