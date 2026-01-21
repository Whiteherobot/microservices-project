FROM nginx:alpine

# Copiar frontend estático y configuración de Nginx (el contexto de build ahora es la raíz del repo)
COPY frontend /usr/share/nginx/html
COPY api-gateway/nginx.conf /etc/nginx/nginx.conf

# Crear directorio de logs y ajustar permisos para el usuario nginx
RUN mkdir -p /var/log/nginx && \
    chmod 755 /var/log/nginx && \
    chown -R nginx:nginx /usr/share/nginx/html

# Exponer puerto interno del gateway
EXPOSE 8080

# Health check
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

# Comando por defecto (Nginx se ejecuta en foreground)
CMD ["nginx", "-g", "daemon off;"]
