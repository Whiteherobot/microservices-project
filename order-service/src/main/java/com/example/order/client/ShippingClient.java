package com.example.order.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/shipping")
@RegisterRestClient(configKey = "shipping-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ShippingClient {
    
    @POST
    @Path("/calculate")
    ShippingResponse calculateShipping(ShippingRequest request);
    
    class ShippingRequest {
        public double weight;
        public double distance;

        public ShippingRequest() {}

        public ShippingRequest(double weight, double distance) {
            this.weight = weight;
            this.distance = distance;
        }
    }
    
    class ShippingResponse {
        public double cost;

        public ShippingResponse() {}

        public ShippingResponse(double cost) {
            this.cost = cost;
        }
    }
}