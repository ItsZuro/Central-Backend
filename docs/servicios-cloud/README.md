# Restaurant Central — Proyecto Final Servicios Cloud

Rama de trabajo: `feature/cloud-final-project`. No fusionar con `main` hasta validar los cambios.

## Adaptaciones implementadas

- Redis retirado de dependencias Maven y Docker Compose.
- `TokenBlacklistService` utiliza PostgreSQL para almacenar/revisar tokens revocados mediante `TokenAuditRepository`; nuevas entradas `BLACKLISTED` guardan un hash SHA-256.
- Datasource de `prod` parametrizado con `DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `DB_SSLMODE`.
- Cliente S3 usa `DefaultCredentialsProvider`, compatible con rol IAM de tarea ECS y perfiles locales.
- CI habilitado para la rama de adaptación; Docker Hub solo se publica desde `main`.
- Pruebas unitarias nuevas para revocación de tokens.

## Variables para ECS (sin credenciales en GitHub)

`SPRING_PROFILES_ACTIVE=prod`, `DB_HOST=<endpoint-RDS>`, `DB_PORT=5432`, `DB_SSLMODE=require`, `POSTGRES_DB=centraldb`, `POSTGRES_USER=<usuario-app>`, `POSTGRES_PASSWORD=<secreto>`, `JWT_SECRET=<secreto>`, `AWS_REGION=us-east-1`, `AWS_S3_BUCKET_NAME=<bucket>`, `APP_FRONTEND_URL=<Vercel>`, `APP_CORS_ALLOWED_ORIGINS=<origen-HTTPS>`.

El backend también usa OAuth Google, correo y Niubiz; comprobar configuración de prueba y valores requeridos antes de iniciar Spring Boot. No poner secretos en logs ni en archivos `VITE_*` del frontend.

## Verificaciones aún pendientes

1. Revisar resultado de GitHub Actions (Maven y Docker Compose) tras este commit.
2. Confirmar login, logout, refresh, reservas, productos y `GET /actuator/health`.
3. Verificar conexiones de la API a RDS privado y a S3 con rol IAM.
4. Revisar retención de la auditoría: los eventos antiguos `ISSUED`/`REFRESHED` pueden contener JWT en texto claro.
5. Las revocaciones guardadas solo en el Redis antiguo no se importan; usar tokens de demostración nuevos al migrar.
6. El broker WebSocket en memoria no distribuye eventos entre dos tareas ECS; no afirmar alta disponibilidad WebSocket sin adaptar ese componente.
7. El valor `JPA_DDL_AUTO=update` es solo para el primer laboratorio; en un entorno real se usarán migraciones controladas.

## Alcance previsto del despliegue

Vercel → ALB HTTPS → ECS Fargate (Spring Boot) → RDS PostgreSQL (privado); S3 para archivos y CloudWatch para observabilidad. Empezar con una sola tarea y una base Single-AZ, y realizar una prueba acotada de escalado a dos tareas. La arquitectura Multi-AZ se justifica en el informe sin simular que ya fue implementada.

**Ninguna modificación del código crea automáticamente recursos en AWS.**

## Auditoría de módulos de Restaurant Central (noviembre de 2026)

- Reservas: la página cliente consulta `GET /api/v1/tables/available-times` y registra `POST /api/v1/reservations/me`; el backend bloquea la fila de la mesa para validar solapamientos de reservas concurrentes. Cancelar una reserva futura ya no cambia el estado de ocupación física de la mesa.
- Reseñas: `GET /api/v1/reviews/product/{productId}` permite consultar comentarios de productos. Crear/modificar reseñas requiere autenticación; solo el propietario puede modificarlas.
- Reparto: `GET /api/v1/orders/delivery/assigned-me` lista las entregas del empleado autenticado y `PUT /api/v1/orders/{id}/delivery/status` solo acepta `READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED` para una orden asignada al propio empleado. Se aprovecha temporalmente `ROLE_WAITER`, no existe un rol independiente de repartidor inicializado. El navegador muestra ruta de Google Maps, no es seguimiento GPS automático.
- Niubiz: mantener exclusivamente el **sandbox** ya configurado (`apisandbox.vnforappstest.com` y `static-content-qas.vnforapps.com`). Las credenciales reales se suministran por variables de entorno. Compilación y health checks no prueban la autenticación, la creación de sesión ni la autorización de una tarjeta de prueba; esas comprobaciones requieren secretos sandbox válidos y conectividad.
- Delivery: `DELIVERY_MAX_DISTANCE_KM` controla un umbral provisional por recorrido vial, hasta que se confirme la zona original. No se agrega tarifa de reparto arbitraria al total de la compra.

## Precauciones antes de una demostración en AWS

1. El documento académico plantea ElastiCache/Redis, pero **la rama actual eliminó la dependencia operativa de Redis**, usando PostgreSQL para revocación de tokens. Presentar Redis como componente *propuesto opcional* o actualizar la arquitectura antes de afirmar que se utiliza.
2. Si ECS Fargate corre en **subred privada**, se requiere salida segura a Internet (por ejemplo, NAT Gateway) para Google Maps, Niubiz, correo y Google OAuth, salvo que cada integración disponga de una alternativa compatible. Configurar endpoints VPC cuando proceda (por ejemplo S3), sin asumir que sustituyen el acceso a APIs externas.
3. Mantener secretos fuera de Docker, Vercel y Git; en AWS configurar `SPRING_PROFILES_ACTIVE=prod`, CORS exacto, conectividad RDS, rol IAM para S3 y acceso TLS ALB. No activar ninguna pasarela de producción.
4. Verificar los límites de reserva, roles y pedidos con dos sesiones distintas y PostgreSQL real. La compilación de GitHub Actions **no** sustituye pruebas funcionales end-to-end ni prueba resiliencia Multi-AZ.
5. Para evidencia de Servicios Cloud, utilizar Docker Compose, health checks, diagramas y simulación de falla/crecimiento. No afirmar que ALB, ECS, RDS o Multi-AZ están implementados sin capturas reales.

## Cuentas de desarrollo y seguridad en producción

Con `SPRING_PROFILES_ACTIVE=dev` y `app.demo.seed-users=true` se crean cuentas de demostración en una base de datos de desarrollo; si ya existen, sus contraseñas no se sobreescriben. No utilizar estas credenciales en Internet.

Con `SPRING_PROFILES_ACTIVE=prod`, el inicializador de seguridad registra los roles requeridos, pero **no crea usuarios de prueba ni credenciales estáticas**. Los demás inicializadores académicos (empleados, clientes, productos, proveedores, pedidos, mesas y recompensas) se limitan a `dev`. Crear el administrador inicial en producción mediante un procedimiento seguro y credenciales únicas; no habilitar seeds académicos en RDS. Bases existentes que ya contengan cuentas de prueba requieren limpieza/rotación antes de exposición.

Roles inicializados: `ROLE_CLIENT`, `ROLE_WAITER`, `ROLE_CHEF`, `ROLE_CASHIER`, `ROLE_ADMIN`, `ROLE_SUPPLIER`, `ROLE_MANAGER`. Demo: `admin`, `cliente1`, `cliente2`, `jose_m`, `maria_w`, `carlos_c`, `sanfernando_user`, `gloria_user`. No existe cuenta de cajero independiente predefinida; el gerente incorpora `ROLE_CASHIER`.
