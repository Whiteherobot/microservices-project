from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="Shipping Calculator Service")

class ShippingRequest(BaseModel):
    weight: float
    distance: float

class ShippingResponse(BaseModel):
    cost: float

@app.post("/shipping/calculate", response_model=ShippingResponse)
def calculate_shipping(request: ShippingRequest):
    """
    Calcula el costo de envío basado en peso y distancia.
    Fórmula simple para fines académicos.
    """
    base_cost = 5.0
    cost = base_cost + (request.weight * 0.5) + (request.distance * 0.2)
    return ShippingResponse(cost=cost)
