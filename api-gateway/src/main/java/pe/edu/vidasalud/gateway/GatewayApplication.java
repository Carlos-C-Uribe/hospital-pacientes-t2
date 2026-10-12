package pe.edu.vidasalud.gateway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
@SpringBootApplication
public class GatewayApplication {
 public static void main(String[] args) { SpringApplication.run(GatewayApplication.class,args); }
 @Bean RouteLocator rutas(RouteLocatorBuilder builder,
   @Value("${hospital.pacientes-uri:lb://pacientes-service}") String destino,
   @Value("${hospital.citas-uri:lb://citas-service}") String citas) {
  return builder.routes()
   .route("pacientes", r->r.path("/api/pacientes","/api/pacientes/**").uri(destino))
   .route("citas", r->r.path("/api/citas","/api/citas/**").uri(citas)).build();
 }
}
