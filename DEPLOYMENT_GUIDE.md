# Guía de Despliegue en Kubernetes con Kompose

## Descripción General

Este documento proporciona un procedimiento paso a paso para desplegar la arquitectura de microservicios en Kubernetes utilizando Kompose. La arquitectura incluye un API Gateway (Nginx), tres microservicios (Product Service, Order Service, Shipping Service) y dos bases de datos (PostgreSQL, MySQL).

## Requisitos Previos

- Docker Desktop instalado y ejecutándose
- Minikube instalado y configurado
- kubectl instalado y configurado
- Kompose instalado
- Git (opcional, para control de versiones)

Verificar instalación:
```powershell
docker --version
kubectl version --client
minikube version
kompose version
```

## Estructura del Proyecto

```
microservices-project/
├── api-gateway/
│   ├── Dockerfile
│   └── nginx.conf
├── frontend/
│   ├── index.html
│   ├── app.js
│   └── styles.css
├── product-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── order-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── shipping-service/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app/
└── docker-compose.yml
```

## Fase 1: Preparación de Archivos

### 1.1 Actualizar docker-compose.yml

Modificar la sección del api-gateway para usar contexto de raíz en lugar de bind-mounts:

```yaml
api-gateway:
  build:
    context: .
    dockerfile: api-gateway/Dockerfile
  # ... resto de configuración
  # Remover los volúmenes de bind-mount (frontend y nginx.conf)
```

### 1.2 Actualizar Dockerfile del API Gateway

```dockerfile
FROM nginx:alpine

# Copiar frontend y configuración desde contexto raíz
COPY frontend /usr/share/nginx/html
COPY api-gateway/nginx.conf /etc/nginx/nginx.conf

# Crear directorios y ajustar permisos
RUN mkdir -p /var/log/nginx && \
    chmod 755 /var/log/nginx && \
    chown -R nginx:nginx /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
```

### 1.3 Configurar CORS en nginx.conf

Agregar headers CORS dentro de cada location proxy. Ejemplo para /v1/products:

```nginx
location ^~ /v1/products {
    if ($request_method = OPTIONS) {
        add_header 'Access-Control-Allow-Origin' '*' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
        add_header 'Access-Control-Allow-Headers' 'Content-Type, Authorization, X-Requested-With' always;
        add_header 'Access-Control-Max-Age' '3600' always;
        return 204;
    }
    add_header 'Access-Control-Allow-Origin' '*' always;
    add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
    add_header 'Access-Control-Allow-Headers' 'Content-Type, Authorization, X-Requested-With' always;
    proxy_pass http://product_service;
    # ... resto de configuración proxy
}
```

Repetir para todos los endpoints (/v1/products, /products, /v1/orders, /orders).

### 1.4 Actualizar Frontend (app.js)

Cambiar la URL base para usar el origen dinámico:

```javascript
const API_BASE = window.location.origin;
```

## Fase 2: Generar Manifiestos de Kubernetes

### 2.1 Convertir docker-compose.yml a Kubernetes YAML

Ejecutar kompose desde la raíz del proyecto:

```powershell
cd "C:/Users/mlata/Documents/SistemasDistribuidos/microservices-project"
kompose convert -f docker-compose.yml --out k8s --controller deployment --volumes persistentVolumeClaim --with-kompose-annotation=false
```

Este comando generará archivos YAML en la carpeta `k8s/`:
- `api-gateway-service.yaml`, `api-gateway-deployment.yaml`
- `product-service-service.yaml`, `product-service-deployment.yaml`
- `order-service-service.yaml`, `order-service-deployment.yaml`
- `shipping-service-service.yaml`, `shipping-service-deployment.yaml`
- `product-db-service.yaml`, `product-db-deployment.yaml`
- `order-db-service.yaml`, `order-db-deployment.yaml`
- `product-data-persistentvolumeclaim.yaml`
- `order-data-persistentvolumeclaim.yaml`

### 2.2 Ajustar el Manifiesto Generado (k8s)

**Cambios en el Service del API Gateway:**

```yaml
apiVersion: v1
kind: Service
metadata:
  labels:
    io.kompose.service: api-gateway
  name: api-gateway
spec:
  type: LoadBalancer
  ports:
    - name: "http"
      port: 8080
      targetPort: 8080
  selector:
    io.kompose.service: api-gateway
```

**Cambios en el Deployment del API Gateway:**

