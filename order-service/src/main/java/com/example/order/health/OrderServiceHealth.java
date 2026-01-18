package com.example.order.health;

import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
@Liveness
public class OrderServiceHealth implements HealthCheck {
    
    @Inject
    EntityManager em;
    
    @Override
    public HealthCheckResponse call() {
        try {
            // Verificar conexión a base de datos
            em.createQuery("SELECT 1", Integer.class).setMaxResults(1).getSingleResult();
            return HealthCheckResponse.up("OrderService - Database connection verified");
        } catch (Exception e) {
            return HealthCheckResponse.down("OrderService - Database connection failed: " + e.getMessage());
        }
    }
}
