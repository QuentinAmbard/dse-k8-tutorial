# Stateful sets

Let's apply our statefulset with the DSE image. DSE drivers request a list of contact point to access the nodes. We'll be using the node hostname.

Because we use the hostname and not a service IP, we'll use a headless service for our DSE replication. It won't expose 1 global port across all nodes for our service which doesn't make sense for C*. This is done by setting `spec.type: ClusterIP`

Cassandra itself is able to restart with a different IP without any issue, but it'll create some issues in the driver <4.0 and the current opscenter version (6.7). 

Because our statefulSet is called `dse`, each pod will get a DNS following this pattern: `<statefulName>-X.<serviceName>.default.svc.cluster.local` (ex: `dse-0.dse-service.default.svc.cluster.local`). If you are unsure you can get the value from  `kubectl exec -it dse-2 -- cat /etc/hosts`

dse.yml configuration:
    
```yaml
apiVersion: v1
kind: Service
metadata:
  name: dse-service
  labels:
    app: dse
spec:
  clusterIP: None
  ports:
  - port: 9042
    name: cql
  selector:
    app: dse

---
   
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: dse
  labels:
    app: dse
spec:
  serviceName: dse-service
  replicas: 3 #Start 3 DSE nodes
  selector:
    matchLabels:
      app: dse
  podManagementPolicy: OrderedReady
  updateStrategy:
    type: RollingUpdate
  template:
    metadata:
      labels:
        app: dse
    spec:
      securityContext:
        fsGroup: 999 #We set DSE user here to be able to read/write on our ebs
      containers:
      - name: dse
        image: datastax/dse-server:6.0.5
        args: ["-s"] #start the node with search enabled
        imagePullPolicy: IfNotPresent
        env:
        - name: DS_LICENSE
          value: accept
        - name: SEEDS
          value: dse-0.dse-service.default.svc.cluster.local,dse-1.dse-service.default.svc.cluster.local
        - name: CLUSTER_NAME
          value: "My_Cluster"
        - name: NUM_TOKENS
          value: "8"
        - name: DC
          value: "DC-1"
        - name: RACK
          value: "rack-1"
        - name: SNITCH
          value: GossipingPropertyFileSnitch
        - name: JVM_EXTRA_OPTS
          value: "-Xms1500m -Xmx1500m" #must be set to match the resource limits.
        ports:
        - containerPort: 7000
          name: intra-node
        - containerPort: 7199
          name: jmx
        - containerPort: 9042
          name: cql        
        volumeMounts:
        - name: dse-data
          mountPath: /var/lib/cassandra
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "2Gi" 
            cpu: "1" 
  volumeClaimTemplates:
  - metadata:
      name: dse-data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: "gp2"
      resources:
        requests:
          storage: 10Gi
```

Deploy our new service and replicaset:

```bash
kubectl create -f dse.yml 
service/dse-service created
statefulset.apps/dse created
```

```bash
kubectl get pods -l app=dse
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
