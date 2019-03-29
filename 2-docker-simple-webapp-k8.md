# Create a docker image

The goal is to create a simple docker image containing a webapp do be able to deploy it on our k8 cluster.

## Build our app
Install maven and git
```bash
sudo yum install java-1.8.0 maven git -y
git clone https://github.com/QuentinAmbard/dse-k8-tutorial.git

cd /usr/local/src
wget http://www-us.apache.org/dist/maven/maven-3/3.5.4/binaries/apache-maven-3.5.4-bin.tar.gz
tar -xf apache-maven-3.5.4-bin.tar.gz
mv apache-maven-3.5.4/ apache-maven/
echo "export M2_HOME=/usr/local/src/apache-maven" >> ~/.bashrc
echo "export PATH=${M2_HOME}/bin:${PATH}" >> ~/.bashrc
source ~/.bashrc
cd
/usr/local/src/apache-maven/bin/mvn -version
```

Install docker: start by removing any existing installation:
```bash
sudo yum install -y htop vim curl
sudo yum remove docker docker-common docker-selinux docker-engine
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
```
redhat isn’t supported by docker CE, we need to install container selinux first
```bash
#sudo yum install -y http://mirror.centos.org/centos/7/extras/x86_64/Packages/container-selinux-2.74-1.el7.noarch.rpm
sudo yum install -y docker-ce
sudo systemctl enable docker.service
sudo systemctl start docker


go into ./simple-webapp, build the app and try to start it:

```bash
cd dse-k8-tutorial/simple-webapp
/usr/local/src/apache-maven/bin/mvn package && java -jar target/demo-simple-webapp-0.1.0.jar
```

go to http://localhost:8080 to see your "Hello Docker World" message.

## Let's containerize it!

Docker has a unique format: Dockerfile. Let's build our app from a `8-jdk-alpine` image:

```dockerfile
FROM openjdk:8-jdk-alpine
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

we can now build the docker image with docker

```bash
docker build -t simple-webapp --build-arg JAR_FILE=target/demo-simple-webapp-0.1.0.jar . 
```

or also build it using the maven plugin:
```bash
/usr/local/src/apache-maven/bin/mvn install dockerfile:build 
```

Let's see which image we now have available:

```dockerfile
docker image ls                                                                            
REPOSITORY            TAG                 IMAGE ID            CREATED             SIZE
simple-webapp         latest              7d1f7566b76e        7 minutes ago       121MB
openjdk               8-jdk-alpine        792ff45a2a17        3 days ago          105MB
```

Let's start the docker image. Our server is running on port 8080 so we need to allow it and map it to our local 8080 port

```bash
docker run -p 8080:8080 -t simple-webapp
```

go on http://localhost:8080 and make sur the app is running !

Let's see which container is running:
```bash
docker ps
CONTAINER ID        IMAGE               COMMAND                CREATED             STATUS              PORTS                    NAMES
5fddfe39a2cc        simple-webapp       "java -jar /app.jar"   11 minutes ago      Up 11 minutes       0.0.0.0:8080->8080/tcp   practical_jackson
```

Tail your app logs:

```bash
docker logs -f 5fddfe39a2cc
```

Manually explore your container:
```bash
docker exec -t -i 5fddfe39a2cc /bin/sh
```
And finally stop it:

```bash
docker stop 5fddfe39a2cc
```

## Sending our image on a repository

For now the docker image is local.
Let's deploy our image to a registry to be able to access from anywhere.  To keep it simple, we'll be using the AWS ECR registry

We can now push the image to docker:
```bash
#sign in aws registry (ecr)
$(aws ecr get-login --no-include-email --region eu-west-2)
#create an image with a proper tag and push it:
docker tag simple-webapp:latest 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin
docker push 553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training:quentin
```



-------------------------------------------

-------------------------------------------

-------------------------------------------

EXTRA deploy local repo with docker: If you get the following error: `Get https://<registryIP>:5000/v2/: http: server gave HTTP response to HTTPS client`, you need to allow your client to talk with the registry without TLS:
      
```bash
sudo vim /etc/docker/daemon.json
{ "insecure-registries":["<registryIP>:5000"] }
sudo service docker restart
```

Make sure the image is properly deployed:

```bash
curl -X GET http:553261234129.dkr.ecr.eu-west-2.amazonaws.com/k8s-training/v2/_catalog 
{"repositories":["k8-training/simple-webapp"]}
curl -X GET http://<registryIP>:5000/v2/k8-training/simple-webapp/tags/list
{"name":"k8-training/simple-webapp","tags":["latest"]}
```
