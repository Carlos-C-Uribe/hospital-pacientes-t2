package pe.edu.vidasalud.gateway;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIT {
 static HttpServer destino;
 static {
  try {
   destino=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   destino.createContext("/api/pacientes",exchange->{
    byte[] body="[{\"id\":1,\"nombres\":\"Paciente de prueba\"}]".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type","application/json");
    exchange.sendResponseHeaders(200,body.length);
    try(var out=exchange.getResponseBody()) { out.write(body); }
   });
   destino.createContext("/api/citas",exchange->{
    byte[] body="[{\"id\":1,\"estado\":\"PENDIENTE\"}]".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type","application/json");
    exchange.sendResponseHeaders(200,body.length);
    try(var out=exchange.getResponseBody()) { out.write(body); }
   });
   destino.start();
  } catch(Exception e) { throw new IllegalStateException(e); }
 }
 @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
  r.add("hospital.pacientes-uri",()->"http://127.0.0.1:"+destino.getAddress().getPort());
  r.add("hospital.citas-uri",()->"http://127.0.0.1:"+destino.getAddress().getPort());
  r.add("eureka.client.enabled",()->false);
 }
 @Value("${local.server.port}") int port;
 @Test void gatewayConservaLaRutaYEnrutaLaSolicitud() {
  WebTestClient.bindToServer().baseUrl("http://localhost:"+port).build()
   .get().uri("/api/pacientes").exchange().expectStatus().isOk().expectBody().jsonPath("$[0].id").isEqualTo(1);
 }
 @Test void gatewayEnrutaCitas() {
  WebTestClient.bindToServer().baseUrl("http://localhost:"+port).build()
   .get().uri("/api/citas").exchange().expectStatus().isOk().expectBody().jsonPath("$[0].estado").isEqualTo("PENDIENTE");
 }
 @AfterAll static void cerrar() { destino.stop(0); }
}
