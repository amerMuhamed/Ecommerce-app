package com.spring.eCommerce.repository;

import com.spring.eCommerce.entity.TokenInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenInfoRepo extends JpaRepository<TokenInfo, Long> {
    TokenInfo findByRefreshToken(String refreshToken);
    TokenInfo findByAccessToken(String accessToken);
}
