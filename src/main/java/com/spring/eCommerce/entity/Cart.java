package com.spring.eCommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cart")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CartItem> cartItems = new ArrayList<>();

    @OneToOne(mappedBy = "cart")
    @JsonIgnore
    private AppUser user;

    public void addItem(CartItem cartItem) {
        cartItems.add(cartItem);
        cartItem.setCart(this);
    }

    public void removeItem(CartItem cartItem) {
        cartItems.remove(cartItem);
        cartItem.setCart(null);
    }

    public void clearItems() {
        for (CartItem cartItem : new ArrayList<>(cartItems)) {
            removeItem(cartItem);
        }
    }

}
