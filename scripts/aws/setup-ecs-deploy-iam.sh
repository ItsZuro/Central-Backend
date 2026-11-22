#!/usr/bin/env bash
# Configura UNA VEZ un rol OIDC exclusivo para desplegar Central-Backend a ECS.
# Ejecutar desde AWS CloudShell como usuario autorizado; no requiere access keys.
set -euo pipefail
umask 077

REGION="us-east-1"
EXPECTED_ACCOUNT="431223004152"
CLUSTER="central-cluster"
SERVICE="central-backend-service"
TASK_FAMILY="central-backend-task"
CONTAINER="central-backend"
DEPLOY_ROLE="central-github-ecs-deploy-role"
GITHUB_SUB="repo:ItsZuro/Central-Backend:ref:refs/heads/feature/cloud-final-project"

command -v aws >/dev/null || { echo "Falta aws CLI" >&2; exit 1; }
command -v jq >/dev/null || { echo "Falta jq" >&2; exit 1; }

ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
if [[ "$ACCOUNT" != "$EXPECTED_ACCOUNT" ]]; then
  echo "Cuenta AWS inesperada: $ACCOUNT. No se modificó IAM." >&2
  exit 1
fi

SERVICE_INFO=$(aws ecs describe-services \
  --cluster "$CLUSTER" --services "$SERVICE" --region "$REGION" \
  --query 'services[0].[serviceArn,taskDefinition]' --output text)
read -r SERVICE_ARN TASK_ARN <<< "$SERVICE_INFO"
if [[ -z "$SERVICE_ARN" || -z "$TASK_ARN" || "$TASK_ARN" == "None" ]]; then
  echo "No se encontró el servicio $CLUSTER/$SERVICE. No se modificó IAM." >&2
  exit 1
fi

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

aws ecs describe-task-definition \
  --task-definition "$TASK_ARN" --region "$REGION" \
  --query taskDefinition --output json > "$WORKDIR/task.json"

jq -e --arg family "$TASK_FAMILY" --arg container "$CONTAINER" \
  '.family == $family and ([.containerDefinitions[].name] | index($container) != null)' \
  "$WORKDIR/task.json" >/dev/null || {
    echo "La definición actual no coincide con el contenedor y familia esperados." >&2
    exit 1
  }

# Recupera los roles reales de la tarea; no se adivinan sus nombres.
ROLES=$(jq -c '[.executionRoleArn, .taskRoleArn]
  | map(select(. != null and . != "")) | unique' "$WORKDIR/task.json")
if [[ "$ROLES" == "[]" ]]; then
  echo "La tarea no tiene roles IAM: no es seguro crear esta política." >&2
  exit 1
fi

OIDC_PROVIDER="arn:aws:iam::$EXPECTED_ACCOUNT:oidc-provider/token.actions.githubusercontent.com"

jq -n --arg provider "$OIDC_PROVIDER" --arg sub "$GITHUB_SUB" '{
  Version:"2012-10-17",
  Statement:[{
    Effect:"Allow",
    Principal:{Federated:$provider},
    Action:"sts:AssumeRoleWithWebIdentity",
    Condition:{StringEquals:{
      "token.actions.githubusercontent.com:aud":"sts.amazonaws.com",
      "token.actions.githubusercontent.com:sub":$sub
    }}
  }]
}' > "$WORKDIR/trust.json"

if aws iam get-role --role-name "$DEPLOY_ROLE" >/dev/null 2>&1; then
  aws iam get-role --role-name "$DEPLOY_ROLE" \
    --query 'Role.AssumeRolePolicyDocument' --output json > "$WORKDIR/current-trust.json"
  jq -e --arg provider "$OIDC_PROVIDER" --arg sub "$GITHUB_SUB" '
    .Statement | length == 1 and
    .[0].Effect == "Allow" and
    .[0].Principal.Federated == $provider and
    .[0].Action == "sts:AssumeRoleWithWebIdentity" and
    .[0].Condition.StringEquals["token.actions.githubusercontent.com:aud"] == "sts.amazonaws.com" and
    .[0].Condition.StringEquals["token.actions.githubusercontent.com:sub"] == $sub
  ' "$WORKDIR/current-trust.json" >/dev/null || {
    echo "El rol existente tiene una política de confianza diferente. Revisa IAM manualmente." >&2
    exit 1
  }
  echo "Rol OIDC existente verificado: $DEPLOY_ROLE"
else
  aws iam create-role \
    --role-name "$DEPLOY_ROLE" \
    --assume-role-policy-document "file://$WORKDIR/trust.json" \
    --description "Despliegue ECS de Central-Backend solo desde feature/cloud-final-project" \
    --query 'Role.Arn' --output text
fi

jq -n --arg service "$SERVICE_ARN" --argjson roles "$ROLES" '{
  Version:"2012-10-17",
  Statement:[
    {
      Sid:"DescribeCentralECS",
      Effect:"Allow",
      Action:["ecs:DescribeServices","ecs:DescribeTaskDefinition"],
      Resource:"*"
    },
    {
      Sid:"RegisterEcsRevision",
      Effect:"Allow",
      Action:["ecs:RegisterTaskDefinition"],
      Resource:"*"
    },
    {
      Sid:"UpdateCentralBackendServiceOnly",
      Effect:"Allow",
      Action:["ecs:UpdateService"],
      Resource:$service
    },
    {
      Sid:"PassExistingEcsTaskRolesOnly",
      Effect:"Allow",
      Action:["iam:PassRole"],
      Resource:$roles,
      Condition:{StringEquals:{"iam:PassedToService":"ecs-tasks.amazonaws.com"}}
    }
  ]
}' > "$WORKDIR/permission.json"

aws iam put-role-policy \
  --role-name "$DEPLOY_ROLE" \
  --policy-name "central-backend-ecs-deploy" \
  --policy-document "file://$WORKDIR/permission.json"

echo "IAM listo. Rol: arn:aws:iam::$EXPECTED_ACCOUNT:role/$DEPLOY_ROLE"
echo "Servicio permitido: $SERVICE_ARN"
echo "Roles de tarea autorizados (sin publicar secretos):"
jq -r '.[]' <<< "$ROLES"
echo "Siguiente paso: habilitar ENABLE_BACKEND_ECS_DEPLOY=true en las variables de GitHub Actions."
