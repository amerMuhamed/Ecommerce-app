package com.spring.eCommerce.service.payment.paymob.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymobIntentionRequest(
        @JsonProperty("amount") long amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("payment_methods") List<Integer> paymentMethods,
        @JsonProperty("items") List<PaymobIntentionItem> items,
        @JsonProperty("billing_data") PaymobBillingData billingData,
        @JsonProperty("customer") PaymobCustomer customer,
        @JsonProperty("extras") Map<String, Object> extras,
        @JsonProperty("special_reference") String specialReference,
        @JsonProperty("expiration") Integer expiration,
        @JsonProperty("notification_url") String notificationUrl,
        @JsonProperty("redirection_url") String redirectionUrl
) {
}
