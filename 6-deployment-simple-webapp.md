# Upgrading our simple app with K8 Deployment

What if we want to upgrade our application to a new version? We could create a new ReplicaSet with the same labels which will automatically be added to our service and then destroy the old one.

Deployments are a K8 object allowing to perform rolling upgrade and rollbacks with much more options and keeping a single manifest file for our application. They also allow features like "wait X number of seconds between upgrade" or "wait X number of seconds after each Pod comes up before us mark it as healthy"... 

Using Deployments, K8 will automatically create and clear ReplicaSet for us without having to do anything.

Let's start by deleting our simple-app replicaset. Remember, this won't affect our service.

```bash
kubectl delete rs simple-app-rs
replicaset.extensions "simple-app-rs" deleted
```
Our service is still running, but without any endpoints:
```bash
kubectl get ep simple-app-svc 
NAME             ENDPOINTS   AGE
simple-app-svc   <none>      21m
``` 

Let's create our deployment simple-app-deploy.yml:

```yaml
apiVersion: apps/v1beta2
kind: Deployment
metadata:
  name: simple-app-deploy
spec:
  replicas: 3
  selector:
    matchLabels:
      app: simple-app
  minReadySeconds: 10 #wait 10 sec after the node is in ready state before continuing
  strategy:
    type: RollingUpdate #How to perform our future upgrade
    rollingUpdate:
      maxUnavailable: 1 #Allow 1 node down
      maxSurge: 1 #Allow 1 extra node up (so upgrade 1 pod, delete 1 old, upgrade the next etc) 
  template:
    metadata:
      labels:
        app: simple-app #must match spec.selector.matchLabels
    spec:
      containers:
      - name: simple-app
        image: <masterIP>:5000/k8-training/simple-webapp
        ports:
        - containerPort: 8080
        resources:
          requests: #What the container will be assigned at startup
            memory: "128Mi"
            cpu: "250m" #0.25 = 25% of a cpu
          limits: #What the container can use as max while running
            memory: "256Mi" 
            cpu: "1" 
```
Send it to K8 master:
```bash
kubectl create -f simple-app-deploy.yml
```

As usual, we can use get and describe to check the status of our deployment:

```bash
kubectl get deploy simple-app-deploy
NAME                READY   UP-TO-DATE   AVAILABLE   AGE
simple-app-deploy   3/3     3            3           45s
```

```bash
kubectl describe deploy simple-app-deploy
Name:                   simple-app-deploy
Namespace:              default
CreationTimestamp:      Sun, 10 Feb 2019 15:20:08 +0000
Labels:                 <none>
Annotations:            deployment.kubernetes.io/revision: 1
Selector:               app=simple-app
Replicas:               3 desired | 3 updated | 3 total | 3 available | 0 unavailable
StrategyType:           RollingUpdate
MinReadySeconds:        10
RollingUpdateStrategy:  1 max unavailable, 1 max surge
Pod Template:
  Labels:  app=simple-app
  Containers:
   simple-app:
    Image:      <masterIP>:5000/k8-training/simple-webapp
    Port:       8080/TCP
    Host Port:  0/TCP
    Limits:
      cpu:     1
      memory:  256Mi
    Requests:
      cpu:        250m
      memory:     128Mi
    Environment:  <none>
    Mounts:       <none>
  Volumes:        <none>
Conditions:
  Type           Status  Reason
  ----           ------  ------
  Available      True    MinimumReplicasAvailable
  Progressing    True    NewReplicaSetAvailable
OldReplicaSets:  <none>
NewReplicaSet:   simple-app-deploy-5676ff448d (3/3 replicas created)
Events:
  Type    Reason             Age   From                   Message
  ----    ------             ----  ----                   -------
  Normal  ScalingReplicaSet  67s   deployment-controller  Scaled up replica set simple-app-deploy-5676ff448d to 3
```

```bash
curl 10.99.227.133:8080
Hello Docker World
```

Deployment automatically create Replicasets:

```bash
kubectl get rs
NAME                           DESIRED   CURRENT   READY   AGE
simple-app-deploy-5676ff448d   3         3         3       2m48s
```

