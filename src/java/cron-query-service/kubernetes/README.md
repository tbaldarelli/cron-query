# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the cron-query-service to a Kubernetes cluster.

## Prerequisites

1. **Kubernetes cluster** (one of):
   - Docker Desktop with Kubernetes enabled (easiest for learning)
   - Minikube
   - Kind (Kubernetes in Docker)
   - Cloud provider (GKE, EKS, AKS)

2. **kubectl** installed and configured

3. **Docker image** built locally:
   ```bash
   cd ..
   docker build -t cron-query-service:latest .
   ```

## Quick Start

### 1. Verify Kubernetes is Running

```bash
kubectl cluster-info
kubectl get nodes
```

### 2. Deploy to Kubernetes

```bash
# Apply ConfigMap (contains test crontab)
kubectl apply -f configmap.yaml

# Apply Deployment and Service
kubectl apply -f deployment.yaml

# Verify deployment
kubectl get all
```

### 3. Wait for Pods to be Ready

```bash
# Watch pods starting up
kubectl get pods -w

# Check pod status
kubectl get pods -l app=cron-query-service
```

Expected output:
```
NAME                                  READY   STATUS    RESTARTS   AGE
cron-query-service-xxxxxxxxxx-xxxxx   1/1     Running   0          1m
cron-query-service-xxxxxxxxxx-xxxxx   1/1     Running   0          1m
```

### 4. Access the Service

**Get service details:**
```bash
kubectl get service cron-query-service
```

**For Docker Desktop:**
```bash
# Service is available at localhost
curl http://localhost/actuator/health
curl "http://localhost/api/jobs?query=jobs+on+weekdays"

# Open Swagger UI
open http://localhost/swagger-ui.html
```

**For Minikube:**
```bash
# Get the service URL
minikube service cron-query-service --url

# Use the returned URL
curl $(minikube service cron-query-service --url)/actuator/health
```

**Port forwarding (works everywhere):**
```bash
kubectl port-forward service/cron-query-service 8080:80

# Then access at localhost:8080
curl http://localhost:8080/actuator/health
```

## Kubernetes Operations

### View Logs

```bash
# Logs from all pods
kubectl logs -l app=cron-query-service

# Logs from specific pod
kubectl logs <pod-name>

# Follow logs (tail -f)
kubectl logs -f <pod-name>

# Logs from all containers in all pods
kubectl logs -l app=cron-query-service --all-containers=true
```

### Scale the Deployment

```bash
# Scale to 3 replicas
kubectl scale deployment cron-query-service --replicas=3

# Scale to 1 replica
kubectl scale deployment cron-query-service --replicas=1

# Watch scaling happen
kubectl get pods -w
```

### Update the Application

After rebuilding your Docker image:

```bash
# Restart all pods with new image
kubectl rollout restart deployment/cron-query-service

# Check rollout status
kubectl rollout status deployment/cron-query-service

# View rollout history
kubectl rollout history deployment/cron-query-service
```

### Debug a Pod

```bash
# Get detailed pod information
kubectl describe pod <pod-name>

# Execute commands in a pod
kubectl exec <pod-name> -- curl http://localhost:8080/actuator/health

# Get a shell in a pod
kubectl exec -it <pod-name> -- /bin/bash

# View the mounted crontab file
kubectl exec <pod-name> -- cat /app/test_crontab.txt
```

### Check Resource Usage

```bash
# Pod resource usage
kubectl top pods -l app=cron-query-service

# Node resource usage
kubectl top nodes
```

### View Events

```bash
# All events in default namespace
kubectl get events --sort-by='.lastTimestamp'

# Events for specific pod
kubectl describe pod <pod-name> | grep -A 10 Events
```

## Update ConfigMap

To change the test crontab:

1. Edit `configmap.yaml`
2. Apply changes:
   ```bash
   kubectl apply -f configmap.yaml
   ```
3. Restart pods to pick up changes:
   ```bash
   kubectl rollout restart deployment/cron-query-service
   ```

## Cleanup

### Delete Everything

