# Deploy the simple-webapp on k8 POD

A pod is the atomic unit of k8. 1 pod can run multiple docker images (typically the webapp and a side car for a monitoring agent)

To deploy a Pod to a Kubernetes cluster we define it in a manifest file and then POST that manifest file to the API server. 

Each Pod creates its own network namespace - a single IP address, a single range of ports, and a single routing table. if the Pod is a multi-container Pod - each container in a Pod shares the Pods IP, range of ports, and routing table.

Connect to your master node and create a simple-app-pod.yaml file with the following:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: simple-app #pod name
  labels: #some custom labels
    zone: prod 
    version: v1 
spec:
  containers:
  - name: simple-app #pod name
    image: 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin
    ports:
    - containerPort: 8080 #we expose the port our webapp is running on
```

Then start your pod:
```bash
kubectl create -f simple-app-pod.yaml
pod/simple-app created
```

Make sure your pod is running (it can take time to pull the required image to start the container inside the Pod.)
```bash
kubectl get pods
NAME         READY   STATUS    RESTARTS   AGE
simple-app   0/1     Pending   0          105s

```
You can also get a more complete view of your pod:
```bash
kubectl describe pods simple-app
```

We can make sure our jar is running in our pod:

```bash
kubectl exec simple-app ps aux | grep jar
1 root      0:08 java -jar /app.jar
```
Check our application logs:

```bash
kubectl logs -f simple-app
```

Or just start a shell:

```bash
kubectl exec -it simple-app sh
```
For now we cannot (easily) access our service deployed on 8080 from within the cluster or the outside. We'll come to that later with a more advanced type of deployment.
