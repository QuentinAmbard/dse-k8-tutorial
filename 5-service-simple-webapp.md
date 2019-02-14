# Access our simple app with a K8 Service

Our app is deployed on a replicaset. We can easily scale it down and up, but now we want to be able to access our simple-app page.

Our concern is that pod IPs are unreliable. When Pods fail, they are replaced with new Pods with new IPs. Scaling-up a ReplicaSet introduces new Pods with new, previously unknown, IP addresses.

That's what K8 Service are for. They provide a stable IP address, DNS name, and port.

Services uses labels to dynamically associate with a set of Pods. They will act as a a load-balancer between our pods and split the load in a round-robin fashion.

Let's create a service for our simple-app replicaset. Our replicaset was created with the label "app: simple-app", we'll use that in our service selector.


```yaml
apiVersion: v1
kind: Service
metadata:
  name: simple-app-svc
  labels:
    app: simple-app 
spec:
  type: NodePort #Makes the  service available outside of the cluster on this port (30001). ClusterIP is default
  ports:
  - port: 8080 #Our application port 
    nodePort: 30001
    protocol: TCP
  selector:
    app: simple-app    # Label selector, much match our pod labels in the rs spec.template.metadata.labels
```

pin nodes

Driver => kill the pod if too many error for driver 3.x (because ip are cached before 4.0)


Because this service is named `simple-app-svc`, K8 DNS will create an entry for this service and our webapp will become available on this fixed IP from inside our K8 cluster.

A Service port is cluster-wide. If we want to access it from the outside, we can use this port to connect from any of our node K8 node. From the outside, we can use NodePort.

Let's deploy our new service:
```bash
kubectl create -f simple-app-svc.yml
```

Make sure our service is properly started:  
```bash
kubectl get svc simple-app-svc 
NAME             TYPE       CLUSTER-IP      EXTERNAL-IP   PORT(S)          AGE
simple-app-svc   NodePort   10.99.227.133   <none>        8080:30001/TCP   2m30s
```
We can check that our 5 pods (endpoints) have been matched for this service:
```bash
kubectl describe svc simple-app-svc  
Name:                     simple-app-svc
Namespace:                default
Labels:                   app=simple-app
Annotations:              <none>
Selector:                 app=simple-app
Type:                     NodePort
IP:                       10.99.227.133
Port:                     <unset>  8080/TCP
TargetPort:               8080/TCP
NodePort:                 <unset>  30001/TCP
Endpoints:                10.244.0.8:8080,10.244.1.2:8080,10.244.1.3:8080 + 2 more...
Session Affinity:         None
External Traffic Policy:  Cluster
Events:                   <none>
```
Get the detailed list of endpoint (ep)
```bash
kubectl describe ep simple-app-svc 
Name:         simple-app-svc
Namespace:    default
Labels:       app=simple-app
Annotations:  <none>
Subsets:
  Addresses:          10.244.0.8,10.244.1.2,10.244.1.3,10.244.2.2,10.244.2.3
  NotReadyAddresses:  <none>
  Ports:
    Name     Port  Protocol
    ----     ----  --------
    <unset>  8080  TCP
```

Our service is now available from within the K8 cluster:

```bash
curl 10.99.227.133:8080
Hello Docker World
```
And also from the outside:
```bash
curl <MASTER_IP>:30001
```

