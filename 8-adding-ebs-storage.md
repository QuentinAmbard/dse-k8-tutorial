# Add ebs to our simple application

Having storage on our the local filesystem is probably not what we want to do at a larger scale. 

We need to provide independent volume to each of our pod. Because we are on AWS, we'll be using AWS EBS storage. 

Remember that 1 EBS can only be mount to a sing le pod, and EBS AZ need to match the server AZ.

Defining AWS as cloud provider can be quite challenging and won't be covered here (more details: https://blog.scottlowe.org/2018/09/28/setting-up-the-kubernetes-aws-cloud-provider/).

##1: install kops
We'll be installing kubernetes using kops instead. Follow https://github.com/kubernetes/kops/blob/master/docs/install.md to install kops.

Notes: details on aws specific config: https://github.com/kubernetes/kops/blob/master/docs/aws.md

##2: Install aws cli


```bash
sudo yum install vim wget htop
wget http://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo rpm -ivh epel-release-latest-7.noarch.rpm
sudo yum install python36 python36-setuptools
sudo easy_install-3.6 pip
pip3 install awscli --upgrade --user
#configure aws cli with a secret:
aws configure
```
You might need to add aws in your PATH: `vim ~/.bashrc` => `export PATH=~/.local/bin/:$PATH`

##3: Configure and run kops

```bash
aws s3api create-bucket     --bucket qa11-kops-state-store     --region eu-west-3  --create-bucket-configuration LocationConstraint=eu-west-3
aws s3api put-bucket-versioning --bucket qa11-kops-state-store --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket qa11-kops-state-store --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
export NAME=k8cluster.k8s.local
export KOPS_STATE_STORE=s3://qa11-kops-state-store

#make sure you have a key we'll be using to access the nodes, if not create it with ssh-keygen -b 2048 -t rsa
kops create secret --name k8cluster.k8s.local sshpublickey admin -i ~/.ssh/id_rsa.pub
kops create cluster --zones eu-west-3a  --networking flannel-vxlan --node-count 5 --node-size t2.medium --master-size t2.small ${NAME}
kops update cluster k8cluster.k8s.local --yes
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/bc79dd1505b0c8681ece4de4c0d86c5cd2643275/Documentation/kube-flannel.yml
```
NOTE: you might want to remove public access to the S3 bucket. 

NOTE: if something is wrong, to delete cluster, run `kops delete cluster k8cluster.k8s.local --yes`

Your cluster must now be starting. the kubectl is setup with your new kubernetes configuration:
```bash
kubectl config view
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: DATA+OMITTED
    server: https://xxxxxxxx
  name: k8cluster.k8s.local
contexts:
- context:
    cluster: k8cluster.k8s.local
    user: k8cluster.k8s.local
  name: k8cluster.k8s.local
current-context: k8cluster.k8s.local
kind: Config
preferences: {}
users:
- name: k8cluster.k8s.local
  user:
    client-certificate-data: REDACTED
    client-key-data: REDACTED
    password: xxxxxxxx
    username: xxxxxxxx
- name: k8cluster.k8s.local-basic-auth
  user:
    password: xxxxxxxx
    username: xxxxxxxx

```
Check its state with 
```bash
kubectl get nodes --show-labels
kubectl get nodes --show-labels
NAME                                          STATUS   ROLES    AGE   VERSION   LABELS
ip-172-20-34-65.eu-west-3.compute.internal    Ready    node     41s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-34-65.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-45-192.eu-west-3.compute.internal   Ready    node     38s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-45-192.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-46-179.eu-west-3.compute.internal   Ready    master   1m    v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.small,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=master-eu-west-3a,kubernetes.io/hostname=ip-172-20-46-179.eu-west-3.compute.internal,kubernetes.io/role=master,node-role.kubernetes.io/master=
ip-172-20-54-24.eu-west-3.compute.internal    Ready    node     38s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-54-24.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-55-211.eu-west-3.compute.internal   Ready    node     42s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-55-211.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-63-206.eu-west-3.compute.internal   Ready    node     44s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-63-206.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
```


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