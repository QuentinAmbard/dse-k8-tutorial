# MultiDC setup

Multi DC on a single k8s cluster is pretty simple. The DC Name can be set as label and accessed during the pod startup.

Things get much more complicated when the DC are located in different k8s cluster, or in an hybrid configuration.

## pinning the DSE instances to a specific list of host

The easiest solution is probably to pin the dse instance to a specific host and use these hosts as contact points and seed address.

However, you lose most k8s advantage: k8 won't be able to restart the node on a different one if the node is destroyed since we only allow it to start on a specific number of nodes.  

In order to pin dse to a restricted set of k8 host, put labels on the nodes: 
```bash
kubectl label nodes <node-name> app=dse
```
We can then add a `spec.nodeSelector` to our replicaset:
```yaml
spec:
  nodeSelector:
    app: dse
```


### Using Hostport
Now that our pods are pinned to a specific host, we need to expose our pod port to the host.
It can be done with the `hostPort: 9042` instruction, but as discussed previously (`accessing-the-application-from-the-outside.md`), that's not a great solution.

### using NodePort

A better way is to use a NodePort service with `spec.externalTrafficPolicy: Local`. By setting the policy to local, the host will only forward the traffic to the local pods. 

Because we only deploy 1 DSE node per k8 host, it'll redirect the traffic to the proper dse node. Yeaay!

vim dse-external.yaml :
```bash
apiVersion: v1
kind: Service
metadata:
  name: dse-external
  labels:
    app: dse
spec:
  externalTrafficPolicy: Local
  ports:
  - nodePort: 30042
    port: 30042
    protocol: TCP
    targetPort: 9042
  selector:
    app: dse
  type: NodePort
```


### Broadcast address
Since the nodes are in 2 different clusters, we need to broadcast the k8s host IPs (and not the POD IPs).

This can easily be done with the following conf in your statefulset file to change this setting in the cassandra.yaml file: 
```yaml
...
    env:
    - name: NATIVE_TRANSPORT_BROADCAST_ADDRESS
      valueFrom:
        fieldRef:
          fieldPath: status.hostIP
...
```

apply the changes and run the usual `kubectl apply -f dse.yaml`

We can now check that the node is properly configured:
```bash
kubectl exec dse-0 cat /opt/dse/resources/cassandra/conf/cassandra.yaml | egrep "(^listen_address|^native_transport_broadca)"
listen_address: 100.96.4.3
native_transport_broadcast_address: 172.20.45.192
```

In this case `listen_address: 100.96.4.3` is the pod IP and `native_transport_broadcast_address: 172.20.45.192` is the host (AWS EC2 instance) IP.

If the k8 cluster need to be accessed both from the outside and inside k8s, an AddressTranslators could be used to route the application internal on the `native_transport_address` and not the `native_transport_broadcast_address` (to reproduce something similar to the `prefer_local=true` settings in cassandra-rackdc.properties)

## Without pinning 
Pinning a DSE pod to a k8 node is far from being a good solution. It's not dynamic as we have to manually flag the nodes we want to deploy the nodes and retrieve theses ips to setup the seed nodes of each cluster, and potentially the client contact point if you require external connection.

But other solution exists

## Using a LoadBalancer per pod with a static IP
!!With this solution, we need to be able to create a LoadBalancer with a fixed/static IP address. This isn't doable in AWS for now.!!

