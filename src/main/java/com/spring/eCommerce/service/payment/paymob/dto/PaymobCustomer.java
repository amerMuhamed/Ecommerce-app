package com.spring.eCommerce.service.payment.paymob.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymobCustomer(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("email") String email,
        @JsonProperty("extras") Map<String, Object> extras
) {
}
