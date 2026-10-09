package com.spring.eCommerce.service.payment.paymob.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymobIntentionItem(
        @JsonProperty("name") String name,
        @JsonProperty("amount") long amount,
        @JsonProperty("description") String description,
        @JsonProperty("quantity") int quantity
) {
}
