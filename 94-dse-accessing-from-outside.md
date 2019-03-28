# Accessing DSE from the outside

## pinning a node to a host

Accessing the node from outside the cluster is challenging. 

The easiest solution is to pin the dse instance to a specific host and use this host as contact point.
in order to pin dse to a restricted set of k8 host, put labels on the nodes: 

```bash
kubectl label nodes <node-name> <label-key>=<label-value>
```
## Using a LoadBalancer per pod with a static IP
!!With this solution, we need to be able to create a LoadBalancer with a fixed/static IP address. This isn't doable in AWS for now.!!

We can deploy 1 LoadBalancer per POD with a static IP, and use `externalTrafficPolicy: Local` to make sure the LB is only deployed against the host running the dse-0 pod. 

If we have a replicaset with 3 instances, we'll need to deploy 3 services, 1 per pod.

The ability to bind a loadBalancerIP depends of the provider. It should work on PKS and OpenShift, and I believe GKE and EKS have paid option for this feature (TODO: check this).  

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

On AWS, we can use a external-dns service to map the ELB against a route53 DNS entry. https://github.com/kubernetes-incubator/external-dns/blob/master/docs/tutorials/aws.md
 
Edit your kops setup to allow the nodes to change your route53 settings:
```bash
kops edit cluster ${CLUSTER_NAME}
```
```yaml
spec:
  additionalPolicies:
    node: |
      [
        {
          "Effect": "Allow",
          "Action": [
            "route53:ChangeResourceRecordSets"
          ],
          "Resource": [
            "arn:aws:route53:::hostedzone/*"
          ]
        },
        {
          "Effect": "Allow",
          "Action": [
            "route53:ListHostedZones",
            "route53:ListResourceRecordSets"
          ],
          "Resource": [
            "*"
          ]
        }
      ]
    master: |
      [
        {
          "Effect": "Allow",
          "Action": [
            "route53:ChangeResourceRecordSets"
          ],
          "Resource": [
            "arn:aws:route53:::hostedzone/*"
          ]
        },
        {
          "Effect": "Allow",
          "Action": [
            "route53:ListHostedZones",
            "route53:ListResourceRecordSets"
          ],
          "Resource": [
            "*"
          ]
        }
      ]
```

Now you can run a cluster update to have the changes take effect:
```bash
kops update cluster ${CLUSTER_NAME} --yes
```
Kops is secured by default with RBAC (role based acces control), externalDNS will need to have access to k8 api, to make it easier we can set the default user as admin:
```bash
kubectl create clusterrolebinding default-admin --clusterrole cluster-admin --serviceaccount=default:default
```
Start by installing the route53 mapper addon:

```yaml
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: external-dns
spec:
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: external-dns
    spec:
      containers:
      - name: external-dns
        image: registry.opensource.zalan.do/teapot/external-dns:v0.5.11
        args:
        - --source=service
        - --source=ingress
        - --provider=aws
        - --registry=txt
        - --txt-owner-id=<<PUT_UNIQUE_ID_HERE>>
        - --log-level=debug

```
Create the external service deployment
```bash
kubectl apply -f external-dns.yml
```


Next, we need to add the `external-dns.alpha.kubernetes.io/hostname` annitation:

```yaml
kind: Service
apiVersion: v1
metadata:
  name: simple-app-lb-0
  labels:
    app: simple-app-lb-0
    dns: route53
  annotations:
    external-dns.alpha.kubernetes.io/hostname: test2.octoshield.com.
	external-dns.alpha.kubernetes.io/ttl: 60
spec:
  selector:
    statefulset.kubernetes.io/pod-name: simple-app-ss-0
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer

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
  template:
    metadata:
      labels:
        app: simple-app #must match spec.selector.matchLabels
    spec:
      containers:
      - name: simple-app
        image: 553261234129.dkr.ecr.us-east-2.amazonaws.com/k8s-training:yourName
        ports:
        - containerPort: 8080  
```
Make sure the dns entries are properly updated:
```bash
kubectl logs -f external-dns-6d5668477-t6jkm
```
Once it's working with our simple app demo, we can do the same with a LB on 9042 
The only downside is that 

## Using Hostport
Now that our pods are pinned to a specific host, we need to expose our pod port to the host.
It can be done with the `hostPort: 9042` instruction.

### using NodePort

A better way is to use a NodePort service with `spec.externalTrafficPolicy: Local`. By setting the policy to local, the host will only forward the traffic to the local pods. Because we only deploy 1 DSE node per k8 host, it'll redirect the traffic to the proper dse node. 


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

## Broadcast address
Since the node will be accessed from outside of the k8s cluster, we need to broadcast the k8s host IPs to the clients (and not the POD IPs).

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
