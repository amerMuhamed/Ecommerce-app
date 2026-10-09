package com.spring.eCommerce.service.payment.paymob.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymobBillingData(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("email") String email,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("street") String street,
        @JsonProperty("building") String building,
        @JsonProperty("floor") String floor,
        @JsonProperty("apartment") String apartment,
        @JsonProperty("city") String city,
        @JsonProperty("country") String country,
        @JsonProperty("state") String state
) {
}