```yaml
spec:
  containers:
    - image: microservices-project/api-gateway:latest
      imagePullPolicy: Never
      command:
        - nginx
        - "-g"
        - "daemon off;"
      livenessProbe:
        httpGet:
          path: /health
          port: 8080
        initialDelaySeconds: 5
        periodSeconds: 10
        timeoutSeconds: 3
        failureThreshold: 3
      readinessProbe:
        httpGet:
          path: /health
          port: 8080
        initialDelaySeconds: 3
        periodSeconds: 10
        timeoutSeconds: 3
      name: api-gateway
      ports:
        - containerPort: 8080
          protocol: TCP
```

**Cambios en el Deployment de product-service:**

```yaml
spec:
  containers:
    - image: microservices-project/product-service:latest
      imagePullPolicy: Never
      name: product-service
      ports:
        - containerPort: 8081
          protocol: TCP
      livenessProbe:
        httpGet:
          path: /v1/products
          port: 8081
        failureThreshold: 3
        periodSeconds: 10
        timeoutSeconds: 5
      readinessProbe:
        httpGet:
          path: /v1/products
          port: 8081
        initialDelaySeconds: 5
        periodSeconds: 10
        timeoutSeconds: 5
```

**Cambios en el Deployment de order-service:**

```yaml
spec:
  containers:
    - image: microservices-project/order-service:latest
      imagePullPolicy: Never
      name: order-service
      ports:
        - containerPort: 8083
          protocol: TCP
      livenessProbe:
        httpGet:
          path: /q/health
          port: 8083
        failureThreshold: 3
        periodSeconds: 10
        timeoutSeconds: 5
      readinessProbe:
        httpGet:
          path: /q/health
          port: 8083
        initialDelaySeconds: 5
        periodSeconds: 10
        timeoutSeconds: 5
```

**Cambios en el Deployment de shipping-service:**

```yaml
spec:
  containers:
    - image: microservices-project/shipping-service:latest
      imagePullPolicy: Never
      name: shipping-service
      ports:
        - containerPort: 8082
          protocol: TCP
      livenessProbe:
        tcpSocket:
          port: 8082
        failureThreshold: 3
        periodSeconds: 10
        timeoutSeconds: 5
      readinessProbe:
        tcpSocket:
          port: 8082
        initialDelaySeconds: 3
        periodSeconds: 10
        timeoutSeconds: 5
```

**Agregar Services para bases de datos y microservicios internos:**

```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: product-db
spec:
  type: ClusterIP
  ports:
    - name: "postgres"
      port: 5432
      targetPort: 5432
  selector:
    io.kompose.service: product-db

---
apiVersion: v1
kind: Service
metadata:
  name: order-db
spec:
  type: ClusterIP
  ports:
    - name: "mysql"
      port: 3306
      targetPort: 3306
  selector:
    io.kompose.service: order-db

---
apiVersion: v1
kind: Service
metadata:
  name: product-service
spec:
  type: ClusterIP
  ports:
    - name: "http"
      port: 8081
      targetPort: 8081
  selector:
    io.kompose.service: product-service

---
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  type: ClusterIP
  ports:
    - name: "http"
      port: 8083
      targetPort: 8083
  selector:
    io.kompose.service: order-service

---
apiVersion: v1
kind: Service
metadata:
  name: shipping-service
spec:
  type: ClusterIP
  ports:
    - name: "http"
      port: 8082
      targetPort: 8082
  selector:
    io.kompose.service: shipping-service
```

## Fase 3: Construcción de Imágenes Docker

Configurar el entorno de Minikube para usar su daemon de Docker:

```powershell
minikube docker-env | Invoke-Expression
```

Construir las imágenes en el daemon de Minikube:

```powershell
# API Gateway
docker build -f api-gateway/Dockerfile -t microservices-project/api-gateway:latest .

# Product Service
docker build -t microservices-project/product-service:latest product-service

# Order Service
docker build -t microservices-project/order-service:latest order-service

# Shipping Service
docker build -t microservices-project/shipping-service:latest shipping-service
```

## Fase 4: Crear Namespace de Kubernetes

```powershell
kubectl create namespace microservices
```

## Fase 5: Desplegar en Kubernetes

Aplicar todos los manifiestos:

```powershell
cd "C:/Users/mlata/Documents/SistemasDistribuidos/microservices-project"
kubectl apply -f k8s -n microservices
```

