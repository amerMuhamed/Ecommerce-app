package com.spring.eCommerce.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AppUser {
    @Id()
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String username;
    private String password;
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "profile_image_id", referencedColumnName = "id")
  private Image image;

    @ManyToMany
    @JoinTable(name = "app_user_role",
            joinColumns = @JoinColumn(name = "app_user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
     private Set<Role> roles=new HashSet<>();



    @OneToMany(mappedBy = "appUser", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private Set<TokenInfo> deviceTokens = new HashSet<>();
    public AppUser(Long id, String fullName, String username, String password, Set<Role> roles) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    public AppUser(Long id) {
        this.id = id;
    }
}
