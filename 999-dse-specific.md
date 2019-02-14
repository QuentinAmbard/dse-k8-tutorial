#DSE specific 

- TPC configuration: Cassandra node will be using the host number of cpu by default (not k8s aware). Set it manually to on cassandra.yaml to the k8 `spec.containers.resources.limit.cpu` value, to be tested !
- Be careful of the other OS tuning settings. for example if Search is used, readahead need to be set on the k8 node, but might be also be required on the host too. (Need to be tested)
- DSE 3.x driver don't detect a node restart with a different IP address. The issue is fixed in 4.0. Kill the app and restart it if using driver <4.0.0
- Using Opscenter might be challenging when a node is being restarted with a different IP address.
- Opscenter LCM cannot be used, it's against k8 principle and won't work well.
- Backup strategy should be executed with a custom script launched in a K8s cron job (https://kubernetes.io/docs/tasks/job/automated-tasks-with-cron-jobs/)
- Don't stack multiple DSE node on a single k8 node. There will be issues with port setting. Don't do overbook the instances.
- It's probably best to maintain a separate DSE image with specific requirement (fork DSE git). Create an operator, helm or or use an image with configuration as environment variable.
- TODO: add PodDisruptionBudget
- If the node is accessed from the outside of the cluster, you'll need a headless server and expose the DSE port. 
    - 8888:  opscenter web ui *http* requests, OC server side
    - 8443:  opscenterd daemon for SSL *https* requests, OC server side
    - 25:    SMTP server to send email alerts, OC server side
    - 465:   SSL SMTP for email alerts, OC server side
    - 7199:  JMX monitoring that connects to DSE nodes, DSE nodes agent side
    - 9042:  ubiquitous DataStax *common* native_transport_port, *ALL* nodes everywhere
    - 7000:  ubiquitous DataStax *common* native_transport_port, *ALL* nodes everywhere
    - 61619: OpsCenter 'stomp' port used by OC to talk to itself, OC server side
    - 61620: DSE node agents talking *in* to the OC node, OC server side
    - 61621: OC talking *out* to the DSE node agents, DSE nodes agent side
    - 22:    Standard SSH port
    - 389:   non-SSL LDAP/AD traffic out to LDAP/AD server, OC server side
    - 636:   SSL  LDAP/AD traffic out to LDAP/AD server, OC server side
    - 7080:  OC talking out to DSE nodes for Spark job inspection, OC server side
    - 7081:  OC talking out to 3rd party apps performing Spark job inspection, OC server side
    - 162:   OC talking out to SNMP targets, OC server side
    - 2003:  OC talking out to "Graphite" consumer app
    
    
    
TODO: can't run command on statefulset?