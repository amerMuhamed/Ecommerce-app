package com.spring.eCommerce.service.token;

import com.spring.eCommerce.entity.TokenInfo;
import com.spring.eCommerce.repository.TokenInfoRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenInfoService {
    @Autowired
    private final TokenInfoRepo tokenInfoRepo;

    public TokenInfo findById(Long id ){
        return tokenInfoRepo.findById(id).orElse(null);
    }
    public TokenInfo findByRefreshToken(String refreshToken) {
        return tokenInfoRepo.findByRefreshToken(refreshToken);
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

}
