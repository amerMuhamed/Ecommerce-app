package com.spring.eCommerce.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="sec_token_info")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    @Column(length = 800)
    private String accessToken;
    @NotBlank
    @Column(length = 800)
    private String refreshToken;
    private String userAgentText;
    private String localIpAddress;
    private String remoteIpAddress;

    public TokenInfo(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    @JsonBackReference
    private AppUser appUser;


}
