# Docker Quick Start Guide

## TL;DR - Get Running in 2 Minutes

```bash
# 1. Build
docker build -t cron-query-service:latest .

# 2. Run
docker run -d --name cron-query-service -p 8080:8080 cron-query-service:latest

# 3. Test
curl http://localhost:8080/actuator/health
curl "http://localhost:8080/api/jobs?query=all+jobs"

# 4. View
open http://localhost:8080/swagger-ui.html
```

## Automated Testing

**Linux/Mac:**
```bash
./test-docker.sh
```

**Windows:**
```powershell
.\test-docker.ps1
```

## Common Commands

### Build & Run
```bash
# Build image
docker build -t cron-query-service:latest .

# Run container
docker run -d --name cron-query-service -p 8080:8080 cron-query-service:latest

# Run with custom crontab
docker run -d --name cron-query-service -p 8080:8080 \
  -v "$(pwd)/../../../test_crontab.txt:/app/test_crontab.txt:ro" \
  cron-query-service:latest
```

### Docker Compose
```bash
# Start
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

### Monitoring
```bash
# View logs
docker logs -f cron-query-service

# Check health
docker ps
curl http://localhost:8080/actuator/health

# Resource usage
docker stats cron-query-service
```

### Cleanup
```bash
# Stop and remove
docker stop cron-query-service
docker rm cron-query-service

# Remove image
docker rmi cron-query-service:latest
```

## Quick Tests

### Health Check
```bash
curl http://localhost:8080/actuator/health | jq '.'
```

### Query Jobs
```bash
# Natural language
curl "http://localhost:8080/api/jobs?query=jobs+on+weekdays"

# Structured
curl "http://localhost:8080/api/jobs?time=07:00"

# CSV format
curl "http://localhost:8080/api/jobs?query=all+jobs&format=csv"
```

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### Metrics
```bash
curl http://localhost:8080/actuator/prometheus | grep cronquery
```

## Troubleshooting

### Container won't start
```bash
docker logs cron-query-service
```

### Port already in use
```bash
docker run -d --name cron-query-service -p 8081:8080 cron-query-service:latest
```

### Health check fails
```bash
docker exec cron-query-service curl http://localhost:8080/actuator/health
```

### View crontab file
```bash
docker exec cron-query-service cat /app/test_crontab.txt
```

## Environment Variables

```bash
docker run -d --name cron-query-service -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e LOGGING_LEVEL_COM_CRONQUERY=DEBUG \
  cron-query-service:latest
```

## Container Registries

### Understanding Image Storage

When you build an image, it's stored **locally** on your machine. To share or deploy it elsewhere, you need to push it to a **container registry**.

**View your local images:**
```bash
docker images
```

### Docker Hub (Public Registry)

Docker Hub is the default public registry (like GitHub for Docker images).

**1. Create account:** https://hub.docker.com

**2. Login:**
```bash
docker login
# Enter your Docker Hub username and password
```

**3. Tag your image:**
```bash
# Format: docker tag local-image:tag username/repository:tag
docker tag cron-query-service:latest yourusername/cron-query-service:1.2.2
docker tag cron-query-service:latest yourusername/cron-query-service:latest
```

**4. Push to Docker Hub:**
```bash
docker push yourusername/cron-query-service:1.2.2
docker push yourusername/cron-query-service:latest
```

**5. Pull from anywhere:**
```bash
# Now anyone can pull your image
docker pull yourusername/cron-query-service:latest
docker run -d -p 8080:8080 yourusername/cron-query-service:latest
```

### Private Registries

For production, you typically use private registries:

**AWS ECR (Elastic Container Registry):**
```bash
# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

# Tag and push
docker tag cron-query-service:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/cron-query-service:latest
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/cron-query-service:latest
```

**Azure ACR (Azure Container Registry):**
```bash
# Login to ACR
az acr login --name myregistry

# Tag and push
docker tag cron-query-service:latest myregistry.azurecr.io/cron-query-service:latest
docker push myregistry.azurecr.io/cron-query-service:latest
```

**Google GCR (Google Container Registry):**
```bash
# Configure Docker to use gcloud
gcloud auth configure-docker

# Tag and push
docker tag cron-query-service:latest gcr.io/my-project/cron-query-service:latest
docker push gcr.io/my-project/cron-query-service:latest
```

### Save/Load Images (Manual Transfer)

For air-gapped environments or manual transfer:

**Save image to file:**
```bash
docker save cron-query-service:latest -o cron-query-service.tar
# Creates a ~685MB tar file
```

**Load image from file:**
```bash
# On another machine
docker load -i cron-query-service.tar
docker images  # Verify it's loaded
```

**Compress for smaller transfer:**
```bash
docker save cron-query-service:latest | gzip > cron-query-service.tar.gz
# Load compressed
gunzip -c cron-query-service.tar.gz | docker load
```

## Kubernetes Basics

Kubernetes (K8s) orchestrates containers across multiple machines. Here's a simple example to get started.

### Prerequisites

**Install kubectl (Kubernetes CLI):**
```bash
# Windows (with Chocolatey)
choco install kubernetes-cli

# Mac
brew install kubectl

# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
```

**Local Kubernetes Options:**
- **Docker Desktop**: Enable Kubernetes in settings (easiest for learning)
- **Minikube**: Lightweight local cluster
- **Kind**: Kubernetes in Docker

### Enable Kubernetes in Docker Desktop

1. Open Docker Desktop
2. Go to Settings → Kubernetes
3. Check "Enable Kubernetes"
4. Click "Apply & Restart"
5. Wait for Kubernetes to start (green indicator)

**Verify:**
```bash
kubectl version --client
kubectl cluster-info
```

### Basic Kubernetes Deployment

Create `kubernetes/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cron-query-service
  labels:
    app: cron-query-service
spec:
  replicas: 2  # Run 2 instances for learning about scaling
  selector:
    matchLabels:
      app: cron-query-service
  template:
    metadata:
      labels:
        app: cron-query-service
    spec:
      containers:
      - name: cron-query-service
        image: cron-query-service:latest
        imagePullPolicy: Never  # Use local image (for learning)
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SERVER_PORT
          value: "8080"
        - name: SPRING_PROFILES_ACTIVE
          value: "dev"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 45
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: cron-query-service
spec:
  type: LoadBalancer  # Exposes service externally
  selector:
    app: cron-query-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
```

### Deploy to Kubernetes

```bash
# Create deployment
kubectl apply -f kubernetes/deployment.yaml

# Check deployment status
kubectl get deployments
kubectl get pods
kubectl get services

# View pod logs
kubectl logs -l app=cron-query-service

# Describe a pod (detailed info)
kubectl describe pod <pod-name>
```

### Access the Service

```bash
# Get service URL (Docker Desktop)
kubectl get service cron-query-service

# Access the service
curl http://localhost/actuator/health
curl "http://localhost/api/jobs?query=jobs+on+weekdays"

# Or forward a port directly
kubectl port-forward service/cron-query-service 8080:80
# Then access: http://localhost:8080
```

### Kubernetes Operations

**Scale the deployment:**
```bash
# Scale to 3 replicas
kubectl scale deployment cron-query-service --replicas=3

# Watch pods being created
kubectl get pods -w
```

**Update the image:**
```bash
# After rebuilding your Docker image
kubectl rollout restart deployment/cron-query-service

# Check rollout status
kubectl rollout status deployment/cron-query-service
```

**View logs from all pods:**
```bash
kubectl logs -l app=cron-query-service --all-containers=true
```

**Execute commands in a pod:**
```bash
# Get a shell in a pod
kubectl exec -it <pod-name> -- /bin/bash

# Run a single command
kubectl exec <pod-name> -- curl http://localhost:8080/actuator/health
```

**Delete everything:**
```bash
kubectl delete -f kubernetes/deployment.yaml
```

### ConfigMap for Test Crontab

Create `kubernetes/configmap.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: cron-query-config
data:
  test_crontab.txt: |
    # Test crontab for Kubernetes
    0 8 * * 1-5 /usr/bin/morning-backup.sh
    30 12 * * * /usr/bin/lunch-reminder.sh
    0 0 * * 0 /usr/bin/weekly-report.sh
    0 */2 * * * /usr/bin/check-status.sh
```

Update deployment to use ConfigMap:

```yaml
# Add to deployment.yaml under spec.template.spec:
      volumes:
      - name: crontab-volume
        configMap:
          name: cron-query-config
      containers:
      - name: cron-query-service
        # ... existing config ...
        volumeMounts:
        - name: crontab-volume
          mountPath: /app/test_crontab.txt
          subPath: test_crontab.txt
```

Apply:
```bash
kubectl apply -f kubernetes/configmap.yaml
kubectl apply -f kubernetes/deployment.yaml
```

### Kubernetes Dashboard (Optional)

View your cluster in a web UI:

```bash
# Install dashboard
kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml

# Create admin user (for learning only!)
kubectl create serviceaccount dashboard-admin -n kubernetes-dashboard
kubectl create clusterrolebinding dashboard-admin --clusterrole=cluster-admin --serviceaccount=kubernetes-dashboard:dashboard-admin

# Get token
kubectl -n kubernetes-dashboard create token dashboard-admin

# Start proxy
kubectl proxy

# Access dashboard:
# http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/
# Use the token to login
```

### Learning Resources

**Key Concepts You've Learned:**
- **Deployment**: Manages replicas of your application
- **Service**: Exposes your application (LoadBalancer, NodePort, ClusterIP)
- **Pod**: Smallest unit (container instance)
- **ConfigMap**: Configuration data
- **Probes**: Health checks (liveness, readiness)
- **Scaling**: Running multiple replicas
- **Rolling updates**: Zero-downtime deployments

**Next Steps:**
- Learn about **Namespaces** (isolate resources)
- Explore **Ingress** (advanced routing)
- Try **Helm** (package manager for Kubernetes)
- Study **StatefulSets** (for stateful apps)
- Understand **Persistent Volumes** (storage)

## Full Documentation

- **Comprehensive Guide**: [DOCKER_TESTING.md](DOCKER_TESTING.md)
- **Validation Checklist**: [DOCKER_VALIDATION_CHECKLIST.md](DOCKER_VALIDATION_CHECKLIST.md)
- **Main README**: [README.md](README.md)

## Expected Results

- **Build Time**: 3-5 minutes (first build)
- **Image Size**: 685MB
- **Startup Time**: 30-40 seconds
- **Memory Usage**: 200-400MB
- **Health Status**: "healthy" after 40 seconds
