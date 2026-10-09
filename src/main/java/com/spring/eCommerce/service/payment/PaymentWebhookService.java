package com.spring.eCommerce.service.payment;

import com.spring.eCommerce.Mapper.PaymentMapper;
import com.spring.eCommerce.dto.payment.PaymentResponseDto;
import com.spring.eCommerce.dto.payment.PaymentReturnResponseDto;
import com.spring.eCommerce.entity.Order;
import com.spring.eCommerce.entity.Payment;
import com.spring.eCommerce.entity.PaymentWebhookEvent;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;
import com.spring.eCommerce.exception.BusinessException;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.PaymentAttemptRepo;
import com.spring.eCommerce.repository.PaymentRepo;
import com.spring.eCommerce.repository.PaymentWebhookEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Log4j2
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final Set<PaymentStatus> OPEN_STATES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION, PaymentStatus.PROCESSING,
            PaymentStatus.FAILED, PaymentStatus.EXPIRED);
    private static final Set<PaymentStatus> REVERSAL_STATES = EnumSet.of(
            PaymentStatus.REFUNDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.CANCELLED);

    private final PaymentRepo paymentRepo;
    private final PaymentAttemptRepo paymentAttemptRepo;
    private final PaymentWebhookEventRepo webhookEventRepo;
    private final OrderRepo orderRepo;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayRegistry gatewayRegistry;

    /**
     * SUCCEEDED, REFUNDED and CANCELLED are settled: a late failure/pending/expiry can never overwrite them.
     * A succeeded payment can only move to a refund or void. Open states may move anywhere (a failed card
     * attempt can be followed by a successful one on the same checkout, and an out-of-order refund can
     * arrive before the success it reverses).
     */
    static boolean isAllowedTransition(PaymentStatus current, PaymentStatus next) {
        if (next == null || current == next) {
            return false;
        }
        if (OPEN_STATES.contains(current)) {
            return true;
        }
        return switch (current) {
            case SUCCEEDED -> REVERSAL_STATES.contains(next);
            case PARTIALLY_REFUNDED -> next == PaymentStatus.REFUNDED;
            default -> false;
        };
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * Applies a webhook whose signature has already been verified by the provider's gateway.
     * Every received event is recorded once per (provider, eventId); ignored and quarantined (FAILED) events are
     * recorded with a note and never change payment or order state. Processing errors roll the whole
     * transaction back so the provider can redeliver.
     */
    @Transactional
    public WebhookOutcome handleWebhook(PaymentProvider provider, PaymentGateway.WebhookEvent event,
                                        Map<String, Object> rawBody, String rawPayload) {
        Optional<Long> paymentId = findPaymentId(provider, event.providerReference());
        // Lock before the duplicate check so concurrent redeliveries of one event are serialized.
        Payment payment = paymentId.flatMap(paymentRepo::findByIdForUpdate).orElse(null);

        Optional<PaymentWebhookEvent> existing = webhookEventRepo.findByProviderAndEventId(provider, event.eventId());
        if (existing.isPresent()) {
            Payment existingPayment = existing.get().getPayment();
            return new WebhookOutcome(existing.get().getStatus(), "Duplicate webhook event; already handled.",
                    existingPayment == null ? null : paymentMapper.toDto(existingPayment));
        }

        PaymentWebhookEvent webhookEvent = PaymentWebhookEvent.builder()
                .provider(provider)
                .eventId(event.eventId())
                .payment(payment)
                .status(WebhookEventStatus.RECEIVED)
                .payload(rawPayload)
                .build();

        if (payment == null) {
            log.warn("Quarantined {} webhook {}: no payment for provider reference {}",
                    provider, event.eventId(), event.providerReference());
            return record(webhookEvent, WebhookEventStatus.FAILED, "Unknown payment reference.", null);
        }

        PaymentGateway.WebhookResolution resolution =
                gatewayRegistry.get(provider).resolveWebhook(payment, event, rawBody);
        if (resolution.outcome() != WebhookEventStatus.PROCESSED) {
            if (resolution.outcome() == WebhookEventStatus.FAILED) {
                log.warn("Quarantined {} webhook {} for payment {}: {}",
                        provider, event.eventId(), payment.getId(), resolution.reason());
            }
            return record(webhookEvent, resolution.outcome(), resolution.reason(), payment);
        }
        return applyResolution(webhookEvent, payment, resolution);
    }

    @Transactional(readOnly = true)
    public PaymentReturnResponseDto getReturnStatus(PaymentProvider provider, String providerReference) {
        Payment payment = findPaymentId(provider, providerReference)
                .flatMap(paymentRepo::findById)
                .orElseThrow(() -> new BusinessException("Payment not found."));
        return new PaymentReturnResponseDto(payment.getOrder().getId(), payment.getStatus());
    }

    private WebhookOutcome applyResolution(PaymentWebhookEvent webhookEvent, Payment payment,
                                           PaymentGateway.WebhookResolution resolution) {
        PaymentStatus current = payment.getStatus();
        PaymentStatus next = resolution.status();

        if (current == PaymentStatus.SUCCEEDED && next == PaymentStatus.SUCCEEDED) {
            // A different successful transaction (duplicates were filtered by event id): the customer may
            // have been charged twice for one order.
            log.error("Payment {} received a second successful {} transaction ({}); manual refund review required",
                    payment.getId(), payment.getProvider(), webhookEvent.getEventId());
            return record(webhookEvent, WebhookEventStatus.FAILED,
                    "Additional successful transaction for an already paid payment; review for refund.", payment);
        }
        if (current == next) {
            return record(webhookEvent, WebhookEventStatus.IGNORED, "Payment already in state " + current + ".", payment);
        }
        if (!isAllowedTransition(current, next)) {
            return record(webhookEvent, WebhookEventStatus.IGNORED,
                    "Transition " + current + " -> " + next + " is not allowed.", payment);
        }

        payment.setStatus(next);
        if (resolution.rawResponse() != null) {
            payment.setProviderData(resolution.rawResponse());
        }
        String note = updateOrder(payment, next);
        paymentRepo.save(payment);
        return record(webhookEvent, WebhookEventStatus.PROCESSED, note, payment);
    }

    private String updateOrder(Payment payment, PaymentStatus newStatus) {
        Order order = payment.getOrder();
        if (newStatus == PaymentStatus.SUCCEEDED) {
            if (order.getOrderStatus() == OrderStatus.PENDING) {
                order.setOrderStatus(OrderStatus.CONFIRMED);
                orderRepo.save(order);
                return null;
            }
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                log.warn("Payment {} succeeded for cancelled order {}; refund review required", payment.getId(), order.getId());
                return "Payment succeeded for a cancelled order; review for refund.";
            }
            return null;
        }
        if (REVERSAL_STATES.contains(newStatus)) {
            log.warn("Payment {} for order {} moved to {}; order status {} left for manual handling",
                    payment.getId(), order.getId(), newStatus, order.getOrderStatus());
            return "Payment " + newStatus + "; order status left unchanged.";
        }
        return null;
    }

    private WebhookOutcome record(PaymentWebhookEvent webhookEvent, WebhookEventStatus status, String note, Payment payment) {
        webhookEvent.setStatus(status);
        webhookEvent.setProcessingNote(note == null ? null : truncate(note, 500));
        // Unique (provider, event_id) is the final guard: a concurrent duplicate fails here and rolls back.
        webhookEventRepo.saveAndFlush(webhookEvent);
        String message = switch (status) {
            case PROCESSED -> "Webhook processed.";
            case IGNORED -> "Webhook acknowledged without changes: " + note;
            default -> "Webhook rejected: " + note;
        };
        return new WebhookOutcome(status, message, payment == null ? null : paymentMapper.toDto(payment));
    }

    /**
     * Current reference first; then references from earlier attempts, so a callback for a checkout that was
     * replaced by a newer attempt still reaches its payment.
     */
    private Optional<Long> findPaymentId(PaymentProvider provider, String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return Optional.empty();
        }
        Optional<Payment> current = paymentRepo.findByProviderAndProviderPaymentId(provider, providerReference);
        if (current.isPresent()) {
            return Optional.of(current.get().getId());
        }
        List<Long> ids = paymentAttemptRepo.findPaymentIdsByProviderReference(provider, providerReference);
        if (ids.size() > 1) {
            log.error("{} reference {} is linked to multiple payments {}", provider, providerReference, ids);
            return Optional.empty();
        }
        return ids.stream().findFirst();
    }

    public record WebhookOutcome(WebhookEventStatus status, String message, PaymentResponseDto payment) {
    }
}
