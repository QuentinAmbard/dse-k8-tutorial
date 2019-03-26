# Running kubernetes on multiple DC (or multiple AWS regions)

Kubernetes has a concept of federation. However, the current version isn't working properly and the V2 is in active development. From the documentation:

```bash
Use of Federation v1 is strongly discouraged. Federation V1 never achieved GA status and is no longer under active development. Documentation is for historical purposes only.
https://kubernetes.io/docs/concepts/cluster-administration/federation/
```

One of the main issue for our DSE deployment is the network: internal POD ips aren't available/routable between each DC.
 
## Hacky solution

- Open network between the 2 DC in your security group
- Pin the DSE nodes to a specific host
- Set cluster name and seed nodes to a proper value 
- Use NodePort with `externalTrafficPolicy: Local` to expose the node from the outside
- Deploy the simple demo app in each cluster (use a `Deployment`). 
- Configure the app to execute QUORUM requests to ensure it's working properly

   Hint: the consistency level can be changed in the application using an env variable.
   
   Example using docker (change it accordingly in your app Deployment)

```bash
docker run -e demo.cassandra.contactPoint=192.168.43.239 -p 8080:8080 -t demo-app
``` 
 
Very good reading:
https://www.cockroachlabs.com/blog/experience-report-running-across-multiple-kubernetes-clusters/