```bash
# Delete all resources (recommended)
kubectl delete -f kubernetes/

# Or delete individual files
kubectl delete -f deployment.yaml
kubectl delete -f configmap.yaml

# Verify deletion
kubectl get all
```

### Delete Specific Resources

```bash
# Delete just the deployment
kubectl delete deployment cron-query-service

# Delete just the service
kubectl delete service cron-query-service

# Delete just the configmap
kubectl delete configmap cron-query-config
```

## Manifest Files Explained

### deployment.yaml

Contains two resources:

**1. Deployment:**
- Manages 2 replicas of the application
- Defines container image, ports, environment variables
- Sets resource requests/limits (CPU, memory)
- Configures health checks (liveness and readiness probes)
- Mounts ConfigMap as a file

**2. Service:**
- Type: LoadBalancer (exposes externally)
- Maps port 80 to container port 8080
- Routes traffic to pods with label `app=cron-query-service`

### configmap.yaml

**ConfigMap:**
- Stores the test crontab file as configuration data
- Mounted into pods as `/app/test_crontab.txt`
- Can be updated without rebuilding the image

## Learning Concepts

### Pods
- Smallest deployable unit in Kubernetes
- Contains one or more containers
- Each pod gets its own IP address
- Pods are ephemeral (can be replaced)

### Deployments
- Manages a set of identical pods
- Ensures desired number of replicas are running
- Handles rolling updates and rollbacks
- Self-healing (restarts failed pods)

### Services
- Provides stable network endpoint for pods
- Load balances traffic across pods
- Types: ClusterIP, NodePort, LoadBalancer

### ConfigMaps
- Store configuration data separately from code
- Can be mounted as files or environment variables
- Changes require pod restart to take effect

### Probes
- **Liveness**: Is the container alive? (restart if fails)
- **Readiness**: Is the container ready for traffic? (remove from service if fails)

### Resource Management
- **Requests**: Minimum resources guaranteed
- **Limits**: Maximum resources allowed
- Helps Kubernetes schedule pods efficiently

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods

# View pod details
kubectl describe pod <pod-name>

# Check logs
kubectl logs <pod-name>
```

Common issues:
- Image pull errors (check `imagePullPolicy`)
- Resource constraints (check node resources)
- ConfigMap not found (apply configmap.yaml first)

### Service Not Accessible

```bash
# Check service
kubectl get service cron-query-service

# Check endpoints
kubectl get endpoints cron-query-service
```

If no endpoints:
- Pods might not be ready (check readiness probe)
- Label selector might not match pods

### Health Checks Failing

```bash
# Check probe configuration
kubectl describe pod <pod-name> | grep -A 5 Liveness
kubectl describe pod <pod-name> | grep -A 5 Readiness

# Test health endpoint manually
kubectl exec <pod-name> -- curl http://localhost:8080/actuator/health
```

Adjust `initialDelaySeconds` if app takes longer to start.

## Advanced Topics (Next Steps)

Once comfortable with basics, explore:

1. **Namespaces**: Isolate resources
   ```bash
   kubectl create namespace cron-query-dev
   kubectl apply -f deployment.yaml -n cron-query-dev
   ```

2. **Ingress**: Advanced HTTP routing
   - Route multiple services through one IP
   - SSL/TLS termination
   - Path-based routing

3. **Helm**: Package manager for Kubernetes
   - Template your manifests
   - Manage releases
   - Share charts

4. **Secrets**: Store sensitive data
   - Database passwords
   - API keys
   - Certificates

5. **Persistent Volumes**: Stateful storage
   - Database data
   - File uploads
   - Logs

6. **Horizontal Pod Autoscaler**: Auto-scaling
   ```bash
   kubectl autoscale deployment cron-query-service --cpu-percent=70 --min=2 --max=10
   ```

## Resources

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
- [Kubernetes Patterns](https://www.redhat.com/en/resources/oreilly-kubernetes-patterns-cloud-native-apps)
- [Play with Kubernetes](https://labs.play-with-k8s.com/) - Free online playground
