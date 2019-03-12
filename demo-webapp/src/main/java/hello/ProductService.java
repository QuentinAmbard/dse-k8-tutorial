package hello;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.policies.ConstantSpeculativeExecutionPolicy;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.dse.DseCluster;
import com.datastax.driver.dse.DseSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {
    private final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Value("${demo.cassandra.contactPoint}")
    private String contactPoint;

    @Value("${demo.cassandra.speculativeRetry}")
    private boolean speculativeRetry;

    @Value("${demo.cassandra.allowRemoteHosts}")
    private boolean allowRemoteHosts;

    @Value("${demo.cassandra.cl}")
    private String cl;

    @Value("${demo.cassandra.dc1}")
    private String dc1;

    @Value("${demo.cassandra.dc2}")
    private String dc2;

    private DseSession session = null;
    private PreparedStatement selectProductsSt;

    @PostConstruct
    public void startCassandraConnection() {
        QueryOptions qo = new QueryOptions().setConsistencyLevel(ConsistencyLevel.valueOf(cl));
        DseCluster.Builder cluster = DseCluster.builder().addContactPoint(contactPoint);
        log.info("----------------------------------------");
        log.info("Starting demo with:");
        log.info("contactPoint="+contactPoint);
        log.info("speculativeRetry="+speculativeRetry);
        log.info("CL="+cl);
        log.info("allowRemoteHosts="+allowRemoteHosts);
        log.info("----------------------------------------");
        if(speculativeRetry){
            qo.setDefaultIdempotence(true);
            cluster.withSpeculativeExecutionPolicy(new ConstantSpeculativeExecutionPolicy(500, 1));
        }
        if(allowRemoteHosts){
            cluster.withLoadBalancingPolicy(DCAwareRoundRobinPolicy.builder().allowRemoteDCsForLocalConsistencyLevel().withUsedHostsPerRemoteDc(2).build());
        }
        cluster.withQueryOptions(qo);
        session = cluster.build().connect();
        String dc2Command = "";
        if(dc2 != null && dc2.length()>0){
            dc2Command = ", '"+dc2+"': 1";
        }
        session.execute("CREATE KEYSPACE IF NOT EXISTS demo WITH replication = {'class': 'NetworkTopologyStrategy', '"+dc1+"': 1 "+dc2Command+"}");
        session.execute("CREATE TABLE IF NOT EXISTS demo.products (id int PRIMARY KEY, name text, price int)");
        session.execute("INSERT INTO demo.products (id, name, price) values (1, 'iphone X', 1199)");
        session.execute("INSERT INTO demo.products (id, name, price) values (2, 'Samsung S9', 800)");
        selectProductsSt = session.prepare("select * from demo.products");
    }

    public List<Product> getTasks() {
        List<Product> tasks = new ArrayList<>();
        for (Row r : session.execute(selectProductsSt.bind())){
            tasks.add(new Product(r.getInt("id"), r.getString("name"), r.getInt("price")));
        }
        return tasks;
    }

}
