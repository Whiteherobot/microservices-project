package com.example.order.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.jboss.logging.Logger;

import com.example.order.model.Order;
import com.example.order.resource.dto.CreateOrderRequest;
import com.example.order.resource.dto.CreateOrderResponse;
import com.example.order.service.OrderService;

import java.util.List;
import java.util.stream.Collectors;

@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Orders API", description = "Gestión de órdenes y orquestación de microservicios")
public class OrderResource {
    
    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    @Inject
    OrderService orderService;

    @GET
    @Operation(summary = "Obtener todas las órdenes", description = "Retorna la lista completa de órdenes en orden descendente")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Lista de órdenes obtenida exitosamente"),
        @APIResponse(responseCode = "500", description = "Error interno del servidor")
    })
    public Response getAllOrders() {
        try {
            List<Order> orders = orderService.getAllOrders();
            
            List<CreateOrderResponse> responses = orders.stream()
                .map(order -> {
                    double totalCost = order.totalPrice + order.shippingCost;
                    return new CreateOrderResponse(
                        order.id,
                        order.productId,
                        order.quantity,
                        order.totalPrice,
                        order.shippingCost,
                        totalCost,
                        order.status.name()
                    );
                })
                .collect(Collectors.toList());
            
            return Response.ok(responses).build();
            
        } catch (Exception e) {
            LOG.errorf("Error al obtener órdenes: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Error al obtener órdenes: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Operation(summary = "Crear nueva orden", description = "Orquesta la creación de orden: valida producto, calcula envío, descuenta stock y persiste orden. Con tolerancia a fallos y compensating transactions.")
    @APIResponses(value = {
        @APIResponse(responseCode = "201", description = "Orden creada exitosamente"),
        @APIResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @APIResponse(responseCode = "404", description = "Producto no encontrado"),
        @APIResponse(responseCode = "409", description = "Stock insuficiente"),
        @APIResponse(responseCode = "503", description = "Servicio externo no disponible")
    })
    public Response createOrder(
        @RequestBody(description = "Datos para crear la orden") CreateOrderRequest request,
        @HeaderParam("Idempotency-Key") String idempotencyKey) {
        try {
            // Validar datos de entrada
            if (request.productId == null || request.quantity <= 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Datos de entrada inválidos: productId y quantity requeridos"))
                    .build();
            }
            
            if (request.weight <= 0 || request.distance <= 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Peso y distancia deben ser valores positivos"))
                    .build();
            }
            
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                LOG.infof("Procesando orden con Idempotency-Key: %s", idempotencyKey);
            }
            
            // Ejecutar orquestación con resiliencia
            Order order = orderService.createOrder(request);
            
            // Construir respuesta exitosa
            double totalCost = order.totalPrice + order.shippingCost;
            CreateOrderResponse response = new CreateOrderResponse(
                order.id,
                order.productId,
                order.quantity,
                order.totalPrice,
                order.shippingCost,
                totalCost,
                order.status.name()
            );
            
            LOG.infof("Orden creada exitosamente con ID: %d", order.id);
            return Response.status(Response.Status.CREATED)
                .entity(response)
                .build();
                
        } catch (OrderService.ProductNotFoundException e) {
            LOG.warnf("Producto no encontrado: %s", e.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
                
        } catch (OrderService.InsufficientStockException e) {
            LOG.warnf("Stock insuficiente: %s", e.getMessage());
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
                
        } catch (OrderService.OrderCreationException e) {
            LOG.errorf("Error en creación de orden: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Error al crear orden: " + e.getMessage()))
                .build();
                
        } catch (jakarta.ws.rs.ProcessingException e) {
            LOG.errorf("Error de comunicación con servicio externo: %s", e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(new ErrorResponse("Servicio externo no disponible: " + e.getMessage()))
                .build();
                
        } catch (Exception e) {
            LOG.errorf("Error técnico inesperado: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("Error interno del servidor: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener orden por ID", description = "Retorna los detalles de una orden específica")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Orden obtenida exitosamente"),
        @APIResponse(responseCode = "404", description = "Orden no encontrada")
    })
    public Response getOrder(@PathParam("id") Long id) {
        Order order = orderService.getOrder(id);
        
        if (order == null) {
            LOG.warnf("Orden no encontrada con ID: %d", id);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Orden no encontrada"))
                .build();
        }
        
        double totalCost = order.totalPrice + order.shippingCost;
        CreateOrderResponse response = new CreateOrderResponse(
            order.id,
            order.productId,
            order.quantity,
            order.totalPrice,
            order.shippingCost,
            totalCost,
            order.status.name()
        );
        
        return Response.ok(response).build();
    }
    
    // DTO para respuestas de error
    public static class ErrorResponse {
        public String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
    }
}
