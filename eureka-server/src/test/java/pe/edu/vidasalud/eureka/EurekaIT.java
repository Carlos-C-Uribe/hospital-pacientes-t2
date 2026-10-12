package pe.edu.vidasalud.eureka;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertEquals;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaIT {
 @Value("${local.server.port}") int port;
 @Test void servidorEurekaArranca() throws Exception {
  var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/")).GET().build();
  var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
  assertEquals(200,response.statusCode());
 }
}
