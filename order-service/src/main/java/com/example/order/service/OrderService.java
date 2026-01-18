package com.example.order.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

import com.example.order.client.ProductClient;
import com.example.order.client.ShippingClient;
import com.example.order.model.Order;
import com.example.order.resource.dto.CreateOrderRequest;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class OrderService {
    
    private static final Logger LOG = Logger.getLogger(OrderService.class);
    
    @Inject
    EntityManager em;
    
    @Inject
    @RestClient
    ProductClient productClient;
    
    @Inject
    @RestClient
    ShippingClient shippingClient;
    
    /**
     * Orquesta el flujo completo de creación de orden con tolerancia a fallos:
     * 1. Valida el producto y disponibilidad de stock
     * 2. Calcula el costo de envío
     * 3. Descuenta el stock del producto
     * 4. Persiste la orden
     * 
     * Si cualquier paso falla, realiza compensating transaction (rollback de stock)
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        LOG.infof("Iniciando creación de orden: productId=%d, quantity=%d, weight=%f, distance=%f", 
                  request.productId, request.quantity, request.weight, request.distance);
        
        // PASO 1: Validar producto y stock con circuit breaker
        ProductClient.ProductDTO product = validateProductAndStock(request.productId, request.quantity);
        LOG.infof("Producto validado: %s, Stock disponible: %d", product.name, product.stock);
        
        // PASO 2: Calcular costo de envío con circuit breaker
        double shippingCost = calculateShippingCost(request.weight, request.distance);
        LOG.infof("Costo de envío calculado: $%.2f", shippingCost);
        
        // PASO 3: Descontar stock del producto
        try {
            decreaseProductStock(request.productId, request.quantity);
            LOG.infof("Stock descontado exitosamente para productId=%d", request.productId);
        } catch (Exception e) {
            LOG.errorf("Error al descontar stock. Abortando orden. Error: %s", e.getMessage());
            throw new OrderCreationException("Error al descontar stock: " + e.getMessage());
        }
        
        // PASO 4: Crear y persistir la orden
        double productCost = product.price * request.quantity;
        double totalCost = productCost + shippingCost;
        
        Order order = new Order(
            request.productId,
            request.quantity,
            "N/A",
            productCost,
            shippingCost
        );
        order.status = Order.OrderStatus.CONFIRMED;
        
        try {
            em.persist(order);
            em.flush();
            LOG.infof("Orden creada exitosamente: orderId=%d, total=%.2f", order.id, totalCost);
        } catch (Exception e) {
            // COMPENSATING TRANSACTION: Restaurar stock si falla persistencia
            LOG.errorf("Fallo en persistencia. Ejecutando compensating transaction. Error: %s", e.getMessage());
            try {
                restoreProductStock(request.productId, request.quantity);
                LOG.infof("Stock restaurado como parte de compensating transaction para productId=%d", request.productId);
            } catch (Exception rollbackError) {
                LOG.errorf("CRÍTICO: Fallo en compensating transaction. Stock podría estar inconsistente. Error: %s", rollbackError.getMessage());
            }
            throw new OrderCreationException("Fallo al persistir orden: " + e.getMessage());
        }
        
        return order;
    }
    
    /**
     * Valida producto y stock con Circuit Breaker y Retry
     */
    @CircuitBreaker(
        requestVolumeThreshold = 5,
        failureRatio = 0.5,
        delay = 5,
        delayUnit = ChronoUnit.SECONDS
    )
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public ProductClient.ProductDTO validateProductAndStock(Long productId, int quantity) {
        List<ProductClient.ProductDTO> products = productClient.getAllProducts();
        
        ProductClient.ProductDTO product = products.stream()
            .filter(p -> p.id.equals(productId))
            .findFirst()
            .orElseThrow(() -> new ProductNotFoundException("Producto con ID " + productId + " no encontrado"));
        
        if (product.stock < quantity) {
            throw new InsufficientStockException(
                "Stock insuficiente. Disponible: " + product.stock + ", Solicitado: " + quantity
            );
        }
        
        return product;
    }
    
    /**
     * Calcula envío con Circuit Breaker
     */
    @CircuitBreaker(
        requestVolumeThreshold = 5,
        failureRatio = 0.5,
        delay = 5,
        delayUnit = ChronoUnit.SECONDS
    )
    @Timeout(value = 3, unit = ChronoUnit.SECONDS)
    public double calculateShippingCost(double weight, double distance) {
        ShippingClient.ShippingRequest shippingRequest = new ShippingClient.ShippingRequest(weight, distance);
        ShippingClient.ShippingResponse shippingResponse = shippingClient.calculateShipping(shippingRequest);
        return shippingResponse.cost;
    }
    
    /**
     * Descuenta stock con Retry y Timeout
     */
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public void decreaseProductStock(Long productId, int quantity) {
        productClient.decreaseStock(productId, quantity);
    }
    
    /**
     * Restaura stock como parte de compensating transaction
     */
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    public void restoreProductStock(Long productId, int quantity) {
        // Implementar endpoint específico en Product Service para restore
        // Por ahora, usar increase stock simulado
        LOG.warnf("Intentando restaurar %d unidades del productId=%d", quantity, productId);
        productClient.decreaseStock(productId, -quantity); // Negative para restaurar
    }
    
    public Order getOrder(Long id) {
        return em.find(Order.class, id);
    }
    
    public List<Order> getAllOrders() {
        return em.createQuery("SELECT o FROM Order o ORDER BY o.id DESC", Order.class)
                 .getResultList();
    }
    
    // Excepciones de negocio
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }
    
    public static class OrderCreationException extends RuntimeException {
        public OrderCreationException(String message) {
            super(message);
        }
    }
}