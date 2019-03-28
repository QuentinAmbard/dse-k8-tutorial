# Add storage to our simple application

Our app will require a local storage to be able to store temporary files being uploaded for example.

Lots of different volume type can be used: see doc at https://kubernetes.io/docs/concepts/storage/volumes 


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
        image: 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin:v2
        volumeMounts:
        - mountPath: /temp-data
          name: crash-dir
        ports:
        - containerPort: 8080
        resources:
          requests: #What the container will be assigned at startup
            memory: "128Mi"
            cpu: "250m" #0.25 = 25% of a cpu
          limits: #What the container can use as max while running
            memory: "256Mi" 
            cpu: "1"
      volumes:
        - name: crash-dir
          emptyDir: {}
```

Let's update our new deployment
```bash
kubectl apply -f simple-app-deploy.yml
kubectl get pods
NAME                                 READY   STATUS    RESTARTS   AGE
simple-app-deploy-66dfdc8fb8-b65xl   1/1     Running   0          7s
simple-app-deploy-66dfdc8fb8-gqxqh   1/1     Running   0          19s
simple-app-deploy-66dfdc8fb8-xnnc5   1/1     Running   0          19s
```

We can now write data into this directory:
```bash
kubectl exec simple-app-deploy-66dfdc8fb8-xnnc5 -it -- /bin/sh
echo "toto" > /temp-data/toto.txt
```

`emptyDir` will create a dir locally to our node. Typically, our file is stored locally in `./var/lib/kubelet/pods/8f6e178d-2ea5-11e9-86e8-0e8fde64d7dc/volumes/kubernetes.io~empty-dir/crash-dir/toto.txt`

Many different volume type exist, check https://kubernetes.io/docs/concepts/storage/volumes for more details. 