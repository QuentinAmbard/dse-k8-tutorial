# Stateful sets

Unlike a Deployment, a StatefulSet maintains a sticky identity for each of their Pods. These pods are created from the same spec, but are not interchangeable: each has a persistent identifier that it maintains across any rescheduling.

When deploying DSE, it offers 3 advantages:

 - It allows to have fixed DNS (BUT IT WONT HAVE FIXED IP, which is an issue for opscenter and C* driver before 4.0)
 - It allows to have stateful disk. If a pod crashes, it'll be re-created using the same ebs.
 - It allows ordered deployment and updates. 

Let's create a stateful set using our simple-app example:

    
```yaml
apiVersion: v1
kind: Service
metadata:
  name: simple-app-service
  labels:
    app: simple-app
spec:
  type: NodePort
  ports:
  - port: 8080
    name: web
    nodePort: 30001
  selector:
    app: simple-app

---
   
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: simple-app-ss
spec:
  serviceName: simple-app-service
  replicas: 3
  selector:
    matchLabels:
      app: simple-app
  updateStrategy:
    type: RollingUpdate
  template:
    metadata:
      labels:
        app: simple-app #must match spec.selector.matchLabels
    spec:
      containers:
      - name: simple-app
        image: 35.180.131.219:5000/k8-training/simple-webapp
        ports:
        - containerPort: 8080
        volumeMounts:
        - name: www
          mountPath: /var/www
        resources:
          requests: #What the container will be assigned at startup
            memory: "128Mi"
            cpu: "250m" #0.25 = 25% of a cpu
          limits: #What the container can use as max while running
            memory: "256Mi" 
            cpu: "1" 
  volumeClaimTemplates:
  - metadata:
      name: www
    spec:
      accessModes: [ "ReadWriteOnce" ] #EBS only support ReadWriteOnce
      storageClassName: "gp2" #if ignored will use kubectl get storageclass
      resources:
        requests:
          storage: 1Gi
```
Deploy our new service and replicaset:

```bash
 kubectl create -f simple-app-ss.yml 
service/simple-app-service created
statefulset.apps/simple-app-ss created
```

Our service is now running and we can access it:
```bash
kubectl describe service simple-app-service
Name:                     simple-app-service
Namespace:                default
Labels:                   app=simple-app
Annotations:              <none>
Selector:                 app=simple-app
Type:                     NodePort
IP:                       100.70.217.22
Port:                     web  8080/TCP
TargetPort:               8080/TCP
NodePort:                 web  30001/TCP
Endpoints:                100.96.1.24:8080,100.96.2.26:8080,100.96.2.27:8080
Session Affinity:         None
External Traffic Policy:  Cluster
Events:                   <none>


curl simple-app-service:8080
Hello Docker World V2
```
