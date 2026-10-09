package com.spring.eCommerce.entity;

import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.WebhookEventStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "event_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookEvent extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookEventStatus status;

    @Column(columnDefinition = "TEXT")
    private String payload;

    /**
     * Why the event was ignored or quarantined (FAILED); null when processed normally.
     */
    @Column(name = "processing_note", length = 500)
    private String processingNote;
}
