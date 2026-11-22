# Despliegue automático de Central-Backend en ECS

Este repositorio mantiene el CI/CD del backend en `feature/cloud-final-project`.

## Flujo

1. `quality`: ejecuta pruebas Maven con Java 21.
2. `container`: construye Docker y comprueba los endpoints de salud.
3. `ecr-publish`: construye y publica una imagen ECR con etiqueta inmutable `<commit-sha>-<run-id>-<attempt>`.
4. `ecs-deploy`: si la variable de repositorio `ENABLE_BACKEND_ECS_DEPLOY` es `true`, obtiene credenciales AWS temporales mediante OIDC, registra una nueva revisión de `central-backend-task`, actualiza `central-backend-service` y espera a que se estabilice.

Recursos: región `us-east-1`, clúster `central-cluster`, servicio `central-backend-service`, contenedor `central-backend`.

## Primera habilitación

El propietario debe ejecutar una sola vez `scripts/aws/setup-ecs-deploy-iam.sh` en AWS CloudShell, comprobar la política del rol `central-github-ecs-deploy-role` y crear la variable de Actions `ENABLE_BACKEND_ECS_DEPLOY=true`. No se usan claves AWS permanentes.

Una vez habilitado, cada push a `feature/cloud-final-project` dispara automáticamente las cuatro etapas. Los workflows manuales (`workflow_dispatch`) también ejecutan las etapas pertinentes en esa rama.

## Comprobaciones

- Verificar que `ecs-deploy` termine en verde, no `skipped`.
- Confirmar que la revisión de tarea coincide con la desplegada y que `runningCount == desiredCount`.
- Revisar `/actuator/health` por el balanceador o CloudFront.
- Si falla el despliegue, revisar los logs de GitHub Actions y los eventos del servicio ECS. El pipeline intenta restaurar la tarea previa cuando falla el estabilizado; este intento de restauración debe verificarse.

## Nota

No habilitar `ENABLE_BACKEND_ECS_DEPLOY` sin configurar previamente IAM. La edición de sucursales y las coordenadas para delivery se administran por separado desde el frontend.
