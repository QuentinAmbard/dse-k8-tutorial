package hello;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@SpringBootApplication
@RestController
public class Application {

    @Autowired
    private ProductService taskService;

    @RequestMapping("/products")
    public List<Product> getAllproducts() {
        return taskService.getTasks();
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
