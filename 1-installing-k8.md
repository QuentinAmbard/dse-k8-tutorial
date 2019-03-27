# Install Kubernetes

## Easy install with KOPS

### 0: Start a new instance to run kubectl and kops

Go on AWS console, start a micro redhat instance (tag it with your name) and open SSH connection to this instance.

Open all ports in the instance security group as we'll use it for our docker registry

```bash
chmod 600 ~/Downloads/quentin_training.pem
ssh -i /home/quentin/Download/mykey root@<instanceIP>
``` 

### 1: install kops
We'll be installing kubernetes using kops. Follow https://github.com/kubernetes/kops/blob/master/docs/install.md

```bash
curl -Lo kops https://github.com/kubernetes/kops/releases/download/$(curl -s https://api.github.com/repos/kubernetes/kops/releases/latest | grep tag_name | cut -d '"' -f 4)/kops-linux-amd64
chmod +x ./kops
sudo mv ./kops /usr/local/bin/
```
Notes: details on aws specific config: https://github.com/kubernetes/kops/blob/master/docs/aws.md

### 2: Install kubect

```bash
curl -Lo kubectl https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/darwin/amd64/kubectl
chmod +x ./kubectl
sudo mv ./kubectl /usr/local/bin/kubectl
```
Install kubectl autocompletion command 
```bash
yum install -y bash-completion
kubectl completion bash
```
### 3: Install aws cli


```bash
sudo yum install -y vim wget htop
wget http://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
sudo rpm -ivh epel-release-latest-7.noarch.rpm
sudo yum install -y python36 python36-setuptools
sudo easy_install-3.6 pip
pip3 install awscli --upgrade --user
#configure aws cli with a secret:
aws configure
```
You might need to add aws in your PATH: `echo "export PATH=~/.local/bin/:$PATH" >> ~/.bashrc && source ~/.bashrc`

### 4: Configure and run kops

!! You must set a unique cluster name and s3 bucket name. Make sure your cluster name ends by `.k8s.local` !!

```bash
export BUCKET_NAME=qa2-kops-state-store
export NAME=qa2-k8cluster.k8s.local
export KOPS_STATE_STORE="s3://$BUCKET_NAME"
aws s3api create-bucket     --bucket $BUCKET_NAME     --region eu-west-2  --create-bucket-configuration LocationConstraint=eu-west-2
aws s3api put-bucket-versioning --bucket $BUCKET_NAME --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket $BUCKET_NAME --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

#make sure you have a key we'll be using to access the nodes, if not create it with:
# ssh-keygen -b 2048 -t rsa && chmod 600
kops create secret --name k8cluster.k8s.local sshpublickey admin -i ~/.ssh/id_rsa.pub
kops create cluster --zones eu-west-3a  --networking flannel-vxlan --node-count 3 --node-size t2.medium --master-size t2.small ${NAME}
kops update cluster k8cluster.k8s.local --yes

```

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
NAME                                          STATUS   ROLES    AGE   VERSION   LABELS
ip-172-20-34-65.eu-west-3.compute.internal    Ready    node     41s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-34-65.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-45-192.eu-west-3.compute.internal   Ready    node     38s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-45-192.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-46-179.eu-west-3.compute.internal   Ready    master   1m    v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.small,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=master-eu-west-3a,kubernetes.io/hostname=ip-172-20-46-179.eu-west-3.compute.internal,kubernetes.io/role=master,node-role.kubernetes.io/master=
ip-172-20-54-24.eu-west-3.compute.internal    Ready    node     38s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-54-24.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-55-211.eu-west-3.compute.internal   Ready    node     42s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-55-211.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
ip-172-20-63-206.eu-west-3.compute.internal   Ready    node     44s   v1.11.7   beta.kubernetes.io/arch=amd64,beta.kubernetes.io/instance-type=t2.medium,beta.kubernetes.io/os=linux,failure-domain.beta.kubernetes.io/region=eu-west-3,failure-domain.beta.kubernetes.io/zone=eu-west-3a,kops.k8s.io/instancegroup=nodes,kubernetes.io/hostname=ip-172-20-63-206.eu-west-3.compute.internal,kubernetes.io/role=node,node-role.kubernetes.io/node=
```

Install flannel. TODO: check if it's not already setup with the `--networking flannel-vxlan`
```bash
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/bc79dd1505b0c8681ece4de4c0d86c5cd2643275/Documentation/kube-flannel.yml
```

## Manual installation
Start 3 CentOS instances on EC2 and start installing docker on your 3 instances:
Make sure the instances are within an open VPC (ex: All traffic in/out over 172.31.0.0/16)

Remove any existing installation:
```bash
sudo yum install -y htop vim curl
sudo yum remove docker docker-common docker-selinux docker-engine
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
```
redhat isn’t supported by docker CE, we need to install container selinux first
```bash
sudo yum install -y http://mirror.centos.org/centos/7/extras/x86_64/Packages/container-selinux-2.74-1.el7.noarch.rpm
sudo yum install -y docker-ce
sudo systemctl enable docker.service
sudo systemctl start docker
```

k8 won't let you run with swap on, disable it:
```bash
swapoff -a
```

Add k8 repository:
```bash
cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://packages.cloud.google.com/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
exclude=kube*
EOF
```

Set SELinux in permissive mode (effectively disabling it)
```bash
setenforce 0
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config
```

```bash
yum install -y kubeadm kubectl kubelet --disableexcludes=kubernetes
systemctl enable kubelet.service

```
Add autocompletion support:
```bash
yum install bash-completion -y
echo "source <(kubectl completion bash)" >> ~/.bashrc
source ~/.bashrc
```

## Setup the master

```bash
kubeadm init --pod-network-cidr=10.244.0.0/16
```
Keep the resulting command:
```bash
#kubeadm join 172.31.34.146:6443 --token jd6dtl.j9zygzw2ybjvihe2 --discovery-token-ca-cert-hash sha256:30faaadf54014fc9b73f212cf7781d858eb8401e9c8721b0f675ff1d80f96cda
```
make sure the kubelet service is running:
```bash
service kubelet status
kubectl get pods --all-namespaces
```

If you get the following error:
`The connection to the server localhost:8080 was refused - did you specify the right host or port?`
export the KUBECONFIG varenv:
```bash
export KUBECONFIG=/etc/kubernetes/admin.conf
```

Install a network add-on (required to start the CoreDNS). We'll be using flannel.

```bash
kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/bc79dd1505b0c8681ece4de4c0d86c5cd2643275/Documentation/kube-flannel.yml
```
 
## Make the nodes join the cluster
On the other nodes, run the command you get from the previous `kueadm init` command:
```bash
kubeadm join 172.31.34.146:6443 --token jd6dtl.j9zygzw2ybjvihe2 --discovery-token-ca-cert-hash sha256:30faaadf54014fc9b73f212cf7781d858eb8401e9c8721b0f675ff1d80f96cda
```
On the master, make sure the 3 nodes have joined the cluster before continuing:

```bash
kubectl get nodes
NAME                                          STATUS   ROLES    AGE    VERSION
ip-172-31-34-146.eu-west-3.compute.internal   Ready    master   22h    v1.13.0
ip-172-31-34-94.eu-west-3.compute.internal    Ready    <none>   87s    v1.13.1
ip-172-31-37-5.eu-west-3.compute.internal     Ready    <none>   103s   v1.13.1
```
