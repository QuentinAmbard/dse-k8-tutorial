# Node Anti affinity

It's generally best to run a single DSE instance per k8s host, especially if a NodePort is used.   

This is done using a `podAntiAffinity` rule:


```bash
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
        fsGroup: 999
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app #refers to template.metadata.labels.app
                operator: In
                values:
                - dse #we don't want our node to be collocated with another DSE pod
            topologyKey: kubernetes.io/hostname
      containers:
      - name: dse
        image: datastax/dse-server:6.0.5
        args: ["-s"] #start the node with search enabled
        imagePullPolicy: IfNotPresent
        readinessProbe:
          tcpSocket:
            port: 9042
          initialDelaySeconds: 15 #wait 15sec before trying to test
          timeoutSeconds: 5 #run the check every 5 sec
        livenessProbe:
          tcpSocket:
            port: 9042
          initialDelaySeconds: 60 #Give some time to the node to startup
          timeoutSeconds: 1 #error if it doesn't answer within 1 sec
          periodSeconds: 10 
          failureThreshold: 10 #kill after 10 failures
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
labels 
pin dse to k8 host

https://kubernetes.io/docs/concepts/configuration/assign-pod-node/