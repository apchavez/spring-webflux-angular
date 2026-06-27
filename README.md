# Reactive Customer Service API

API REST reactiva desarrollada con **Spring Boot WebFlux** para gestionar clientes con operaciones de registro y consulta.

## Tecnologías

* Java 21
* Spring Boot 3.5.3
* Spring WebFlux (Mono / Flux)
* Spring Data R2DBC
* Spring Security
* Spring Boot Actuator
* H2 (perfil dev) / PostgreSQL (perfil prod)
* Gradle + JaCoCo
* Lombok
* Springdoc OpenAPI (Swagger UI)
* Docker + docker-compose
* Kubernetes (manifests en `k8s/`)

## Funcionalidades

* Crear clientes con validación de dominio
* Evitar IDs duplicados
* Consultar cliente por ID
* Listar clientes activos
* Manejo de errores centralizado
* Rate limiting por IP
* Cobertura mínima del 80 % en dominio y aplicación

---

## Ejecutar localmente (H2 en memoria)

```bash
./gradlew bootRun
```

La aplicación inicia en `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

## Ejecutar con Docker (PostgreSQL)

```bash
docker compose up --build
```

## Desplegar en Kubernetes

Antes del primer deploy, editar `k8s/deployment.yaml` y reemplazar `OWNER` por el usuario u organización de GitHub:

```bash
# Ejemplo: ghcr.io/apchavez/reactive-customer-service:latest
sed -i 's/OWNER/tu-usuario-github/' k8s/deployment.yaml
```

Aplicar los manifests en orden:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
```

Para apuntar a un commit concreto en lugar de `latest`:

```bash
kubectl set image deployment/customer-service \
  customer-service=ghcr.io/OWNER/reactive-customer-service:sha-abc1234 \
  -n customer-service
```

> Actualizar `k8s/secret.yaml` con las credenciales reales antes de desplegar en producción.  
> El Ingress asume un NGINX Ingress Controller y el host `customer-service.local`.

---

## Endpoints disponibles

Base path: `/api/v1/customers`

### POST `/api/v1/customers` — Crear cliente

El campo `id` es opcional; si se omite, se genera automáticamente.

**Request body:**
```json
{ "nombre": "Alex", "apellido": "Prieto", "estado": "ACTIVE", "edad": 30 }
```

**Respuesta 201:**
```json
{ "id": 7, "nombre": "Alex", "apellido": "Prieto", "estado": "ACTIVE", "edad": 30 }
```

**Respuesta 409 — ID duplicado:**
```json
{ "timestamp": "2026-06-26T10:00:00", "status": 409, "error": "Conflict", "mensaje": "Ya existe un cliente con el ID: 100" }
```

**Respuesta 400 — campos inválidos (Bean Validation):**
```json
{
  "timestamp": "2026-06-26T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "mensaje": "Error de validación de campos",
  "errores": [
    { "campo": "nombre", "mensaje": "El nombre es requerido" },
    { "campo": "edad",   "mensaje": "La edad debe ser mayor que cero" }
  ]
}
```

| Caso | Código |
|---|---|
| Creación exitosa | `201 Created` |
| ID duplicado | `409 Conflict` |
| Campos inválidos (Bean Validation) | `400 Bad Request` |

### GET `/api/v1/customers/active` — Listar clientes activos

**Respuesta 200:**
```json
[
  { "id": 1, "nombre": "Alex", "apellido": "Prieto", "estado": "ACTIVE", "edad": 30 }
]
```

Siempre retorna 200 (array vacío si no hay clientes activos).

### GET `/api/v1/customers/{id}` — Buscar por ID

**Respuesta 200:**
```json
{ "id": 1, "nombre": "Alex", "apellido": "Prieto", "estado": "ACTIVE", "edad": 30 }
```

**Respuesta 404:**
```json
{ "timestamp": "2026-06-26T10:00:00", "status": 404, "error": "Not Found", "mensaje": "No se encontró un cliente con el ID: 9999" }
```

| Caso | Código |
|---|---|
| Cliente encontrado | `200 OK` |
| No existe | `404 Not Found` |

---

## Ejemplos con cURL

```bash
# Crear cliente (ID autogenerado)
curl -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Alex","apellido":"Prieto","estado":"ACTIVE","edad":30}'

# Buscar por ID
curl http://localhost:8080/api/v1/customers/1

# Listar activos
curl http://localhost:8080/api/v1/customers/active
```

---

## Arquitectura

Hexagonal (Ports & Adapters):

