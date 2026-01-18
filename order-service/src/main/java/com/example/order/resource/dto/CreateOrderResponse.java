package com.example.order.resource.dto;

public class CreateOrderResponse {

    public Long id;
    public Long productId;
    public int quantity;
    public double subtotal;
    public double shippingCost;
    public double total;
    public String status;

    public CreateOrderResponse(Long id, Long productId, int quantity, double subtotal, double shippingCost, double total, String status) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.subtotal = subtotal;
        this.shippingCost = shippingCost;
        this.total = total;
        this.status = status;
    }
}
