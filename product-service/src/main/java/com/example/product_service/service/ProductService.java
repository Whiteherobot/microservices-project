package com.example.product_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.example.product_service.model.Product;
import com.example.product_service.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product create(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product payload cannot be null");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (product.getPrice() == null || product.getPrice() < 0) {
            throw new IllegalArgumentException("Product price must be zero or greater");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("Product stock must be zero or greater");
        }
        return repository.save(product);
    }

    @Transactional
    public void decreaseStock(Long id, int quantity) {
        if (id == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }
        
        Product p = repository.findById(id)
            .orElseThrow();

        if (p.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock");
        }

        p.setStock(p.getStock() - quantity);
    }
}
