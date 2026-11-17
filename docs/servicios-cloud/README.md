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