Verificar que todos los pods estén corriendo:

```powershell
kubectl get pods -n microservices
```

Resultado esperado:
```
NAME                               READY   STATUS    RESTARTS   AGE
api-gateway-7ff9f6c46-n4pq9        1/1     Running   0          2m
order-db-7b8f7654f6-5m4xr          1/1     Running   0          2m
order-service-bd5b8b67d-5qwpw      1/1     Running   0          2m
product-db-7b7c4bf797-nh98j        1/1     Running   0          2m
product-service-74b794c85c-rrd4w   1/1     Running   0          2m
shipping-service-55cf8877f-5v5zj   1/1     Running   0          2m
```

## Fase 6: Acceso a la Aplicación

### Opción A: Port Forward (Recomendado para desarrollo)

```powershell
kubectl port-forward service/api-gateway 18080:8080 -n microservices
```

Acceder en el navegador: `http://localhost:18080`

### Opción B: Usar Minikube Service

```powershell
minikube service api-gateway -n microservices
```

Esto abrirá automáticamente la aplicación en el navegador.

### Opción C: NodePort Manual

```powershell
kubectl get svc api-gateway -n microservices
```

Encontrar el puerto asignado (ej: 32365) y acceder a través de la IP de Minikube:

```powershell
minikube ip
```

Resultado: `http://192.168.49.2:32365`

## Verificación de Servicios

Listar todos los servicios desplegados:

```powershell
kubectl get svc -n microservices
```

Acceder a logs de un servicio específico:

```powershell
kubectl logs deployment/product-service -n microservices --tail=50
kubectl logs deployment/api-gateway -n microservices --tail=50
```

## Solución de Problemas

### Pod en CrashLoopBackOff

Verificar logs del pod:

```powershell
kubectl describe pod <pod-name> -n microservices
kubectl logs pod/<pod-name> -n microservices
```

### Conexión rechazada a un microservicio

Verificar DNS resolution dentro del cluster:

```powershell
kubectl run -it --rm debug --image=busybox --restart=Never -n microservices -- sh
# Dentro del pod
nslookup product-service
```

### Port-forward no funciona

Intentar con un puerto diferente:

```powershell
kubectl port-forward service/api-gateway 18080:8080 -n microservices
```

O reiniciar el port-forward:

```powershell
# Presionar Ctrl+C para detener el actual
kubectl port-forward service/api-gateway 18080:8080 -n microservices
```

## Limpiar Despliegue

Eliminar el namespace (esto elimina todos los recursos dentro):

```powershell
kubectl delete namespace microservices
```

O eliminar solo los recursos:

```powershell
kubectl delete -f k8s -n microservices
```

## Notas Importantes

1. **imagePullPolicy: Never**: Las imágenes se construyen localmente en Minikube, no se intenta descargar del registry.

2. **Probes**: 
   - API Gateway usa httpGet con endpoint `/health`
   - Product Service usa httpGet con endpoint `/v1/products`
   - Order Service usa httpGet con endpoint `/q/health` (Quarkus)
   - Shipping Service usa tcpSocket en puerto 8082

3. **CORS**: Todos los endpoints proxy incluyen headers CORS para permitir peticiones desde el frontend.

4. **Persistencia**: Los datos de las bases de datos se persisten en PersistentVolumeClaims (PVCs) de Kubernetes.

5. **Port-forward**: Si es necesario cerrar y reiniciar, asegurarse de no tener otro proceso usando el puerto.

## Comandos Útiles Rápidos

```powershell
# Ver estado general del namespace
kubectl get all -n microservices

# Ver eventos del namespace
kubectl get events -n microservices

# Describir un pod específico
kubectl describe pod <pod-name> -n microservices

# Ejecutar comando dentro de un pod
kubectl exec -it <pod-name> -n microservices -- sh

# Ver recursos de un pod
kubectl top pod <pod-name> -n microservices

# Escalar un deployment
kubectl scale deployment <deployment-name> --replicas=3 -n microservices

# Actualizar imagen de un deployment
kubectl set image deployment/<deployment-name> <container-name>=<new-image> -n microservices
```

## Referencias

- Kompose: https://kompose.io/
- Kubernetes Documentation: https://kubernetes.io/docs/
- Minikube: https://minikube.sigs.k8s.io/
- kubectl CLI: https://kubernetes.io/docs/reference/kubectl/
