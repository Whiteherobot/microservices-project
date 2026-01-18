package com.example.product_service.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import com.example.product_service.model.Product;
import com.example.product_service.service.ProductService;

@RestController
@RequestMapping("/v1/products")
public class ProductController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<Product> getAll() {
        logger.info("Obteniendo lista de productos");
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@RequestBody Product product) {
        logger.info("Creando nuevo producto: {}", product.getName());
        return service.create(product);
    }

    @PostMapping("/{id}/decrease-stock")
    public void decreaseStock(
        @PathVariable Long id,
        @RequestParam int quantity
    ) {
        logger.info("Descontando {} unidades del producto {}", quantity, id);
        service.decreaseStock(id, quantity);
    }
}
