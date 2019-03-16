# Liveness & readiness prob

You might want to monitor your application with a more advanced solution. For example execute a custom script to check if the app is running or check for a specific port.

This is done using liveness probe.

For datastax, a probe on 9042 is always a good thing:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: dse
  labels:
    app: dse
spec:
  serviceName: dse-service
  replicas: 3 
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
      containers:
      - name: dse
        image: datastax/dse-server:6.0.5
        args: ["-s"] 
        imagePullPolicy: IfNotPresent
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
          value: "-Xms1500m -Xmx1500m" 
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
The same logic applies for the readiness probe which will notify K8 that the pod is ready. We could also use the 9042 port, but we might need a more advanced script to get our liveness/readiness information. 

Any script can be used, for example if we want to rely on the nodetool status of the node we could do the following:

```bash
#!/bin/bash

if [[ $(nodetool status | grep $POD_IP) == *"UN"* ]]; then
  if [[ $DEBUG ]]; then
    echo "UN";
  fi
  exit 0;
else
  if [[ $DEBUG ]]; then
    echo "Not Up";
  fi
  exit 1;
fi
```
This script requests the $POD_IP env variable to be set. We can get it from  `fieldRef.fieldPath: status.podIP`: (see below)


```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: dse
  labels:
    app: dse
spec:
  serviceName: dse-service
  replicas: 3 
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
      containers:
      - name: dse
        image: datastax/dse-server:6.0.5
        args: ["-s"] 
        imagePullPolicy: IfNotPresent
        readinessProbe:
          exec:
            command:
            - /bin/bash
            - -c
            - /ready-probe.sh
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
          value: "-Xms1500m -Xmx1500m" 
         - name: POD_IP #We assign the POD_IP var 
            valueFrom:
              fieldRef:
                fieldPath: status.podIP  #the value will be dynamically generated
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
