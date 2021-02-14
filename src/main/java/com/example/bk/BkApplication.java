package com.example.bk;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;


@SpringBootApplication(proxyBeanMethods = false)
public class BkApplication {

	@Bean
	@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
	ApplicationListener<ApplicationReadyEvent> runningInKubernetes() {
		return event -> System.out.println("Hello, Kubernetes!");
	}

	@Bean
	RouterFunction<ServerResponse> routes(ApplicationContext context,
																																							CustomerRepository cr) {
		return route()
			.GET("/customers", request -> ok().body(cr.findAll(), Customer.class))
			.GET("/slow/{v}", request -> {
				var v = request.pathVariable("v");
				var slowString = Flux.just(1, 2, 3).map(Object::toString)
					.map(ns -> ns + " for " + v).delayElements(Duration.ofSeconds(5));
				return ok().contentType(MediaType.TEXT_EVENT_STREAM).body(slowString, String.class);
			})
			.POST("/down", serverRequest -> {
				AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);
				return ServerResponse.ok().body(Mono.empty(), Void.class);
			})
			.build();
	}

	@EventListener
	public void availabilityChangeEvent(AvailabilityChangeEvent<?> ace) {
		System.out.println(
			Objects.requireNonNull(ace.getResolvableType()) + ":" +
				ace.getState().toString());
	}


	@Bean
	ApplicationListener<ApplicationReadyEvent> ready(
		DatabaseClient dbc,
		CustomerRepository customerRepository) {
		return event -> {

			var ddl = dbc
				.sql(
					"create table customer(id serial primary key not null, name varchar(255) not null)")
				.fetch()
				.rowsUpdated();

			var saved = Flux
				.just("A", "B", "C", "D")
				.map(name -> new Customer(null, name))
				.flatMap(customerRepository::save);

			ddl.thenMany(saved).thenMany(customerRepository.findAll()).subscribe(System.out::println);

		};
	}

	public static void main(String[] args) {
		SpringApplication.run(BkApplication.class, args);
	}
}


interface CustomerRepository extends ReactiveCrudRepository<Customer, Integer> {

}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Customer {

	@Id
	private Integer id;
	private String name;
}