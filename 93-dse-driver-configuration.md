# Accessing DSE from inside the cluster

Accessing a DSE node from within the cluster is quite simple. Just put the node hostname as contact point: `<statefulName>-X.<serviceName>.default.svc.cluster.local` (ex: `dse-0.dse-service.default.svc.cluster.local`). 

If you are unsure you can get the value from  `kubectl exec -it dse-0 -- cat /etc/hosts`

# Accessing DSE from the outside

Accessing the node from outside the cluster is challenging. 

The easiest solution is to pin the dse instance to a specific host and use this host as contact point.

The dse port can then be exposed to the host with the `hostPort: 9042` instruction.

A NodePort service with `spec.externalTrafficPolicy: Local` can also be used instead:

```bash
apiVersion: v1
kind: Service
metadata:
  name: broker
spec:
  externalTrafficPolicy: Local
  ports:
  - nodePort: 30000
    port: 30000
    protocol: TCP
    targetPort: 9092
  selector:
    app: broker
  type: NodePort
```

in order to pin dse to a restricted set of k8 host, put labels on the nodes: 

```bash
kubectl label nodes <node-name> <label-key>=<label-value>
```
