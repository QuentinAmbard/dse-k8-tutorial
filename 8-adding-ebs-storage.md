# Add ebs to our simple application

Having storage on our the local filesystem is probably not what we want to do at a larger scale. 

We need to provide independent volume to each of our pod. Because we are on AWS, we'll be using AWS EBS storage. 

Remember that 1 EBS can only be mount to a sing le pod, and EBS AZ need to match the server AZ (see multi-region.md for more details).

Defining AWS as cloud provider can be quite challenging and won't be covered here (more details: https://blog.scottlowe.org/2018/09/28/setting-up-the-kubernetes-aws-cloud-provider/).

Since we are using kops, our cluster should be properly configured.

Check your default storage class. Because we used kops, our cluster is automatically configured for AWS with gp2 as our default storage class.

```bash
kubectl get storageclass
NAME            PROVISIONER             AGE
default         kubernetes.io/aws-ebs   41m
gp2 (default)   kubernetes.io/aws-ebs   41m
```

##Mounting an EBS in a pod 
We first need to create a Persistent Volume Claim (pvc)

In this case, we'll create a pvc for a 10Gi disk:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
 name: pvc-10-fast
 annotations:
   volume.beta.kubernetes.io/storage-class: gp2
spec:
 accessModes:
  - ReadWriteOnce
 resources:
   requests:
     storage: 10Gi
```

Because our default storage class is dynamic, we don't have to manually create a Persistent Volume. If no volume match the existing claim, K8s will automatically create an ebs matching our pvc specification.

Our pvc creation also made a pv:
```bash
kubectl get pvc
NAME          STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
pvc-10-fast   Bound    pvc-d589ef38-2ed1-11e9-a0cd-067bec49b73a   10Gi       RWO            gp2            23s
kubectl get pv
NAME                                       CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                 STORAGECLASS   REASON   AGE
pvc-d589ef38-2ed1-11e9-a0cd-067bec49b73a   10Gi       RWO            Delete           Bound    default/pvc-10-fast   gp2                     14s
```

Let's create a simple pod using this pvs:

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
    image: 35.180.131.219:5000/k8-training/simple-webapp
    ports:
    - containerPort: 8080 #we expose the port our webapp is running on
    volumeMounts:
    - mountPath: "/var/www/html"
      name: test-ebs
  volumes:
    - name: test-ebs
      persistentVolumeClaim:
        claimName: pvc-10-fast

```
When we deploy our pod, the ebs is created and mount under `/var/www/html/`

```bash
kubectl create -f simple-app.yaml 
kubectl exec simple-app  -- ls /var/www/html/
```
If the container crashes, it'll be recreated with the same ebs.

Because our default reclaim policy is "Delete", deleting the pvc will also delete our pv (our ebs). Deleting the node only won't affect the pvc:

```bash
kubectl delete pod simple-app 
pod "simple-app" deleted
kubectl get pv
NAME                                       CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM                 STORAGECLASS   REASON   AGE
pvc-d589ef38-2ed1-11e9-a0cd-067bec49b73a   10Gi       RWO            Delete           Bound    default/pvc-10-fast   gp2                     6m
kubectl delete pvc pvc-10-fast 
kubectl get pv
No resources found.
```

Note: while deleting a pv, if it gets stuck in terminating STATUS, run `kubectl patch pv pvc-b0a084a6-2ecd-11e9-a0cd-067bec49b73a -p '{"metadata":{"finalizers":null}}'`

If you want to mount a specific EBS, you should manually create it before (using aws cli for ex) and mount it using a awsElasticBlockStore volume:

```bash
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
    image: 35.180.131.219:5000/k8-training/simple-webapp
    ports:
    - containerPort: 8080 #we expose the port our webapp is running on
    volumeMounts:
    - mountPath: "/var/www/html"
      name: test-ebs
  volumes:
    # This AWS EBS volume must already exist.
    awsElasticBlockStore:
      volumeID: <volume-id>
      fsType: ext4
```

Using PVC and PV for a single deployment is great (for example a MYSQL database). However, things get more complicated in a replica set.

Remember that an EBS can only be mounted in a single node (it only support a spec.accessMode.ReadWriteOnce setup). There is no way to mount a single EBS to all our pods as a "shared folder". If we try using the same configuration as the one we set in our pod.yaml file, the first pod will be created but the other will get stuck waiting since the PV is alread mount in one of the node.


##Note on permission
The process running inside your container needs to have enough permission to access your ebs.

For security reason, it's considered best practice to run pods using random user id, making the user id unpredictable.

You can either change the `template.spec.securityCotnext.fsGroup` to match your user group: 
```yaml
template:
  spec:
    securityContext:
      fsGroup: 999
```

Or in a pv or storageClass change the volume gid to match your app user, which is probably the best solution:  

```yaml
  annotations:
    pv.beta.kubernetes.io/gid: 999     
``` 

```yaml
  annotations:
    pv.beta.kubernetes.io/gid: 999     
``` 

or in a pv or storageClass
```yaml
  annotations:
    volume.beta.kubernetes.io/mount-options: "uid=1000,gid=1000"
```

or in a storage class / persistent volume:

```yaml
mountOptions: #these options
  - uid=1000
  - gid=1000
  - dir_mode=0777
  - file_mode=0777
```

See https://stackoverflow.com/questions/51390789/kubernetes-persistent-volume-claim-mounted-with-wrong-gid/51481107 for some example