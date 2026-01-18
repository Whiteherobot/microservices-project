package com.example.order.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    
    @Column(nullable = false)
    public Long productId;
    
    @Column(nullable = false)
    public Integer quantity;
    
    @Column(nullable = false)
    public String destination;
    
    @Column(nullable = false)
    public Double totalPrice;
    
    @Column(nullable = false)
    public Double shippingCost;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrderStatus status = OrderStatus.PENDING;
    
    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
    
    public Order() {}
    
    public Order(Long productId, Integer quantity, String destination, Double totalPrice, Double shippingCost) {
        this.productId = productId;
        this.quantity = quantity;
        this.destination = destination;
        this.totalPrice = totalPrice;
        this.shippingCost = shippingCost;
    }
    
    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
}