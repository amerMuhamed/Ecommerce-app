package com.spring.eCommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "payment.paymob")
public class PaymobProperties {

    /**
     * Regional API host for the Intention API, e.g. https://accept.paymob.com (Egypt), https://ksa.paymob.com,
     * https://uae.paymob.com or https://oman.paymob.com.
     */
    private String baseUrl = "https://accept.paymob.com";
    /**
     * Regional Unified Checkout host, e.g. https://eg.checkout.paymob.com/ (Egypt), https://ksa.checkout.paymob.com/,
     * https://uae.checkout.paymob.com/ or https://om.checkout.paymob.com/.
     */
    private String checkoutUrl = "https://eg.checkout.paymob.com/";
    private String secretKey;
    private String publicKey;
    private String hmacSecret;
    private List<Integer> integrationIds = new ArrayList<>();
    private String notificationUrl;
    private String redirectionUrl;
    private int expirationSeconds = 3600;

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isAbsoluteHttpUrl(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.getHost() != null
                    && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    /**
     * Names (never values) of the settings required to create intentions and verify callbacks that are missing.
     */
    public List<String> missingRequiredSettings() {
        List<String> missing = new ArrayList<>();
        if (isBlank(baseUrl)) missing.add("payment.paymob.base-url");
        if (isBlank(checkoutUrl)) missing.add("payment.paymob.checkout-url");
        if (isBlank(secretKey)) missing.add("payment.paymob.secret-key");
        if (isBlank(publicKey)) missing.add("payment.paymob.public-key");
        if (isBlank(hmacSecret)) missing.add("payment.paymob.hmac-secret");
        if (integrationIds == null || integrationIds.isEmpty()) missing.add("payment.paymob.integration-ids");
        if (!isAbsoluteHttpUrl(notificationUrl)) missing.add("payment.paymob.notification-url (absolute http(s) URL)");
        if (!isBlank(redirectionUrl) && !isAbsoluteHttpUrl(redirectionUrl)) {
            missing.add("payment.paymob.redirection-url (absolute http(s) URL)");
        }
        return missing;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public List<Integer> getIntegrationIds() {
        return integrationIds;
    }

    public void setIntegrationIds(List<Integer> integrationIds) {
        this.integrationIds = integrationIds;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public String getRedirectionUrl() {
        return redirectionUrl;
    }

    public void setRedirectionUrl(String redirectionUrl) {
        this.redirectionUrl = redirectionUrl;
    }

    public int getExpirationSeconds() {
        return expirationSeconds;
    }

    public void setExpirationSeconds(int expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }
}
