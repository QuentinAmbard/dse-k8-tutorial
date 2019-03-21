# Running kubernets on multiple AZ

## Using kubernetes selector spread priority and downwardAPI
### 1 Kubernetes Region/zone

Kubernetes can handle multiple AZ in a same region using SelectorSpreadPriority.

It uses the following labels to spread across failure domain:

```yaml
failure-domain.beta.kubernetes.io/region
failure-domain.beta.kubernetes.io/zone
```
 
The `SelectorSpreadPriority` will then try to spread the replicas across zones in a best effort mode.

Thanksfully, the Partition Volume Claim are aware of zone. Once a pv is mounted in a specific region, it won't be re-mounted in another one:

```bash
kubectl get pv --show-labels
NAME           CAPACITY   ACCESSMODES   RECLAIM POLICY   STATUS    CLAIM            STORAGECLASS    REASON    AGE       LABELS
pv-gce-mj4gm   5Gi        RWO           Retain           Bound     default/claim1   manual                    46s       failure-domain.beta.kubernetes.io/region=us-central1,failure-domain.beta.kubernetes.io/zone=us-central1-a
```

More details: 

https://kubernetes.io/docs/setup/multiple-zones/

https://kubernetes.io/docs/reference/kubernetes-api/labels-annotations-taints/

### 2 define DSE rack using the downwardAPI
Now that our volumes are aware of the region, we need to find a way to share the region to the POD to be able to mark the nodes as being part of a specific RACK or DATACENTER.

We want the nodes from `us-central1-a` to have `RACK=us-central1-a` in our `cassandra-rackdc.properties` file.

This can be done using the downwardAPI. The downwardapivolumefile can expose the host labels and annotations.

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: kubernetes-downwardapi-volume-example
  labels:
    zone: us-est-coast
    cluster: test-cluster1
    rack: rack-22
  annotations:
    build: two
    builder: john-doe
spec:
  containers:
    - name: simple-app
      image: 35.180.131.219:5000/k8-training/simple-webapp
      volumeMounts:
        - name: podinfo
          mountPath: /etc/podinfo
          readOnly: false
  volumes:
    - name: podinfo
      downwardAPI:
        items:
          - path: "labels"
            fieldRef:
              fieldPath: metadata.labels
          - path: "annotations"
            fieldRef:
              fieldPath: metadata.annotations
```

now we can start the pod, the labels and annotations will be saved in their respective path:

```bash
kubectl exec -it kubernetes-downwardapi-volume-example -- cat /etc/podinfo/labels
cluster="test-cluster1"
rack="rack-22"
zone="us-est-coast"

kubectl exec -it kubernetes-downwardapi-volume-example -- cat /etc/podinfo/annotations
build="two"
builder="john-doe"
```

With this information, we can now adapt the `entrypoint.sh` script and read the `/etc/podinfo/xxx` to determine the rack before starting the DSE server.

More details: https://kubernetes.io/docs/tasks/inject-data-application/downward-api-volume-expose-pod-information/#the-downward-api

#Using multiple deployment
Another way to deploy across multiple Zones would be to use multiple deployment.

A helm chart could be created and the rack could be defined in a helm template. We would have 1 deployment per rack, merge in a single cluster/datacenter.

This solution also has an impact on things like pod disruption budget etc.