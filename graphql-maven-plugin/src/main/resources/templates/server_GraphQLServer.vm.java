package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author generated by graphql-maven-plugin
 * @See https://github.com/graphql-java-generator/graphql-java-generator
 */
@SpringBootApplication
@EnableConfigurationProperties
public class GraphQLServer {

	public static void main(String[] args) {
		SpringApplication.run(GraphQLServer.class, args);
	}

}