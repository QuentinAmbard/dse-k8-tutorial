# Accessing DSE from inside the cluster

Accessing a DSE node from within the cluster is quite simple. Just put the node hostname as contact point: `<statefulName>-X.<serviceName>.default.svc.cluster.local` (ex: `dse-0.dse-service.default.svc.cluster.local`). 

If you are unsure you can get the value from  `kubectl exec -it dse-0 -- cat /etc/hosts`