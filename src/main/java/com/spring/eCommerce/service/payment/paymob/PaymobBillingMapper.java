package com.spring.eCommerce.service.payment.paymob;

import com.spring.eCommerce.dto.payment.PaymentInitiateRequest;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.OrderItem;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.service.payment.paymob.dto.PaymobBillingData;
import com.spring.eCommerce.service.payment.paymob.dto.PaymobCustomer;
import com.spring.eCommerce.service.payment.paymob.dto.PaymobIntentionItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PaymobBillingMapper {

    /**
     * Paymob's documented placeholder for billing fields the merchant does not collect.
     */
    private static final String NOT_AVAILABLE = "NA";
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    PaymobBillingData billingData(AppUser payer, Order order, PaymentInitiateRequest request) {
        String[] names = splitName(payer.getFullName());
        return new PaymobBillingData(
                names[0],
                names[1],
                email(payer, request),
                phoneNumber(request),
                street(order),
                NOT_AVAILABLE,
                NOT_AVAILABLE,
                NOT_AVAILABLE,
                NOT_AVAILABLE,
                NOT_AVAILABLE,
                NOT_AVAILABLE
        );
    }

    PaymobCustomer customer(AppUser payer, Order order, PaymentInitiateRequest request) {
        String[] names = splitName(payer.getFullName());
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("ecom_user_id", payer.getId());
        extras.put("ecom_order_id", order.getId());
        return new PaymobCustomer(names[0], names[1], email(payer, request), extras);
    }

    List<PaymobIntentionItem> items(Order order, long totalMinorUnits, String currency) {
        List<PaymobIntentionItem> lines = new ArrayList<>();
        long sum = 0L;
        for (OrderItem orderItem : order.getOrderItems()) {
            if (orderItem.getProduct() == null || orderItem.getQuantity() == null || orderItem.getPrice() == null
                    || orderItem.getQuantity() <= 0) {
                continue;
            }
            long unitMinor = PaymobMoney.toMinorUnits(orderItem.getPrice(), currency);
            lines.add(new PaymobIntentionItem(
                    truncate(orderItem.getProduct().getName(), 100),
                    unitMinor,
                    "Order #" + order.getId(),
                    orderItem.getQuantity()
            ));
            sum = Math.addExact(sum, Math.multiplyExact(unitMinor, (long) orderItem.getQuantity()));
        }
        // Paymob requires item amounts to add up to the intention amount; otherwise send one summary line.
        if (!lines.isEmpty() && sum == totalMinorUnits) {
            return lines;
        }
        return List.of(new PaymobIntentionItem(
                "Order #" + order.getId(),
                totalMinorUnits,
                "Payment for order #" + order.getId(),
                1
        ));
    }

    private String phoneNumber(PaymentInitiateRequest request) {
        String phone = request == null ? null : request.billingPhoneNumber();
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("billingPhoneNumber is required for Paymob payments.");
        }
        String normalized = phone.replaceAll("[\\s-]", "");
        if (!PHONE.matcher(normalized).matches()) {
            throw new BusinessException("billingPhoneNumber must contain 8 to 15 digits, optionally prefixed with '+'.");
        }
        return normalized;
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{NOT_AVAILABLE, NOT_AVAILABLE};
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return new String[]{truncate(parts[0], 50), NOT_AVAILABLE};
        }
        String last = parts[parts.length - 1];
        String first = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
        return new String[]{truncate(first, 50), truncate(last, 50)};
    }

    private String email(AppUser payer, PaymentInitiateRequest request) {
        String requested = request == null ? null : request.billingEmail();
        if (requested != null && !requested.isBlank()) {
            if (!EMAIL.matcher(requested.trim()).matches()) {
                throw new BusinessException("billingEmail is not a valid email address.");
            }
            return requested.trim();
        }
        String username = payer.getUsername();
        if (username != null && EMAIL.matcher(username).matches()) {
            return username;
        }
        throw new BusinessException("billingEmail is required for Paymob payments when the account username is not an email.");
    }

    private String street(Order order) {
        if (order.getShippingAddress() == null || order.getShippingAddress().isBlank()) {
            return NOT_AVAILABLE;
        }
        return truncate(order.getShippingAddress().trim(), 200);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