We can deploy 1 LoadBalancer per POD with a static IP, and use `externalTrafficPolicy: Local` to make sure the ELB only see the the host running the dse-0 pod (it'll put all your nodes in the ELB but only see 1 as up. TODO: is there a better way?). 

If we have a replicaset with 3 instances (1 DSE DC with 3 nodes), we'll need to deploy 3 services, 1 per pod.

The ability to bind a loadBalancerIP depends of the provider.  (TODO: check this, I believe GKE and EKS have paid option for this feature)

```yaml
kind: Service
apiVersion: v1
metadata:
  name: dse-lb-0
  labels:
    app: dse-lb-0
spec:
  externalTrafficPolicy: Local
  selector:
    statefulset.kubernetes.io/pod-name: dse-0
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  loadBalancerIP: 35.181.69.47
  type: LoadBalancer
``` 

Once it's done, we can access each DSE node using the IP defined in the service.

## Using an external plugin to map the loadbalancer to an external DNS
Because we can't map the LoadBalancer to a static IP, another way to access the LoadBalancer from the outside is to automatically link a DNS to the LB IP.

This requires to use a tool which will use the kubernetes API to get the list of services and update your DNS configuration (see `accessing-the-application-from-the-outside.md`)

We'll create 1 Service for each Pod in our replicaset, and create 1 DNS for each Pod.

With this setup, we can bind the DSE nodes to the internal k8 IP, broadcast the DNS IP which will be the IP of our AWS ELB connected to a single node, use the prefer_local

Install externalDNS (see `accessing-the-application-from-the-outside.md`)

Next, we need to add the `external-dns.alpha.kubernetes.io/hostname` annotation:

```yaml
kind: Service
apiVersion: v1
metadata:
  name: dse-lb-listen_address-0
  labels:
    app: dse-lb-listen_address-0
  annotations:
    external-dns.alpha.kubernetes.io/hostname: dse-0-dc1-qa.dse-k8s-training.com.
	external-dns.alpha.kubernetes.io/ttl: "60"
spec:
  selector:
    statefulset.kubernetes.io/pod-name: dse-0
  ports:
  - protocol: TCP
    port: 7000
    targetPort: 7000
  type: LoadBalancer
```

Make sure the dns entries are properly updated:
```bash
kubectl logs -f external-dns-6d5668477-t6jkm
```

The only remaining step is to setup the broadcast IP of the node (listen and native) to `dse-0-dc1-qa.dse-k8s-training.com`

We'll be using the name of the pod (`dse-0`, `dse-1` etc.) and retrieve it with

```yaml
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: BROADCAST_ADDRESS
          value: "$(POD_NAME)-dc1-qa.dse-k8s-training.com"
```

If you want to delete everything, don't forget to delete your pvcs to restart with a clean setup: 

```bash
kubectl delete -f dse.yml && kubectl delete pvc dse-data-dse-0 && kubectl delete pvc dse-data-dse-1 && kubectl delete pvc dse-data-dse-2
```




-------------------------
-------------------------
-------------------------
-------------------------
-------------------------
-------------------------

EXTRA: 
### Testing NodePort
First, create the dse-external service. 

```bash
kubectl create -f dse-external.yaml 

kubectl describe service dse-external
Name:                     dse-external
Namespace:                default
Labels:                   app=dse
Annotations:              kubectl.kubernetes.io/last-applied-configuration:
                            {"apiVersion":"v1","kind":"Service","metadata":{"annotations":{},"labels":{"app":"dse"},"name":"dse-external","namespace":"default"},"spec...
Selector:                 app=dse
Type:                     NodePort
IP:                       100.64.237.233
Port:                     <unset>  30042/TCP
TargetPort:               9042/TCP
NodePort:                 <unset>  30042/TCP
Endpoints:                100.96.3.2:9042,100.96.4.2:9042,100.96.5.2:9042
Session Affinity:         None
External Traffic Policy:  Local
```

The port 30042 should now be exposed on each host having a pod running, and forwarding to the internal pod on 9042:
```bash
netstat -laputen | grep 30042
tcp6       0      0 :::30042                :::*                    LISTEN      0          45513      2466/kube-proxy     
```

To test it, we can try to run cqlsh from one of the k8s node itself. First check on which k8s node (host) your pod is running:
```bash
kubectl describe pod dse-0ame:               dse-0
Namespace:          default
Priority:           0
PriorityClassName:  <none>
Node:               ip-172-20-45-192.eu-west-3.compute.internal/172.20.45.192
...
```
You can connect to this node (use its public IP): ssh admin@35.180.51.203

Install dse locally to get a cqlsh shell:

```bash
echo "deb https://quentin.ambard%40datastax.com:xxxxxx@debian.datastax.com/enterprise/ stable main" | sudo tee -a /etc/apt/sources.list.d/datastax.sources.list
curl -L https://debian.datastax.com/debian/repo_key | sudo apt-key add -
sudo apt-get update
sudo apt-get install dse-full
```
Try the connection on the 30042 port:
```bash
cqlsh localhost 30042
Connected to My_Cluster at localhost:30042.
[cqlsh 5.0.1 | DSE 6.0.5 | CQL spec 3.4.5 | DSE protocol v2]
Use HELP for help.
cqlsh> 
```







# Running kubernetes on multiple DC (or multiple AWS regions)

Kubernetes has a concept of federation. However, the current version isn't working properly and the V2 is in active development. From the documentation:

```bash
Use of Federation v1 is strongly discouraged. Federation V1 never achieved GA status and is no longer under active development. Documentation is for historical purposes only.
https://kubernetes.io/docs/concepts/cluster-administration/federation/
```

One of the main issue for our DSE deployment is the network: internal POD ips aren't available/routable between each DC.
 
## Hacky solution

- Open network between the 2 DC in your security group
- Pin the DSE nodes to a specific host
- Set cluster name and seed nodes to a proper value 
- Use NodePort with `externalTrafficPolicy: Local` to expose the node from the outside
- Deploy the simple demo app in each cluster (use a `Deployment`). 
- Configure the app to execute QUORUM requests to ensure it's working properly

   Hint: the consistency level can be changed in the application using an env variable.
   
   Example using docker (change it accordingly in your app Deployment)

```bash
docker run -e demo.cassandra.contactPoint=192.168.43.239 -p 8080:8080 -t demo-app
``` 
 
Very good reading:
https://www.cockroachlabs.com/blog/experience-report-running-across-multiple-kubernetes-clusters/

