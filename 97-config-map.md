#Config map for Custom configuration
The main configuration of the cassandra.yaml configuration file can be changed using the docker images environment properties, for example:
```yaml
- name: CLUSTER_NAME
  value: "My_Cluster"
- name: NUM_TOKENS
  value: "8" 
```
However, you'll most likely have to change more advanced configuration in other files (dse.yaml, spark configuration etc). The best way to edo