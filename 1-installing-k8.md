# Install Kubernetes

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

##Setup the master

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
 
##Make the nodes join the cluster
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
