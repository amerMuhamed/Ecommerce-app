package com.spring.eCommerce.entity;

import com.spring.eCommerce.entity.enums.PaymentProvider;
import com.spring.eCommerce.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser payer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private BigDecimal amount;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "EGP";

    @Column(name = "provider_payment_id", unique = true)
    private String providerPaymentId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Column(name = "provider_data", columnDefinition = "TEXT")
    private String providerData;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentAttempt> attempts = new ArrayList<>();

    @Version
    private Long version;

    public void addAttempt(PaymentAttempt attempt) {
        attempts.add(attempt);
        attempt.setPayment(this);
    }
}