## Performing a rolling upgrade
Let's release a new version of our simple-app. Open Application.java and change the message returned, for example

```java
    public String home() {
        return "Hello Docker World V2";
    }
```

Let's deploy a new image for our new version. Create the jar updated and build a new image with a new tag using version v2:


```bash
./mvnw package
docker build -t <masterIP>:5000/k8-training/simple-webapp:v2 --build-arg JAR_FILE=target/gs-spring-boot-docker-0.1.0.jar .
```
Our new image is available locally:
```bash
 docker image ls                                                                                                               
REPOSITORY                                      TAG                 IMAGE ID            CREATED             SIZE
<masterIP>:5000/k8-training/simple-webapp   v2                  5c72a92a491a        2 seconds ago       121MB
```
Let's send it to our K8 registry:

```bash
docker push <masterIP>:5000/k8-training/simple-webapp:v2
```

We can now update our simple-app-deploy.yaml to the new version:
```yaml
apiVersion: apps/v1beta2
kind: Deployment
metadata:
  name: simple-app-deploy
spec:
  replicas: 3
  selector:
    matchLabels:
      app: simple-app
  minReadySeconds: 10 #wait 10 sec after the node is in ready state before continuing
  strategy:
    type: RollingUpdate #How to perform our future upgrade
    rollingUpdate:
      maxUnavailable: 1 #Allow 1 node down
      maxSurge: 1 #Allow 1 extra node up (so upgrade 1 pod, delete 1 old, upgrade the next etc) 
  template:
    metadata:
      labels:
        app: simple-app #must match spec.selector.matchLabels
    spec:
      containers:
      - name: simple-app
        image: <masterIP>:5000/k8-training/simple-webapp:v2 #CHANGE THIS LINE ONLY
        ports:
        - containerPort: 8080
        resources:
          requests: #What the container will be assigned at startup
            memory: "128Mi"
            cpu: "250m" #0.25 = 25% of a cpu
          limits: #What the container can use as max while running
            memory: "256Mi" 
            cpu: "1" 
```

```bash
kubectl apply -f simple-app-deploy.yml  --record
```
Check the upgrade state
```bash
kubectl rollout status deployment simple-app-deploy
Waiting for deployment "simple-app-deploy" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "simple-app-deploy" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "simple-app-deploy" rollout to finish: 2 out of 3 new replicas have been updated...
Waiting for deployment "simple-app-deploy" rollout to finish: 2 of 3 updated replicas are available...
Waiting for deployment "simple-app-deploy" rollout to finish: 2 of 3 updated replicas are available...
deployment "simple-app-deploy" successfully rolled out
```
Everything is now operational

```bash
kubectl get deploy simple-app-deploy
NAME                READY   UP-TO-DATE   AVAILABLE   AGE
simple-app-deploy   3/3     3            3           52m
```

The app is updated to the v2 version:
```bash
curl 10.99.227.133:8080
Hello Docker World V2
```

##performing a rollback
Because we used `--record`, we can list the deployment history: 
 
```bash
kubectl rollout history deployment simple-app-deploy
deployment.extensions/simple-app-deploy 
REVISION  CHANGE-CAUSE
1         kubectl apply --filename=simple-app-deploy.yml --record=true
```

If we list the replica set, we can see that our deployment created a new one and the old one is now empty:

```bash
kubectl get rs
NAME                           DESIRED   CURRENT   READY   AGE
simple-app-deploy-5676ff448d   0         0         0       55m
simple-app-deploy-887844c94    3         3         3       9m3s
```
All we have to do is tell K8 to redeploy the previous RS:

```bash
kubectl rollout undo deployment simple-app-deploy --to-revision=1
deployment.extensions/simple-app-deploy rolled back
```

The old RS is now active:
```bash
kubectl get rs
NAME                           DESIRED   CURRENT   READY   AGE
simple-app-deploy-5676ff448d   3         3         3       57m
simple-app-deploy-887844c94    0         0         0       11m
```

You can define the number of replicaset you archive through `.spec.revisionHistoryLimit`