# Guía Completa de Pruebas - Microservices Project

## Índice
1. [Preparación del Entorno](#preparación)
2. [Iniciar la Infraestructura](#inicio)
3. [Verificar Servicios](#verificación)
4. [Pruebas del Frontend](#frontend)
5. [Escenarios de Prueba](#escenarios)
6. [Resolución de Problemas](#troubleshooting)

---

## Preparación del Entorno

### Requisitos Previos
- Docker Desktop instalado y ejecutándose
- Puerto 8080 disponible (único puerto expuesto)
- Navegador moderno (Chrome, Firefox, Edge)

### Verificar Docker
```powershell
# Verificar que Docker esté corriendo
docker --version
docker ps

# Resultado esperado: versión de Docker y lista de contenedores
```

---

## Iniciar la Infraestructura

### Paso 1: Navegar al Directorio del Proyecto
```powershell
cd C:\Users\mlata\Desktop\microservices-project
```

### Paso 2: Detener Contenedores Previos (si existen)
```powershell
docker-compose down -v
```
- Detiene todos los servicios
- Elimina volúmenes para empezar limpio

### Paso 3: Construir e Iniciar Servicios
```powershell
docker-compose up --build -d
```
- `--build`: Reconstruye imágenes si hay cambios
- `-d`: Ejecuta en segundo plano (detached mode)

### Paso 4: Esperar Inicialización (30-60 segundos)
Los servicios tardan en estar completamente listos. Espera a que:
- Product Service inicialice PostgreSQL
- Order Service configure Quarkus
- API Gateway configure Nginx

---

## Verificar Servicios

### Opción 1: Ver Logs en Tiempo Real
```powershell
# Ver logs de TODOS los servicios
docker-compose logs -f

# Ver logs de un servicio específico
docker-compose logs -f api-gateway
docker-compose logs -f order-service
docker-compose logs -f product-service
docker-compose logs -f shipping-service
```

Logs esperados:
- **api-gateway**: `"start worker process"`
- **order-service**: `"Listening on: http://0.0.0.0:8083"`
- **product-service**: `"Started ProductServiceApplication"`
- **shipping-service**: `"Uvicorn running on http://0.0.0.0:8082"`

### Opción 2: Verificar Estado de Contenedores
```powershell
docker-compose ps
```

Estado esperado:
```
NAME                  STATUS
api-gateway           Up
order-service         Up
product-service       Up
shipping-service      Up
product-db            Up (healthy)
```

### Opción 3: Probar Endpoints con cURL

#### Verificar API Gateway
```powershell
curl http://localhost:8080
```
Respuesta esperada: Página HTML de Nginx o error 404 (normal)

#### Listar Productos
```powershell
curl http://localhost:8080/products
```
Respuesta esperada:
```json
[
  {
    "id": 1,
    "name": "Laptop",
    "price": 999.99,
    "stock": 10
  },
  {
    "id": 2,
    "name": "Mouse",
    "price": 25.50,
    "stock": 50
  }
]
```

#### Crear Orden de Prueba
```powershell
curl -X POST http://localhost:8080/orders `
  -H "Content-Type: application/json" `
  -d '{\"productId\":1,\"quantity\":2,\"shippingWeight\":5.0,\"shippingDistance\":100.0}'
```

Respuesta esperada (HTTP 201):
```json
{
  "id": 1,
  "productId": 1,
  "quantity": 2,
  "subtotal": 1999.98,
  "shippingCost": 50.00,
  "total": 2049.98,
  "createdAt": "2024-01-15T10:30:00"
}
```

---

## Pruebas del Frontend

### Paso 1: Abrir el Frontend
```powershell
# Abrir directamente en el navegador predeterminado
start C:\Users\mlata\Desktop\microservices-project\frontend\index.html
```

O manualmente:
1. Abre tu navegador
2. Presiona `Ctrl + O` (Abrir archivo)
3. Navega a `C:\Users\mlata\Desktop\microservices-project\frontend\index.html`
4. Haz clic en "Abrir"

### Paso 2: Abrir Consola del Desarrollador
- **Chrome/Edge**: Presiona `F12` o `Ctrl + Shift + I`
- **Firefox**: Presiona `F12`

Logs esperados en consola:
```
Aplicación iniciada - Conectando con API Gateway
Configuración de la aplicación:
   API Gateway: http://localhost:8080
   Endpoints:
   - GET  /products → Product Service
   - POST /orders   → Order Service (con orquestación)
Solicitando productos al API Gateway...
Productos recibidos: 2
```

### Paso 3: Inspeccionar la Interfaz

**SECCIÓN 1: Productos Disponibles**
- Se muestran tarjetas de productos con:
  - Nombre del producto
  - Precio ($999.99)
  - Stock disponible
  - ID del producto
- Al hacer clic en un producto, se resalta (borde azul)
- El primer producto está seleccionado por defecto

**SECCIÓN 2: Crear Nueva Orden**
- Formulario con 4 campos:
  - Producto (dropdown)
  - Cantidad
  - Peso del Envío (kg)
  - Distancia de Envío (km)
- Botón "Crear Orden" habilitado

**SECCIÓN 3: Resultado de la Orden**
- Oculta hasta que se cree una orden

**SECCIÓN 4: Información de Arquitectura**
- Diagrama de flujo mostrando:
  - Frontend → API Gateway → Microservicios

---

## Escenarios de Prueba

### Escenario 1: Crear Orden Exitosa

**Pasos:**
1. Selecciona el producto **"Laptop"** (haciendo clic en la tarjeta)
2. Ingresa los siguientes datos:
   - Cantidad: `2`
   - Peso: `5.0` kg
   - Distancia: `100` km
3. Haz clic en **"Crear Orden"**

**Resultado Esperado:**
- Se muestra un spinner de carga
- Mensaje: "Orquestando orden..."
- Aparece alerta verde: "Orden creada exitosamente"
- Se muestra resultado con:
  - **ID de Orden**: #1
  - **Producto**: Laptop
  - **Cantidad**: 2
  - **Subtotal**: $1999.98
  - **Costo de Envío**: $50.00
  - **Total**: $2049.98
- El stock de "Laptop" se reduce de 10 a 8
- Detalles JSON expandibles disponibles

**Consola del Navegador:**
```
Creando orden: {productId: 1, quantity: 2, shippingWeight: 5, shippingDistance: 100}
Enviando orden al API Gateway...
Orden creada exitosamente: {id: 1, productId: 1, ...}
```

---

### Escenario 2: Producto sin Stock

**Pasos:**
1. Crea 5 órdenes del producto "Mouse" con cantidad `10` cada una
2. Intenta crear una 6ª orden con cantidad `5`

**Resultado Esperado:**
- Alerta roja: "Error al crear la orden:"
- Mensaje: "Stock insuficiente para el producto solicitado"
- HTTP 409 Conflict
- No se muestra resultado de orden

**Consola del Navegador:**
```
Error creando orden: Stock insuficiente para el producto solicitado
```

---

### Escenario 3: Producto Inexistente

**Pasos:**
1. Abre la consola del navegador (`F12`)
2. Ejecuta manualmente:
```javascript
fetch('http://localhost:8080/orders', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({productId: 999, quantity: 1, shippingWeight: 1, shippingDistance: 10})
}).then(r => r.json()).then(console.log)
```

**Resultado Esperado:**
- HTTP 404 Not Found
- Error: "Producto no encontrado con ID: 999"

---

### Escenario 4: Validación de Formulario

**Pasos:**
1. Deja el campo "Cantidad" vacío o con `0`
2. Haz clic en "Crear Orden"

**Resultado Esperado:**
- Alerta amarilla/roja: "Errores de validación:"
- Lista de errores:
  - "La cantidad debe ser al menos 1"
- No se envía la petición al servidor

---

### Escenario 5: Múltiples Órdenes Consecutivas

**Pasos:**
1. Crea una orden de "Laptop" (cantidad: 1)
2. Haz clic en **"Crear Nueva Orden"**
3. Crea otra orden de "Mouse" (cantidad: 5)
4. Repite 2-3 veces más

**Resultado Esperado:**
- Cada orden se procesa independientemente
- El stock se actualiza después de cada orden
- Cada resultado muestra el ID incremental (1, 2, 3, 4...)
- El botón "Crear Nueva Orden" resetea el formulario

---

### Escenario 6: Verificar Orquestación (Backend)

**Pasos:**
1. Abre PowerShell
2. Ejecuta:
```powershell
docker-compose logs -f order-service
```
3. Crea una orden desde el frontend

Logs Esperados:
```
INFO  Recibida solicitud de orden: {productId=1, quantity=2, ...}
INFO  Consultando producto ID 1 desde Product Service
INFO  Producto encontrado: Laptop, precio: 999.99, stock: 10
INFO  Calculando costo de envío desde Shipping Service
INFO  Costo de envío calculado: 50.00
INFO  Decrementando stock del producto ID 1
INFO  Stock decrementado exitosamente
INFO  Orden persistida con ID: 1
INFO  Orden creada exitosamente: {id=1, total=2049.98}
```

Esto demuestra:
- Order Service actúa como orquestador
- Llama a Product Service (validación + stock)
- Llama a Shipping Service (cálculo)
- Persiste la orden en H2

---

## Resolución de Problemas

### Problema 1: "Error al cargar productos"

**Síntomas:**
- Página muestra alerta roja
- Consola: `Error cargando productos: Failed to fetch`

**Soluciones:**
```powershell
# 1. Verificar que Docker esté corriendo
docker ps

# 2. Verificar que API Gateway esté UP
docker-compose ps api-gateway

# 3. Ver logs del API Gateway
docker-compose logs api-gateway

# 4. Reiniciar servicios
docker-compose restart api-gateway product-service
```

---

### Problema 2: "Orden no se crea (timeout)"

**Síntomas:**
- Spinner de carga infinito
- No aparece resultado ni error

**Soluciones:**
```powershell
# 1. Verificar logs del Order Service
docker-compose logs -f order-service

# 2. Verificar que todos los servicios estén UP
docker-compose ps

# 3. Verificar conectividad interna
docker exec -it order-service curl http://product-service:8081/products
```

---

### Problema 3: "Puerto 8080 ya en uso"

**Síntomas:**
- Error al iniciar: `Bind for 0.0.0.0:8080 failed: port is already allocated`

**Soluciones:**
```powershell
# 1. Ver qué proceso usa el puerto 8080
netstat -ano | findstr :8080

# 2. Detener el proceso (reemplaza <PID> con el número)
taskkill /PID <PID> /F

# 3. O cambiar el puerto en docker-compose.yml
# Edita la línea:
#   ports:
#     - "8081:80"  # Cambiar a otro puerto
```

---

### Problema 4: "Base de datos no inicializa"

**Síntomas:**
- Product Service falla al iniciar
- Logs: `Connection refused` o `Unknown database`

**Soluciones:**
```powershell
# 1. Eliminar volúmenes y recrear
docker-compose down -v
docker-compose up --build -d

# 2. Verificar salud de PostgreSQL
docker-compose exec product-db pg_isready

# 3. Ver logs de la base de datos
docker-compose logs product-db
```

---

### Problema 5: "CORS Error" en Navegador

**Síntomas:**
- Consola: `Access to fetch at 'http://localhost:8080/...' from origin 'file://' has been blocked by CORS`

**Solución:**
Esto es normal si abres index.html directamente desde el sistema de archivos.

**Opción A: Usar un servidor HTTP local**
```powershell
# Con Python (si está instalado)
cd C:\Users\mlata\Desktop\microservices-project\frontend
python -m http.server 3000

# Luego abre: http://localhost:3000
```

**Opción B: Modificar Nginx para permitir file://**
```nginx
# En api-gateway/nginx.conf, agregar:
add_header 'Access-Control-Allow-Origin' '*';
```

---

## Validación Final - Checklist

### Infraestructura
- [ ] Todos los contenedores están `Up`
- [ ] Puerto 8080 es el único expuesto
- [ ] Logs no muestran errores críticos
- [ ] PostgreSQL está `healthy`

### Backend
- [ ] GET /products devuelve lista de productos
- [ ] POST /orders crea órdenes exitosamente
- [ ] Errores de negocio (404, 409) funcionan
- [ ] Stock se decrementa correctamente
- [ ] Orquestación se refleja en logs

### Frontend
- [ ] Productos cargan automáticamente
- [ ] Formulario valida campos
- [ ] Órdenes se crean y muestran resultado
- [ ] Stock se actualiza en tiempo real
- [ ] Diseño responsive funciona
- [ ] Consola no muestra errores

### Arquitectura
- [ ] Frontend solo habla con API Gateway (puerto 8080)
- [ ] Microservicios NO están expuestos directamente
- [ ] Order Service orquesta Product + Shipping
- [ ] Cada servicio tiene su base de datos
- [ ] No hay comunicación directa entre servicios

---

## Entregables para Evaluación

### 1. Capturas de Pantalla
- Navegador mostrando productos cargados
- Formulario con datos completos
- Resultado de orden exitosa con ID
- Stock actualizado después de orden
- Consola del navegador con logs

### 2. Evidencia de Logs
```powershell
# Guardar logs completos
docker-compose logs > logs-completos.txt
```

### 3. Diagrama de Arquitectura
- Incluido en el frontend (sección 4)
- Frontend → API Gateway → Order Service → (Product Service + Shipping Service)

### 4. Código Fuente
- `frontend/index.html` - Estructura HTML
- `frontend/styles.css` - Estilos modernos
- `frontend/app.js` - Lógica JavaScript
- `docker-compose.yml` - Orquestación
- `api-gateway/nginx.conf` - Reverse proxy

---

## Soporte

Si encuentras problemas no documentados:

1. **Revisa logs completos:**
   ```powershell
   docker-compose logs -f
   ```

2. **Reinicia servicios específicos:**
   ```powershell
   docker-compose restart <nombre-servicio>
   ```

3. **Reinicio completo (limpio):**
   ```powershell
   docker-compose down -v
   docker-compose up --build -d
   ```

4. **Verifica configuración de red:**
   ```powershell
   docker network inspect microservices-project_microservices-net
   ```
