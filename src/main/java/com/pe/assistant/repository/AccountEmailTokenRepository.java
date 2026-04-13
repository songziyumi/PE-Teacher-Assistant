package com.pe.assistant.repository;

import com.pe.assistant.entity.AccountEmailToken;
import com.pe.assistant.entity.AccountEmailTokenPurpose;
import com.pe.assistant.entity.AccountPrincipalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountEmailTokenRepository extends JpaRepository<AccountEmailToken, Long> {

    Optional<AccountEmailToken> findByTokenHash(String tokenHash);

    List<AccountEmailToken> findByPrincipalTypeAndPrincipalIdAndPurposeAndUsedAtIsNull(
            AccountPrincipalType principalType,
            Long principalId,
            AccountEmailTokenPurpose purpose);
}
