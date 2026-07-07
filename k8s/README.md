# Kubernetes Deployment Guide

This directory contains everything needed to run the URL Shortener on **k3d** (Kubernetes in Docker for local development). All monitoring tools (Prometheus, Grafana, Jaeger, Loki, Alloy) are included.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [File Layout](#file-layout)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Step-by-Step Explanation](#step-by-step-explanation)
  - [1. Namespace](#1-namespace)
  - [2. ConfigMap and Secret](#2-configmap-and-secret)
  - [3. Infrastructure (Postgres, Redis, Kafka)](#3-infrastructure)
  - [4. Application Services](#4-application-services)
  - [5. PodDisruptionBudgets](#5-poddisruptionbudgets)
  - [6. Ingress](#6-ingress)
  - [7. Monitoring](#7-monitoring)
- [Useful Commands](#useful-commands)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

```
                   ┌─────────────────────────────────────────────┐
                   │          k3d LoadBalancer (:8080)            │
                   │         (Traefik Ingress Controller)         │
                   └──────────────┬──────────────────────────────┘
                                  │
                          ┌───────▼────────┐
                          │   Ingress Rule  │
                          │  /api/v1/urls → │
                          │  /{segment}   → │
                          │  /api/v1/ana → │
                          │  /actuator/*  → │
                          └───────┬────────┘
                                  │
                     ┌────────────┼────────────┐
                     │            │            │
              ┌──────▼──┐  ┌─────▼─────┐  ┌───▼──────────┐
              │ Shortener│  │ Redirect   │  │ Analytics    │
              │ (:8081)  │  │ (:8082)    │  │ (:8083)      │
              └──────┬───┘  └─────┬──────┘  └──────┬───────┘
                     │            │                 │
              ┌──────┼────────────┼─────────────────┼──────────┐
              │      │            │                 │          │
              │  ┌───▼────────────▼─────────────────▼───┐      │
              │  │           Redis (:6379)               │      │
              │  └──────────────────┬───────────────────┘      │
              │                     │                          │
              │  ┌──────────────────▼───────────────────┐      │
              │  │       PostgreSQL (:5432)               │      │
              │  └──────────────────┬───────────────────┘      │
              │                     │                          │
              │  ┌──────────────────▼───────────────────┐      │
              │  │       Kafka (:9092)                    │      │
              │  └────────────────────────────────────────┘      │
              │                                                  │
              │  ┌────────────────────────────────────────┐      │
              │  │        Monitoring Stack                  │      │
              │  │  Prometheus → Grafana (metrics)          │      │
              │  │  Jaeger (tracing)                        │      │
              │  │  Loki + Alloy (logs)                     │      │
              │  └────────────────────────────────────────┘      │
              └──────────────────────────────────────────────────┘
```

There are **15 pods** running inside the `url-shortener` namespace:

| Pod | Type | Purpose |
|-----|------|---------|
| `postgres-0` | StatefulSet | Primary database (URL mappings + analytics + ID checkpoint) |
| `redis-0` | StatefulSet | Cache, Bloom filter, rate limiter, ID generation |
| `kafka-0` | StatefulSet | Async analytics event pipeline |
| `shortener-*` | Deployment (2 replicas) | URL CRUD, QR generation, versioning |
| `redirect-*` | Deployment (2 replicas) | Redirect handling, password verify, analytics producer |
| `analytics-*` | Deployment (1 replica) | Kafka consumer, GeoIP, analytics query API |
| `gateway-*` | Deployment (2 replicas) | Single entry point, rate limiting, circuit breakers |
| `prometheus-*` | Deployment | Metrics collection |
| `grafana-*` | Deployment | Metrics/logs/traces visualization |
| `jaeger-*` | Deployment | Distributed tracing |
| `loki-*` | StatefulSet | Log storage |
| `alloy-*` | DaemonSet (1 per node) | Log scraping from containers |

### Kubernetes Resource Types — Plain English

| Resource | Like... | What it does | When to use it |
|----------|---------|-------------|----------------|
| **Namespace** | A folder | Groups related resources so they don't clutter the cluster | Always — isolates your app from everything else on the cluster |
| **ConfigMap** | A `.env` file | Stores plain-text config (URLs, hostnames, feature flags) that pods read as env vars | When you have config that changes between environments (dev/staging/prod) |
| **Secret** | A locked `.env` file | Same as ConfigMap but values are base64-encoded (passwords, API keys) | For anything you'd put in `.gitignore` — DB passwords, encryption keys, tokens |
| **PersistentVolumeClaim** | A storage request ticket | Asks the cluster for disk space (1GB, 10GB, etc.) that survives pod restarts | When a pod needs to remember data across restarts — databases, message queues |
| **Deployment** | An elevator | Runs identical pods (your app) with a desired count (e.g., "always keep 2 copies running"). If one crashes, it starts a new one. Supports rolling updates (zero-downtime changes). | Any **stateless** app — services where any replica can handle any request (shortener, redirect, gateway, Prometheus, Grafana, Jaeger) |
| **StatefulSet** | A named parking spot | Like Deployment but each pod gets a fixed name (`postgres-0`, never `postgres-1` unless you scale) and its own persistent disk. Pods start and stop in order. | Any **stateful** app — databases (Postgres, Redis), message queues (Kafka). Things where each instance has its own identity and data |
| **DaemonSet** | A newspaper delivery route | Runs exactly one pod on every machine in the cluster. When you add a node, a new pod appears automatically. | Infrastructure agents that need to be everywhere — log collectors (Alloy), monitoring agents, network proxies |
| **Service** | A receptionist | Gives pods a stable IP and DNS name so other pods can find them. Pods come and go (crashes, scaling), but the Service name stays the same. | Almost every pod — any time other pods need to talk to it |
| **Ingress** | A reception desk at the building entrance | The single door from the outside world into your cluster. Routes `yourdomain.com/foo` to one internal Service, `yourdomain.com/bar` to another. | The one public entry point for your app — never expose a Service directly to the internet |
| **PodDisruptionBudget** | A "don't kick us all out at once" rule | Tells Kubernetes: "You may evict pods for maintenance, but keep at least N running at all times." Prevents downtime during node drains or cluster upgrades. | Any deployment where losing all replicas at once would cause downtime — production services with 2+ replicas |

---

## File Layout

```
k8s/
├── README.md                          ← you are here
├── namespace.yaml                     ← creates the url-shortener namespace
├── kustomization.yaml                 ← wires everything together (one-command deploy)
│
├── config/
│   ├── env-config.yaml                ← ConfigMap: shared env vars (DB host, Redis, Kafka, etc.)
│   └── env-secret.yaml                ← Secret: passwords (DB, cookie encryption)
│
├── infrastructure/
│   ├── postgres-statefulset.yaml      ← PostgreSQL 17.5 with persistent volume
│   ├── postgres-service.yaml          ← ClusterIP service on port 5432
│   ├── postgres-pvc.yaml              ← 1GB persistent volume claim
│   ├── redis-statefulset.yaml         ← Redis Stack 7.4.0 (with RedisBloom)
│   ├── redis-service.yaml             ← ClusterIP service on port 6379
│   ├── kafka-statefulset.yaml         ← Kafka 4.1.1 in KRaft mode (no ZooKeeper)
│   └── kafka-service.yaml             ← ClusterIP service on port 9092
│
├── apps/
│   ├── shortener-deployment.yaml      ← 2 replicas, port 8081, health probes
│   ├── shortener-service.yaml         ← ClusterIP service on port 8081
│   ├── redirect-deployment.yaml       ← 2 replicas, port 8082, health probes
│   ├── redirect-service.yaml          ← ClusterIP service on port 8082
│   ├── analytics-deployment.yaml      ← 1 replica, port 8083, health probes
│   ├── analytics-service.yaml         ← ClusterIP service on port 8083
│   ├── gateway-deployment.yaml        ← 2 replicas, port 8080, references all services
│   ├── gateway-service.yaml           ← ClusterIP service on port 8080
│   ├── ingress.yaml                   ← Traefik Ingress: localhost → gateway
│   ├── shortener-pdb.yaml            ← PDB: keep ≥1 shortener pod running during evictions
│   ├── redirect-pdb.yaml             ← PDB: keep ≥1 redirect pod running during evictions
│   └── gateway-pdb.yaml              ← PDB: keep ≥1 gateway pod running during evictions
│
└── monitoring/
    ├── prometheus-deployment.yaml     ← Single replica, scrapes /actuator/prometheus
    ├── prometheus-service.yaml        ← ClusterIP on port 9090
    ├── prometheus-config.yaml         ← ConfigMap: scrape targets for all 4 services
    ├── grafana-deployment.yaml        ← Single replica, pre-provisioned datasources
    ├── grafana-service.yaml           ← ClusterIP on port 3000
    ├── grafana-config.yaml            ← ConfigMap: datasources (Prometheus + Loki) + dashboard
    ├── jaeger-deployment.yaml         ← All-in-one, OTLP on 4317, UI on 16686
    ├── jaeger-service.yaml            ← ClusterIP on port 16686
    ├── loki-deployment.yaml           ← StatefulSet with 1GB PVC
    ├── loki-service.yaml              ← ClusterIP on port 3100
    ├── loki-config.yaml               ← ConfigMap: same config as docker/loki/loki.yml
    ├── alloy-daemonset.yaml           ← DaemonSet: scrapes container logs on each node
    └── alloy-config.yaml              ← ConfigMap: same config as docker/alloy/alloy.river
```

**36 files total.** Every file is explained below.

---

## Prerequisites

- **k3d** — `brew install k3d` (or download from https://k3d.io)
- **kubectl** — `brew install kubectl`
- **Docker Desktop** — required by k3d (runs K3s inside Docker containers)

---

## Quick Start

### 1. Create the k3d cluster

```bash
k3d cluster create url-shortener \
  --port 8080:80@loadbalancer \
  --port 3000:3000@loadbalancer \
  --port 16686:16686@loadbalancer \
  --agents 2
```

This creates a 3-node cluster (1 server + 2 agents) with port mappings:
- `localhost:8080` → the API Gateway (the app itself)
- `localhost:3000` → Grafana
- `localhost:16686` → Jaeger UI

### 2. Build and import Docker images

```bash
# Build each service using its Dockerfile
docker build -t url-shortener/shortener-service ../shortener-service
docker build -t url-shortener/redirect-service ../redirect-service
docker build -t url-shortener/analytics-service ../analytics-service
docker build -t url-shortener/api-gateway ../api-gateway

# Import into k3d (so Kubernetes can pull them locally)
k3d image import url-shortener/shortener-service -c url-shortener
k3d image import url-shortener/redirect-service -c url-shortener
k3d image import url-shortener/analytics-service -c url-shortener
k3d image import url-shortener/api-gateway -c url-shortener
```

### 3. Deploy everything

```bash
kubectl apply -k k8s/
```

This creates the namespace and all resources inside it.

### 4. Wait for all pods to be ready

```bash
kubectl get pods -n url-shortener -w
```

When all pods show `Running` and `READY` is `1/1` (or `2/2` for replicas), the system is up.

### 5. Verify

```bash
# Create a short URL
curl -s -X POST http://localhost:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com"}'

# Follow the redirect
SHORT_CODE=$(curl -s -X POST http://localhost:8080/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://example.com"}' | jq -r '.shortUrl' | grep -oE '[^/]+$')
curl -s -o /dev/null -w "%{redirect_url}" http://localhost:8080/$SHORT_CODE

# Query analytics
curl -s http://localhost:8080/api/v1/analytics/$SHORT_CODE
```

### 6. Open the dashboards

| Tool | URL | Credentials |
|------|-----|-------------|
| **API Gateway** | http://localhost:8080 | None |
| **Grafana** | http://localhost:3000 | admin / admin |
| **Jaeger** | http://localhost:16686 | None |

---

## Step-by-Step Explanation

### 1. Namespace

**File: `namespace.yaml`**

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: url-shortener
```

All resources (pods, services, configs, volumes) are created inside the `url-shortener` namespace. This keeps them isolated from other projects running on the same k3d cluster. Every subsequent YAML file references this namespace via `namespace: url-shortener`.

**Why a namespace:** You might have other things running in the cluster (Helm tools, monitoring operators, etc.). Without a namespace, resource names could conflict and cleanup is harder. When you want to delete everything: `kubectl delete namespace url-shortener`.

---

### 2. ConfigMap and Secret

These two files hold all the shared configuration that every service needs.

**File: `config/env-config.yaml`**

This is a **ConfigMap** — Kubernetes's way of injecting configuration into pods. It stores **non-sensitive** key-value pairs. Every deployment in the `apps/` folder references this ConfigMap via `envFrom` or `valueFrom.configMapKeyRef`.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: env-config
  namespace: url-shortener
data:
  # Base URL for short links (used when generating short URLs)
  APP_BASE_URL: "http://localhost:8080"

  # Database connection string
  SPRING_DATASOURCE_SHORTENER_URL: "jdbc:postgresql://postgres:5432/url_shortener?currentSchema=short_url"
  SPRING_DATASOURCE_ANALYTICS_URL: "jdbc:postgresql://postgres:5432/url_shortener?currentSchema=analytics"

  # Redis connection details
  SPRING_DATA_REDIS_HOST: "redis"
  SPRING_DATA_REDIS_PORT: "6379"

  # Kafka connection
  KAFKA_BOOTSTRAP: "kafka:9092"

  # OpenTelemetry tracing endpoint (sends traces to Jaeger)
  OTEL_EXPORTER_OTLP_ENDPOINT: "http://jaeger:4317"

  # CORS (allow all origins for development)
  CORS_ALLOWED_ORIGINS: "*"

  # GeoIP database paths (bundled in the JAR, using classpath)
  GEOIP_CITY_DB_PATH: "classpath:geoip/GeoLite2-City.mmdb"
  GEOIP_ASN_DB_PATH: "classpath:geoip/GeoLite2-ASN.mmdb"
```

**File: `config/env-secret.yaml`**

This is a **Secret** — same concept as ConfigMap but the values are base64-encoded (Kubernetes treats them as sensitive). In a real production deployment, you'd use a tool like Mozilla SOPS or Sealed Secrets or an external secrets manager (AWS Secrets Manager, Vault). For local k3d development, storing them in a YAML file is fine — just don't commit the actual values to git. Use `.gitignore`.

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: env-secret
  namespace: url-shortener
type: Opaque
data:
  # base64 encoded values (echo -n "postgres" | base64)
  DB_USERNAME: cG9zdGdyZXM=
  DB_PASSWORD: cG9zdGdyZXM=

  # Cookie encryption — used by redirect-service for "Remember Me" cookies
  COOKIE_ENCRYPTION_PASSWORD: Y2hhbmdlaXQ=
  COOKIE_ENCRYPTION_SALT: ZGVhZGJlZWY=
```

The base64 values shown above decode to:
- `DB_USERNAME`: `postgres`
- `DB_PASSWORD`: `postgres`
- `COOKIE_ENCRYPTION_PASSWORD`: `changeit`
- `COOKIE_ENCRYPTION_SALT`: `deadbeef`

**How deployments use them:**

```yaml
envFrom:
  - configMapRef:
      name: env-config
  - secretRef:
      name: env-secret
```

This injects all ConfigMap keys as environment variables into the pod. Each service's `application.yaml` reads these via `${VAR_NAME:default}` syntax, so the defaults in the JAR files work when no env var is set.

---

### 3. Infrastructure

These are the **stateful** components — they need persistent storage and stable network identities (hence StatefulSets, not Deployments).

#### PostgreSQL

**File: `infrastructure/postgres-pvc.yaml`**

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: url-shortener
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

A PersistentVolumeClaim asks Kubernetes for 1GB of storage. k3d's default storage provisioner (Rancher Local Path Provisioner) will create a local directory on the host machine to satisfy this claim. The volume persists across pod restarts but not across cluster deletion.

**File: `infrastructure/postgres-statefulset.yaml`**

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: url-shortener
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:17.5-alpine3.22
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: "url_shortener"
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: env-secret
                  key: DB_USERNAME
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: env-secret
                  key: DB_PASSWORD
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "postgres"]
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "postgres"]
            initialDelaySeconds: 5
            periodSeconds: 5
          resources:
            requests:
              memory: "256Mi"
              cpu: "0.25"
            limits:
              memory: "1Gi"
              cpu: "0.5"
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```

Key points:
- **StatefulSet** (not Deployment) because we need stable pod naming (`postgres-0`) and stable storage via `volumeClaimTemplates`
- **`volumeClaimTemplates`** automatically creates a PVC for each replica with the name `data-<statefulset-name>-<ordinal>` (e.g., `data-postgres-0`)
- **Liveness probe** checks if Postgres is alive via `pg_isready`; if it fails, k3d restarts the container
- **Readiness probe** waits for Postgres to be ready before sending traffic
- **Resource limits** prevent Postgres from consuming all node memory
- Password comes from the `env-secret` Secret

**File: `infrastructure/postgres-service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: url-shortener
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
```

A simple ClusterIP service. Other pods can reach Postgres at `postgres:5432`. No external access needed — apps communicate internally.

#### Redis

**File: `infrastructure/redis-statefulset.yaml`**

Redis Stack Server bundles Redis with the RedisBloom module (needed for Bloom filters). It uses a smaller PVC (256MB) because Redis primarily works in memory.

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: url-shortener
spec:
  serviceName: redis
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: redis/redis-stack-server:7.4.0-v8
          ports:
            - containerPort: 6379
          args:
            - "--appendonly"
            - "yes"
          volumeMounts:
            - name: data
              mountPath: /data
          livenessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 10
            periodSeconds: 10
          readinessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 5
            periodSeconds: 5
          resources:
            requests:
              memory: "128Mi"
              cpu: "0.1"
            limits:
              memory: "512Mi"
              cpu: "0.5"
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 256Mi
```

Key change from Docker Compose: `--appendonly yes` is set here (whereas the Docker Compose had `--appendonly no`). In Kubernetes, we want the ID counter to survive pod restarts. The PVC ensures the AOF file is persisted on disk.

**File: `infrastructure/redis-service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: url-shortener
spec:
  selector:
    app: redis
  ports:
    - port: 6379
      targetPort: 6379
```

#### Kafka

**File: `infrastructure/kafka-statefulset.yaml`**

Kafka runs in **KRaft mode** (no ZooKeeper). This is simpler to deploy and matches the Docker Compose setup. Single broker is fine for development.

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  namespace: url-shortener
spec:
  serviceName: kafka
  replicas: 1
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
        - name: kafka
          image: apache/kafka:4.1.1
          ports:
            - containerPort: 9092
          env:
            - name: KAFKA_NODE_ID
              value: "1"
            - name: KAFKA_PROCESS_ROLES
              value: "broker,controller"
            - name: KAFKA_LISTENERS
              value: "PLAINTEXT://:9092,CONTROLLER://:9093"
            - name: KAFKA_ADVERTISED_LISTENERS
              value: "PLAINTEXT://kafka:9092"
            - name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
              value: "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
            - name: KAFKA_INTER_BROKER_LISTENER_NAME
              value: "PLAINTEXT"
            - name: KAFKA_CONTROLLER_LISTENER_NAMES
              value: "CONTROLLER"
            - name: KAFKA_CONTROLLER_QUORUM_VOTERS
              value: "1@kafka:9093"
            - name: KAFKA_CLUSTER_ID
              value: "MkU3OEVBNTcwNTJENDM2Qk"
            - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
              value: "1"
            - name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR
              value: "1"
            - name: KAFKA_TRANSACTION_STATE_LOG_MIN_ISR
              value: "1"
            - name: KAFKA_DEFAULT_REPLICATION_FACTOR
              value: "1"
            - name: KAFKA_MIN_INSYNC_REPLICAS
              value: "1"
          volumeMounts:
            - name: data
              mountPath: /var/lib/kafka/data
          livenessProbe:
            exec:
              command: ["/opt/kafka/bin/kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
            initialDelaySeconds: 30
            periodSeconds: 15
          readinessProbe:
            exec:
              command: ["/opt/kafka/bin/kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
            initialDelaySeconds: 15
            periodSeconds: 10
          resources:
            requests:
              memory: "512Mi"
              cpu: "0.25"
            limits:
              memory: "1Gi"
              cpu: "0.5"
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 2Gi
```

The KRaft environment variables are identical to the Docker Compose config. The key change is `KAFKA_ADVERTISED_LISTENERS` using the service name `kafka:9092` instead of `localhost:9092` — this ensures clients inside Kubernetes can reach Kafka.

**File: `infrastructure/kafka-service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: kafka
  namespace: url-shortener
spec:
  selector:
    app: kafka
  ports:
    - port: 9092
      targetPort: 9092
```

---

### 4. Application Services

All four app deployments follow the same pattern. Here's a detailed walkthrough of the **shortener-service**; the others follow the same structure with different ports and replicas.

#### Shortener Service

**File: `apps/shortener-deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: shortener-service
  namespace: url-shortener
spec:
  replicas: 2
  selector:
    matchLabels:
      app: shortener-service
  template:
    metadata:
      labels:
        app: shortener-service
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/api/v1/actuator/prometheus"
        prometheus.io/port: "8081"
    spec:
      containers:
        - name: shortener-service
          image: url-shortener/shortener-service
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8081
          envFrom:
            - configMapRef:
                name: env-config
            - secretRef:
                name: env-secret
          env:
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                configMapKeyRef:
                  name: env-config
                  key: SPRING_DATASOURCE_SHORTENER_URL
          livenessProbe:
            httpGet:
              path: /api/v1/actuator/health/liveness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /api/v1/actuator/health/readiness
              port: 8081
            initialDelaySeconds: 15
            periodSeconds: 5
          resources:
            requests:
              memory: "256Mi"
              cpu: "0.25"
            limits:
              memory: "512Mi"
              cpu: "0.5"
```

Key details:
- **`imagePullPolicy: IfNotPresent`** — tells Kubernetes to use the locally imported image (pulled via `k3d image import`) instead of trying to pull from Docker Hub
- **`envFrom` with both ConfigMap and Secret** — injects all shared env vars. The `SPRING_DATASOURCE_URL` is overridden specifically because shortener and analytics use different schemas
- **Liveness probe at `/actuator/health/liveness`** — if the service becomes unresponsive, k3d restarts the pod
- **Readiness probe at `/actuator/health/readiness`** — if the service isn't ready (e.g., still connecting to Postgres), k3d stops sending traffic to it
- **Prometheus annotations** — Prometheus auto-discovers pods with `prometheus.io/scrape: "true"` and scrapes metrics from the specified path and port

**File: `apps/shortener-service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: shortener-service
  namespace: url-shortener
spec:
  selector:
    app: shortener-service
  ports:
    - port: 8081
      targetPort: 8081
```

#### Redirect Service

**File: `apps/redirect-deployment.yaml`**

Same structure as shortener. Notable differences:
- `replicas: 2`
- `containerPort: 8082`
- `image: url-shortener/redirect-service`
- Uses `SPRING_DATASOURCE_URL` from `SPRING_DATASOURCE_SHORTENER_URL` (same schema)
- Liveness/readiness on port 8082

**File: `apps/redirect-service.yaml`** — ClusterIP on port 8082.

#### Analytics Service

**File: `apps/analytics-deployment.yaml`**

Notable differences:
- `replicas: 1` — Kafka consumer concurrency is internal (3 listener threads in one pod)
- `containerPort: 8083`
- Uses `SPRING_DATASOURCE_URL` from `SPRING_DATASOURCE_ANALYTICS_URL` (different schema)

**File: `apps/analytics-service.yaml`** — ClusterIP on port 8083.

#### API Gateway

**File: `apps/gateway-deployment.yaml`**

The gateway is the **only public-facing service**. It needs environment variables pointing to the internal service URLs:

```yaml
env:
  - name: SHORTENER_SERVICE_URL
    value: "http://shortener-service:8081"
  - name: REDIRECT_SERVICE_URL
    value: "http://redirect-service:8082"
  - name: ANALYTICS_SERVICE_URL
    value: "http://analytics-service:8083"
envFrom:
  - configMapRef:
      name: env-config
```

These `SHORTENER_SERVICE_URL`, `REDIRECT_SERVICE_URL`, `ANALYTICS_SERVICE_URL` env vars override the defaults in the Gateway's `application.yaml`, making it route to the correct Kubernetes service DNS names.

**File: `apps/gateway-service.yaml`** — ClusterIP on port 8080.

#### Why ClusterIP services?

All four app services are `type: ClusterIP` — they're only accessible from within the cluster. The only way traffic from the outside world reaches them is through the **Ingress**.

---

### 5. PodDisruptionBudgets

**Files: `apps/shortener-pdb.yaml`, `apps/redirect-pdb.yaml`, `apps/gateway-pdb.yaml`**

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: shortener-pdb
  namespace: url-shortener
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: shortener
```

A PodDisruptionBudget (PDB) is an insurance policy for voluntary disruptions — situations where Kubernetes intentionally stops a pod, like draining a node for maintenance, upgrading the cluster, or defragmenting resources. Without a PDB, Kubernetes could evict every replica of your service at once, causing downtime.

These PDBs guarantee that at least 1 pod is always running for each of the 2-replica services (shortener, redirect, gateway). Kubernetes will wait for new pods to become ready before evicting old ones during any voluntary disruption.

**Why not for analytics (1 replica) or infrastructure (Postgres, Redis, Kafka)?**
- A PDB with `minAvailable: 1` on a single-replica pod would block all voluntary evictions entirely — Kubernetes could never touch that pod. That's correct for a production database with automated failover, but for local development it's unnecessary rigidity. For a single-replica analytics service, it's also overkill.

---

### 6. Ingress

**File: `apps/ingress.yaml`**

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-gateway-ingress
  namespace: url-shortener
  annotations:
    ingress.kubernetes.io/ssl-redirect: "false"
spec:
  ingressClassName: traefik
  rules:
    - http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
```

In k3d, the default ingress controller is **Traefik**. When you create this Ingress, Traefik configures itself to route all traffic from the k3d loadbalancer (`localhost:8080`) to the `api-gateway` service on port 8080.

The annotation `ingress.kubernetes.io/ssl-redirect: "false"` prevents Traefik from automatically redirecting HTTP to HTTPS (no TLS for local development).

**How the port mapping works:**

```
Your browser                          k3d LoadBalancer              Ingress               Service               Pod
  localhost:8080/abc123  ──────▶  k3d-proxy :80(←:8080)  ──▶  traefik reads Ingress  ──▶  api-gateway:8080  ──▶  gateway-pod:8080
```

The `k3d cluster create --port 8080:80@loadbalancer` maps your local port 8080 to port 80 on the k3d loadbalancer container. Traefik listens on port 80, reads the Ingress rules, and forwards matching requests to the service.

---

### 7. Monitoring

All monitoring components are optional — you can run the app without them. But they provide critical visibility into how the system behaves.

#### Prometheus (Metrics)

**File: `monitoring/prometheus-config.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: url-shortener
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    scrape_configs:
      - job_name: 'shortener-service'
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: shortener-service
            action: keep
          - source_labels: [__address__]
            action: replace
            target_label: __address__
            replacement: shortener-service:8081
        metrics_path: /api/v1/actuator/prometheus

      - job_name: 'redirect-service'
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: redirect-service
            action: keep
          - source_labels: [__address__]
            action: replace
            target_label: __address__
            replacement: redirect-service:8082
        metrics_path: /api/v1/actuator/prometheus

      - job_name: 'analytics-service'
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: analytics-service
            action: keep
          - source_labels: [__address__]
            action: replace
            target_label: __address__
            replacement: analytics-service:8083
        metrics_path: /api/v1/actuator/prometheus

      - job_name: 'api-gateway'
        kubernetes_sd_configs:
          - role: pod
        relabel_configs:
          - source_labels: [__meta_kubernetes_pod_label_app]
            regex: api-gateway
            action: keep
          - source_labels: [__address__]
            action: replace
            target_label: __address__
            replacement: api-gateway:8080
        metrics_path: /api/v1/actuator/prometheus
```

This ConfigMap tells Prometheus which endpoints to scrape. Prometheus auto-discovers pods with the matching labels and scrapes metrics from the Spring Boot Actuator Prometheus endpoint.

**File: `monitoring/prometheus-deployment.yaml`**

A standard Deployment with the Prometheus image, mounting the ConfigMap and a PVC for data persistence. Exposes port 9090.

**File: `monitoring/prometheus-service.yaml`** — ClusterIP on port 9090.

#### Grafana (Visualization)

**File: `monitoring/grafana-config.yaml`**

This ConfigMap auto-provisions datasources and dashboards so Grafana is ready to use immediately after deployment. It contains:

1. **Prometheus datasource** — connects to `http://prometheus:9090`
2. **Loki datasource** — connects to `http://loki:3100`
3. **Pre-built dashboard** — the same URL Shortener dashboard from `docker/grafana/provisioning/dashboards/`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-config
  namespace: url-shortener
data:
  datasources.yaml: |
    apiVersion: 1
    datasources:
      - name: Prometheus
        type: prometheus
        access: proxy
        url: http://prometheus:9090
        isDefault: true
      - name: Loki
        type: loki
        access: proxy
        url: http://loki:3100

  url-shortener-dashboard.json: |
    { ... dashboard JSON from docker/grafana/provisioning/dashboards/url-shortener.json ... }
```

**File: `monitoring/grafana-deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: url-shortener
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
        - name: grafana
          image: grafana/grafana:12.0
          ports:
            - containerPort: 3000
          env:
            - name: GF_SECURITY_ADMIN_USER
              value: "admin"
            - name: GF_SECURITY_ADMIN_PASSWORD
              value: "admin"
            - name: GF_INSTALL_PLUGINS
              value: "grafana-piechart-panel"
          volumeMounts:
            - name: config
              mountPath: /etc/grafana/provisioning
      volumes:
        - name: config
          configMap:
            name: grafana-config
```

**File: `monitoring/grafana-service.yaml`** — ClusterIP on port 3000.

#### Jaeger (Distributed Tracing)

**File: `monitoring/jaeger-deployment.yaml`**

All-in-one Jaeger (collector + query + UI in one container). This matches what the Docker Compose setup uses. Services already emit OpenTelemetry traces to `http://jaeger:4317`.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: url-shortener
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
        - name: jaeger
          image: jaegertracing/all-in-one:latest
          ports:
            - containerPort: 16686   # UI
            - containerPort: 4317    # OTLP gRPC
            - containerPort: 4318    # OTLP HTTP
          env:
            - name: COLLECTOR_OTLP_ENABLED
              value: "true"
```

**File: `monitoring/jaeger-service.yaml`** — ClusterIP on port 16686 (UI) + port 4317 (OTLP).

#### Loki (Log Storage)

**File: `monitoring/loki-config.yaml`**

Same Loki configuration as `docker/loki/loki.yml`. Loki stores logs and indexes them for fast searching. Uses TSDB index and filesystem storage.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: loki-config
  namespace: url-shortener
data:
  loki.yml: |
    auth_enabled: false
    server:
      http_listen_port: 3100
    ingester:
      lifecycler:
        ring:
          kvstore:
            store: inmemory
          replication_factor: 1
    schema_config:
      configs:
        - from: 2024-01-01
          store: tsdb
          object_store: filesystem
          schema: v13
          index:
            prefix: index_
            period: 24h
    storage_config:
      filesystem:
        directory: /tmp/loki
```

**File: `monitoring/loki-deployment.yaml`** — StatefulSet with 1GB PVC mounted at `/tmp/loki`.

**File: `monitoring/loki-service.yaml`** — ClusterIP on port 3100.

#### Alloy (Log Scraper)

**File: `monitoring/alloy-config.yaml`**

Alloy is the replacement for Promtail. It runs as a DaemonSet (one pod per k3d agent node) and scrapes Docker container logs from the Docker socket.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: alloy-config
  namespace: url-shortener
data:
  config.river: |
    // Discover all Docker containers and scrape their logs
    docker.discover "all" {
      host = "unix:///var/run/docker.sock"
    }

    docker.scrape "all" {
      targets    = docker.discover.all.targets
      forward_to = [loki.write.default.receiver]
    }

    // Forward all logs to Loki
    loki.write "default" {
      endpoint {
        url = "http://loki:3100/loki/api/v1/push"
      }
    }
```

**File: `monitoring/alloy-daemonset.yaml`**

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: alloy
  namespace: url-shortener
spec:
  selector:
    matchLabels:
      app: alloy
  template:
    metadata:
      labels:
        app: alloy
    spec:
      containers:
        - name: alloy
          image: grafana/alloy:latest
          args:
            - "run"
            - "--server.http.listen-addr=0.0.0.0:12345"
            - "/etc/alloy/config.river"
          volumeMounts:
            - name: config
              mountPath: /etc/alloy
            - name: docker-socket
              mountPath: /var/run/docker.sock
          ports:
            - containerPort: 12345
      volumes:
        - name: config
          configMap:
            name: alloy-config
        - name: docker-socket
          hostPath:
            path: /var/run/docker.sock
            type: Socket
```

Key details:
- **DaemonSet** — ensures one Alloy pod runs on every k3d node (server + agents)
- **Docker socket mounted as `hostPath`** — allows Alloy to read the Docker API to discover all containers and their logs
- In a real Kubernetes cluster (EKS, AKS, GKE), you'd replace the Docker socket with reading log files from `/var/log/pods/*` — but for k3d (which runs K3s inside Docker containers), the Docker socket approach is the simplest

---

### Kustomization

**File: `kustomization.yaml`**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: url-shortener

resources:
  - namespace.yaml
  - config/env-config.yaml
  - config/env-secret.yaml
  - infrastructure/postgres-pvc.yaml
  - infrastructure/postgres-statefulset.yaml
  - infrastructure/postgres-service.yaml
  - infrastructure/redis-statefulset.yaml
  - infrastructure/redis-service.yaml
  - infrastructure/kafka-statefulset.yaml
  - infrastructure/kafka-service.yaml
  - apps/shortener-deployment.yaml
  - apps/shortener-service.yaml
  - apps/redirect-deployment.yaml
  - apps/redirect-service.yaml
  - apps/analytics-deployment.yaml
  - apps/analytics-service.yaml
  - apps/gateway-deployment.yaml
  - apps/gateway-service.yaml
  - apps/ingress.yaml
  - apps/shortener-pdb.yaml
  - apps/redirect-pdb.yaml
  - apps/gateway-pdb.yaml
  - monitoring/prometheus-config.yaml
  - monitoring/prometheus-deployment.yaml
  - monitoring/prometheus-service.yaml
  - monitoring/grafana-config.yaml
  - monitoring/grafana-deployment.yaml
  - monitoring/grafana-service.yaml
  - monitoring/jaeger-deployment.yaml
  - monitoring/jaeger-service.yaml
  - monitoring/loki-config.yaml
  - monitoring/loki-deployment.yaml
  - monitoring/loki-service.yaml
  - monitoring/alloy-config.yaml
  - monitoring/alloy-daemonset.yaml
```

Kustomize applies all listed resources in order. When you run `kubectl apply -k k8s/`, it:
1. Reads this file
2. Compiles all resources (adding the `namespace` field to everything automatically)
3. Applies them to the cluster in dependency order (namespace first, then infrastructure, then apps, then monitoring)

---

## Useful Commands

### Watch pod status

```bash
kubectl get pods -n url-shortener -w
```

### View logs for a specific service

```bash
kubectl logs -n url-shortener -l app=redirect-service --tail=100 -f
```

### Port-forward to a service (alternative to Ingress)

```bash
kubectl port-forward -n url-shortener svc/api-gateway 8080:8080
kubectl port-forward -n url-shortener svc/grafana 3000:3000
kubectl port-forward -n url-shortener svc/jaeger 16686:16686
```

### Scale a deployment

```bash
kubectl scale deployment -n url-shortener redirect-service --replicas=5
```

### Restart a single pod (without downtime)

```bash
kubectl rollout restart -n url-shortener deployment/shortener-service
```

### Describe a pod (debug startup issues)

```bash
kubectl describe pod -n url-shortener shortener-service-<pod-id>
```

### Get all resources in the namespace

```bash
kubectl get all -n url-shortener
```

### Delete everything

```bash
kubectl delete namespace url-shortener
```

Or just delete the cluster entirely:

```bash
k3d cluster delete url-shortener
```

### Check PVC status

```bash
kubectl get pvc -n url-shortener
kubectl get pv
```

---

## Troubleshooting

### Pod stuck in `Pending`

Most common cause: PVC not binding. k3d's local-path provisioner creates volumes on-demand, but it might need a moment.

```bash
kubectl describe pvc -n url-shortener
kubectl describe pod -n url-shortener <pod-name>
```

### Pod stuck in `CrashLoopBackOff`

```bash
kubectl logs -n url-shortener <pod-name>
```

Common causes:
- **Database not ready yet** — Postgres takes 10-20 seconds to start. The app's readiness probe will keep it from receiving traffic until Postgres is up. Give it 30 seconds.
- **Wrong database URL** — Check `SPRING_DATASOURCE_URL` in `env-config.yaml`
- **Kafka not ready** — Analytics service depends on Kafka. Kafka takes 20-30 seconds to start.

### `ImagePullBackOff`

```bash
kubectl describe pod -n url-shortener <pod-name>
```

If the image name doesn't match the `k3d image import` name, Kubernetes can't find it locally. Make sure you used the exact same name in `docker build` and `k3d image import`.

### Can't reach the app at localhost:8080

```bash
# Check that the Ingress is configured
kubectl get ingress -n url-shortener

# Check that the gateway service is up
kubectl get svc -n url-shortener api-gateway

# Check the k3d port mapping
k3d cluster list
```

The port mapping is set at cluster creation time. If you didn't include `--port 8080:80@loadbalancer`, you'll need to port-forward instead:

```bash
kubectl port-forward -n url-shortener svc/api-gateway 8080:8080
```

### Ingress not working (Traefik)

k3d uses Traefik as the default ingress controller. Verify it's running:

```bash
kubectl get pods -n kube-system | grep traefik
```

If Traefik isn't running, the Ingress won't work. Use `kubectl port-forward` as a fallback (see above).

### No metrics in Prometheus

```bash
# Check Prometheus targets
kubectl port-forward -n url-shortener svc/prometheus 9090:9090
# Open http://localhost:9090/targets in your browser
```

If targets show as "down", check that the services have the correct Prometheus annotations and that `prometheus-config.yaml` has the correct service names.

### No traces in Jaeger

```bash
# Check that Jaeger is receiving traces
kubectl port-forward -n url-shortener svc/jaeger 16686:16686
# Open http://localhost:16686 in your browser
# Create a short URL and visit it, then search for the service name "shortener-service" or "redirect-service"
```

If no traces appear, check that `OTEL_EXPORTER_OTLP_ENDPOINT` is set to `http://jaeger:4317` in `env-config.yaml`.

### Kafka producer/consumer errors

```bash
# Check Kafka logs
kubectl logs -n url-shortener kafka-0

# Check the analytics service logs
kubectl logs -n url-shortener -l app=analytics-service --tail=50
```

Common issue: Kafka advertised listeners. Ensure `KAFKA_ADVERTISED_LISTENERS` is `PLAINTEXT://kafka:9092` (the service name, not `localhost`).

---

## Production vs. k3d Differences

This setup is designed for **local development with k3d**. For a production Kubernetes cluster (EKS, AKS, GKE), you'd want:

| Aspect | k3d (current) | Production |
|--------|---------------|------------|
| **Storage** | Local path provisioner (ephemeral) | EBS, EFS, or CSI-based persistent volumes with backup |
| **Ingress** | Traefik (k3d default) | ALB (AWS), Ingress NGINX, or Gateway API |
| **Secrets** | Plain YAML (base64) | External Secrets Operator + AWS Secrets Manager / HashiCorp Vault |
| **Monitoring** | Simple single-replica deployments | Prometheus Operator, Grafana Operator, Loki microservices mode |
| **Images** | Locally built + imported | Container registry (ECR, Docker Hub, GHCR) with CI/CD |
| **TLS** | None (HTTP only) | cert-manager + Let's Encrypt |
| **High Availability** | Single replicas for infra | Multi-replica Postgres with Patroni, Kafka with 3+ brokers |
| **Service mesh** | None | Istio or Linkerd for mTLS, traffic splitting, observability |

---

## What's Not Included (And Why)

| Feature | Reason |
|---------|--------|
| **Horizontal Pod Autoscaler (HPA)** | Overkill for local dev; add when load testing |
| **Network Policies** | Useful for security but complex in local dev |
| **Init Containers** | The current startup ordering (Postgres → apps) works fine with readiness probes |
