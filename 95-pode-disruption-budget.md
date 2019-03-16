# Pod disruption budget

An Application Owner can create a PodDisruptionBudget object (PDB) for each application. 

A PDB limits the number pods of a replicated application that are down simultaneously from voluntary disruptions. 

For example, with Cassandra we need to ensure that the number of replicas running is never brought below the number needed for a quorum.

If we are running 1 DC with RF 3 at LOCAL_QUORUM, we can only afford to lose 1 node, so we'll set `maxUnavailable: 1`

```yaml
apiVersion: policy/v1beta1
kind: PodDisruptionBudget
metadata:
  name: dse-pdb
spec:
  maxUnavailable: 1
  selector:
    matchLabels:
      app: dse
```

If we run 2 DC, we could create 1 PDB per DC, for example:


```yaml
apiVersion: policy/v1beta1
kind: PodDisruptionBudget
metadata:
  name: dse-pdb-dc1
spec:
  maxUnavailable: 1
  selector:
    matchLabels:
      app: dse
      dc: dc1 
          
---

apiVersion: policy/v1beta1
kind: PodDisruptionBudget
metadata:
  name: dse-pdb-dc2
spec:
  maxUnavailable: 1
  selector:
    matchLabels:
      app: dse
      dc: dc2 
```

We are now protected if an administrator tries for example to drain 1 or multiples k8s node to perform an operation on the host:

```bash
kubectl get nodes
...
kubectl cordon <node1 with dse running> 
kubectl drain <node1 with dse running>
kubectl cordon <node2 with dse running> 
kubectl drain <node2 with dse running>
#==> The second drain will block as long as the dse pod from node1 isn't restarted and running somewhere else

...
kubectl uncordon <node1 with dse running>
kubectl uncordon <node2 with dse running>
```