```text
src/main/java/com/apchavez/customers
├── domain
│   ├── model          Customer, CustomerState
│   ├── exception      Excepciones de dominio
│   ├── port           CustomerRepositoryPort (interfaz)
│   └── service        CustomerDomainService
├── application
│   └── CustomerApplicationService
└── infrastructure
    ├── config         Security, RateLimiting, OpenApi, Startup
    ├── mapper         CustomerMapper
    ├── persistence    CustomerEntity, CustomerR2dbcRepository, CustomerPersistenceAdapter
    └── web            CustomerController, DTOs, GlobalExceptionHandler
```

**Regla de dependencias:** `infrastructure` → `application` → `domain`  
El dominio no conoce las capas externas (verificado por `ArchitectureTest`).

---

## Tests

```bash
./gradlew test          # ejecuta todos los tests + JaCoCo
```

| Tipo | Clase |
|---|---|
| Modelo de dominio — unit + property-based (jqwik) | `CustomerDomainTest` |
| Servicio de dominio — unit | `CustomerDomainServiceTest` |
| Servicio de aplicación — unit | `CustomerApplicationServiceTest` |
| Adaptador de persistencia — `@DataR2dbcTest` | `CustomerPersistenceAdapterTest` |
| Controlador REST — integración completa | `CustomerControllerIntegrationTest` |
| Actuator probes (liveness/readiness) | `ActuatorHealthTest` |
| Arquitectura hexagonal — ArchUnit | `ArchitectureTest` |

---

## CI / CD

El pipeline de GitHub Actions (`.github/workflows/ci.yml`) ejecuta tres jobs en cada push:

| Job | Se ejecuta en | Qué hace |
|---|---|---|
| `test` | todo push / PR | Compila, pruebas, JaCoCo, cobertura ≥ 80 % |
| `k8s-validate` | todo push / PR | Valida los manifests con **kubeconform** |
| `docker` | push a `main` (tras `test`) | Build + push a **ghcr.io** con tags `latest` y `sha-XXXXXXX` |

La imagen publicada queda disponible en:

```
ghcr.io/OWNER/reactive-customer-service:latest
ghcr.io/OWNER/reactive-customer-service:sha-abc1234
```

No se requiere ningún secreto adicional — el job `docker` usa el `GITHUB_TOKEN` integrado de GitHub Actions.

---

## Kubernetes

Los manifests en `k8s/` están preparados para producción:

| Archivo | Descripción |
|---|---|
| `namespace.yaml` | Namespace `customer-service` |
| `configmap.yaml` | Configuración no sensible (perfil, host DB) |
| `secret.yaml` | Credenciales de base de datos (base64) |
| `deployment.yaml` | 2 réplicas, imagen ghcr.io, probes, resource limits, securityContext |
| `service.yaml` | ClusterIP en puerto 80 |
| `ingress.yaml` | NGINX Ingress en `customer-service.local` |

Los probes de k8s apuntan a los endpoints de Spring Boot Actuator:

* **Liveness:** `GET /actuator/health/liveness`
* **Readiness:** `GET /actuator/health/readiness`

---

## Postman

Importar `reactive-customer-service.postman_collection.json` en Postman.

La colección incluye dos carpetas:

**Customers** — 7 requests con test scripts automáticos (ejecutar en orden):

| # | Request | Descripción |
|---|---|---|
| 1 | POST crear (sin `id`) | Crea cliente; guarda el ID generado en la variable `customerId` |
| 2 | POST crear con `id: 100` | Crea cliente con ID explícito 100 |
| 3 | POST duplicado → 409 | Envía `id: 100` de nuevo; depende del paso 2 |
| 4 | POST inválido → 400 | Campos vacíos y estado inválido; verifica el array `errores[].campo` |
| 5 | GET activos → 200 | Verifica que todos los clientes retornados tengan `estado: "ACTIVE"` |
| 6 | GET por ID → 200 | Usa `{{customerId}}` del paso 1; verifica los 5 campos de respuesta |
| 7 | GET ID inexistente → 404 | Busca ID 99999; verifica campo `mensaje` en la respuesta |

**Actuator** — 3 requests de health (sin dependencias):

| Request | Verifica |
|---|---|
| GET `/actuator/health` | `status: "UP"` |
| GET `/actuator/health/liveness` | `status: "UP"` (k8s liveness probe) |
| GET `/actuator/health/readiness` | `status: "UP"` (k8s readiness probe) |

---

## Autor

Proyecto orientado a demostrar habilidades en desarrollo backend reactivo con Java y Spring Boot.
