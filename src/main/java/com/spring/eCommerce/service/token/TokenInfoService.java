package com.spring.eCommerce.service.token;

import com.spring.eCommerce.entity.TokenInfo;
import com.spring.eCommerce.repository.TokenInfoRepo;
import com.spring.eCommerce.security.JwtTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
public class TokenInfoService {
    @Autowired
    private final TokenInfoRepo tokenInfoRepo;

    private final JwtTokenUtils jwtTokenUtils;

    public TokenInfo findById(Long id ){
        return tokenInfoRepo.findById(id).orElse(null);
    }
    public TokenInfo findByRefreshToken(String refreshToken) {
        return tokenInfoRepo.findByRefreshToken(refreshToken);
    }

    public List<TokenInfo> findAll() {
        return tokenInfoRepo.findAll();
    }

    public TokenInfo save(TokenInfo tokenInfo) {
      return tokenInfoRepo.save(tokenInfo);
    }
    public void delete(TokenInfo tokenInfo) {
        tokenInfoRepo.delete(tokenInfo);
    }
    public void deleteById(Long id) {
        tokenInfoRepo.deleteById(id);
    }
    public TokenInfo findByAccessToken(String accessToken) {
        return tokenInfoRepo.findByAccessToken(accessToken);
    }

    @Transactional
    public void deleteExpiredTokens() {
        List<TokenInfo> tokens = tokenInfoRepo.findAll();
        int deletedCount = 0;

        for (TokenInfo tokenInfo : tokens) {
            try {
                boolean accessExpired = jwtTokenUtils.isTokenExpired(tokenInfo.getAccessToken());
                boolean refreshExpired = jwtTokenUtils.isTokenExpired(tokenInfo.getRefreshToken());

                if (accessExpired && refreshExpired) {
                    tokenInfoRepo.delete(tokenInfo);
                    deletedCount++;
                }
            } catch (Exception e) {
                log.warn("Error checking token expiration for token ID {}: {}", tokenInfo.getId(), e.getMessage());
            }
        }

        if (deletedCount > 0) {
            log.info("Deleted {} expired tokens from database", deletedCount);
        }
    }

}
