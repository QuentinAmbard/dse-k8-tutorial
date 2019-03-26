# Create a docker image

The goal is to create a simple docker image containing a webapp do be able to deploy it on our k8 cluster.

## Build our app
go into ./simple-webapp, build the app and try to start it:

```bash
./mvnw package && java -jar target/gs-spring-boot-docker-0.1.0.jar
```

go to http://localhost:8080 to see your "Hello Docker World" message.

## Let's containerize it!

Docker has a unique format: Dockerfile.


```dockerfile
FROM openjdk:8-jdk-alpine
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

we can now build the docker image with docker

```bash
docker build -t simple-app --build-arg JAR_FILE=target/gs-spring-boot-docker-0.1.0.jar . 
```

or also build it using the maven plugin:
```bash
./mvnw install dockerfile:build 
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
Let's deploy our image to a registry to be able to access from anywhere.  To keep it simple, we'll start a local registry on our kubernetes master node. (We could push it to Dockerhub )

Connect to the EC2 k8 master and run a registry:

```bash
sudo iptables -N DOCKER
docker run -d -p 5000:5000 --restart=always --name registry registry:2
```
Change your AWS security group to allow access on port 5000 (our registry).

You can now push a local image to your registry:
```bash
#create an image with a proper tag:
docker tag simple-webapp <registryIP>:5000/k8-training/simple-webapp
docker push <registryIP>:5000/k8-training/simple-webapp
```
If you get the following error: `Get https://<registryIP>:5000/v2/: http: server gave HTTP response to HTTPS client`, you need to allow your client to talk with the registry without TLS:

```bash
sudo vim /etc/docker/daemon.json
{ "insecure-registries":["<registryIP>:5000"] }
sudo service docker restart
```
Make sure the image is properly deployed:

```bash
curl -X GET http://<registryIP>:5000/v2/_catalog 
{"repositories":["k8-training/simple-webapp"]}
curl -X GET http://<registryIP>:5000/v2/k8-training/simple-webapp/tags/list
{"name":"k8-training/simple-webapp","tags":["latest"]}
```
