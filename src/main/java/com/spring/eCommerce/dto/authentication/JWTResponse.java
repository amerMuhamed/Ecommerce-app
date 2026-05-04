package com.spring.eCommerce.dto.authentication;

import lombok.Builder;

@Builder
public record JWTResponse(
    String accessToken,
    String refreshToken
) {
}
