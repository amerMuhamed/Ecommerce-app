package com.spring.eCommerce.dto;

import lombok.Builder;

@Builder
public record JWTResponse(
    String accessToken,
    String refreshToken
) {
}
