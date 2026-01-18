package com.example.order.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/v1/products")
@RegisterRestClient(configKey = "product-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProductClient {
    
    @GET
    List<ProductDTO> getAllProducts();
    
    @POST
    @Path("/{id}/decrease-stock")
    void decreaseStock(@PathParam("id") Long id, @QueryParam("quantity") int quantity);
    
    class ProductDTO {
        public Long id;
        public String name;
        public Double price;
        public Integer stock;

        public ProductDTO() {}

        public ProductDTO(Long id, String name, Double price, Integer stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }
}