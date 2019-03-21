# Demo webapp.

mvn compile jib:build

Start the demo server with custom contactpoints:

java -Ddemo.cassandra.contactPoint=localhost -Ddemo.cassandra.speculativeRetry=false -Ddemo.cassandra.allowRemoteHosts=false -Ddemo.cassandra.dc1=datacenter1 -Ddemo.cassandra.cl=LOCAL_QUORUM -jar ./target/demo-webapp-0.1.0.jar

java -Ddemo.cassandra.contactPoint=xxxxx -Ddemo.cassandra.speculativeRetry=false -Ddemo.cassandra.allowRemoteHosts=false -Ddemo.cassandra.dc1=dc1 -Ddemo.cassandra.dc2=dc2 -Ddemo.cassandra.cl=LOCAL_QUORUM -jar ./target/demo-webapp-0.1.0.jar

docker run -e demo.cassandra.contactPoint=192.168.43.239 --net host -t demo-app

docker run -e demo.cassandra.contactPoint=192.168.43.239 -p 8080:8080 -t demo-app
