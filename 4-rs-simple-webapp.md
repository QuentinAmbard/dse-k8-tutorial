# Deploy the simple-webapp on a k8 ReplicaSet

A single pod isn't great.  We don’t get any self-healing or scalability. So, if that Pod fails, it’s game over!

We want to be able to deploy N instances of our application to provide a scalable and resilient application.

Because of this, we almost always deploy Pods via higher-level objects like ReplicaSets and Deployments.

K8 is mostly used with a declarative approach: we tell K8 what's the state we want and let K8 do the job. We usually don't explicity tell k8 to kill or deploy a new pod. 

Instead we describe a replicaset with N pods. K8 periodically checks if the pods deployed matches the replicaset configuration. If something isn't correct (ex: a pod just crashed), it'll kill or start a new pod 

Let's delete our previous pod first:

```bash
kubectl delete pods simple-app 
```
Create a new simple-app-rs.yaml file containing our replica-set definition:

```yaml
apiVersion: apps/v1beta2
kind: ReplicaSet
metadata:
  name: simple-app-rs
spec:
  replicas: 3 #number of replicas of our pod
  selector:
    matchLabels:
      app: simple-app
  template:
    metadata:
      labels:
        app: simple-app #must match spec.selector.matchLabels
    spec:
      containers:
      - name: simple-app
        image: 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin
        ports:
        - containerPort: 8080
``` 

As you can see, the definition is a ReplicaSet with a `spec.template` containing the same definition as our previous Pod.

Once the file is ready, let's create the RS:

```bash
kubectl create -f simple-app-rs.yaml
replicaset.apps/simple-app-rs created
```
Make sure everything is running properly
```bash
kubectl get rs/simple-app-rs
NAME            DESIRED   CURRENT   READY   AGE
simple-app-rs   3         3         3       36s
```

As a ReplicaSet just deploy pods, we can inspect our pods an run the same commands as above to get the logs etc

```bash
kubectl get pods --show-labels
simple-app-rs-2tsqb   1/1     Running   0          90s   app=simple-app
simple-app-rs-st469   1/1     Running   0          90s   app=simple-app
simple-app-rs-stxxf   1/1     Running   0          90s   app=simple-app
```
To get the full description of our ReplicaSet, run:
```bash
kubectl get rs simple-app-rs --output=yaml
apiVersion: extensions/v1beta1
kind: ReplicaSet
metadata:
  creationTimestamp: "2019-02-09T17:07:39Z"
  generation: 1
  name: simple-app-rs
  namespace: default
  resourceVersion: "20151"
  selfLink: /apis/extensions/v1beta1/namespaces/default/replicasets/simple-app-rs
  uid: 344c954a-2c8d-11e9-86e8-0e8fde64d7dc
spec:
  replicas: 3
  selector:
    matchLabels:
      app: simple-app
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: simple-app
    spec:
      containers:
      - image: 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin
        imagePullPolicy: Always
        name: simple-app
        ports:
        - containerPort: 8080
          protocol: TCP
        resources: {}
        terminationMessagePath: /dev/termination-log
        terminationMessagePolicy: File
      dnsPolicy: ClusterFirst
      restartPolicy: Always
      schedulerName: default-scheduler
      securityContext: {}
      terminationGracePeriodSeconds: 30
status:
  availableReplicas: 3
  fullyLabeledReplicas: 3
  observedGeneration: 1
  readyReplicas: 3
  replicas: 3
```

ReplicaSet and Pod are loosely coupled. You can delete a replicaset and keep the pod : `kubectl delete rs/simple-app-rs --cascade=false`.

We can also recreate a new replicaset which will "adopt" the pod matching its labels

## Scaling our Simple App
Let's say that we need to scale our simple app to 5 instances, but we also want to make sure that it won't use too much RAM and CPU in our cluster.

We can update our simple-app-rs.yaml with the following

```yaml
apiVersion: apps/v1beta2
kind: ReplicaSet
metadata:
  name: simple-app-rs
spec:
  replicas: 5 #Increase our number of replicas to 5
  selector:
    matchLabels:
      app: simple-app
  template:
    metadata:
      labels:
        app: simple-app #must match spec.selector.matchLabels
    spec:
      containers:
      - name: simple-app
        image: 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin
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

Apply the changes:

```bash
kubectl apply -f simple-app-rs.yaml
```

Our cluster automatically scales to the new configuration:

```bash
kubectl get rs simple-app-rs
NAME            DESIRED   CURRENT   READY   AGE
simple-app-rs   5         5         5       21m
```

You can check your cluster details including CPU / Memory resources:

```bash
kubectl describe nodes [node-name]
...
  Namespace                  Name                           CPU Requests  CPU Limits  Memory Requests  Memory Limits  AGE
  ---------                  ----                           ------------  ----------  ---------------  -------------  ---
  default                    simple-app-rs-2tsqb            0 (0%)        0 (0%)      0 (0%)           0 (0%)         24m
  default                    simple-app-rs-5dswr            250m (12%)    1 (50%)     128Mi (1%)       256Mi (3%)     3m32s
  kube-system                kube-flannel-ds-amd64-9s66z    100m (5%)     100m (5%)   50Mi (0%)        50Mi (0%)      62m
  kube-system                kube-proxy-t5z5h               0 (0%)        0 (0%)      0 (0%)           0 (0%)         3h46m

...
```

## High availability: restart the app if the server crashes

By default, pods have a spec.restartPolicy: Always
```bash
kubectl get pods --show-labels
NAME                  READY   STATUS    RESTARTS   AGE   LABELS
simple-app-rs-2tsqb   1/1     Running   0          20h   app=simple-app
simple-app-rs-5dswr   1/1     Running   0          20h   app=simple-app
simple-app-rs-gszcs   1/1     Running   0          20h   app=simple-app
simple-app-rs-st469   1/1     Running   0          20h   app=simple-app
simple-app-rs-stxxf   1/1     Running   0          20h   app=simple-app
```

Let's kill our webapp in the first process
```bash
#get the process id (1)
kubectl exec simple-app-rs-2tsqb ps aux
PID   USER     TIME  COMMAND
    1 root      0:58 java -jar /app.jar
   35 root      0:00 ps aux
#kill it
kubectl exec simple-app-rs-2tsqb -- kill 1
```
The pod monitor by default the service in the ENTRYPOINT and will automatically get restarted (RESTARTS is now = 1):

```bash
kubectl get pods --show-labels
NAME                  READY   STATUS    RESTARTS   AGE   LABELS
simple-app-rs-2tsqb   1/1     Running   1          20h   app=simple-app
simple-app-rs-5dswr   1/1     Running   0          20h   app=simple-app
simple-app-rs-gszcs   1/1     Running   0          20h   app=simple-app
simple-app-rs-st469   1/1     Running   0          20h   app=simple-app
simple-app-rs-stxxf   1/1     Running   0          20h   app=simple-app
```
You can also check the log to see the last restart details:
```bash
kubectl logs simple-app-rs-2tsqb
```

