package com.spring.eCommerce.service.payment.paymob;

import com.fasterxml.jackson.databind.JsonNode;

public record PaymobIntention(
        String intentionId,
        long intentionOrderId,
        String clientSecret,
        String status,
        JsonNode raw
) {
}
