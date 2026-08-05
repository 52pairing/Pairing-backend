package com.pairing.account.infrastructure.mapper;

import com.pairing.account.domain.model.Account;
import com.pairing.account.infrastructure.persistence.AccountJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AccountMapper {

    AccountJpaEntity toJpaEntity(Account account);

    // 도메인 생성자가 닫혀 있으므로 정적 팩토리(reconstitute)로 직접 복원한다.
    default Account toDomain(AccountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Account.reconstitute(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getName(),
                entity.getPhone(),
                entity.getSignupType(),
                entity.getStatus(),
                entity.isEmailVerified(),
                entity.getLoginFailCount(),
                entity.getLockedAt(),
                entity.isTempPassword(),
                entity.getPasswordUpdatedAt(),
                entity.getLastLoginAt(),
                entity.getSuspendReason(),
                entity.getWithdrawnAt(),
                entity.getWithdrawReason(),
                entity.getEmailHash(),
                entity.getPhoneHash(),
                entity.getRejoinAvailableAt(),
                entity.getPurgeAt(),
                entity.getDeletedAt()
        );
    }
}
