package pe.edu.vidasalud.citas;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;

class PacienteClientTest {
    PacienteClient cliente(HttpStatus status, String body) {
        var http = WebClient.builder().baseUrl("http://pacientes-service")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                        .header("Content-Type", "application/json").body(body).build())).build();
        return new PacienteClient(http, Duration.ofSeconds(3));
    }

    @Test
    void aceptaContratoDePacienteActivo() {
        StepVerifier.create(cliente(HttpStatus.OK, "{\"id\":1,\"estado\":\"ACTIVO\",\"nombres\":\"Paciente ficticio\",\"version\":0}").validarActivo(1))
                .assertNext(p -> assertEquals(1L, p.id())).verifyComplete();
    }

    @Test
    void pacienteInexistenteDevuelve404() {
        StepVerifier.create(cliente(HttpStatus.NOT_FOUND, "{}").validarActivo(1))
                .expectErrorSatisfies(e -> assertEquals("PACIENTE_NO_ENCONTRADO", ((ReglaException) e).codigo()))
                .verify();
    }

    @Test
    void pacienteInactivoDevuelve409() {
        StepVerifier.create(cliente(HttpStatus.OK, "{\"id\":1,\"estado\":\"INACTIVO\"}").validarActivo(1))
                .expectErrorSatisfies(e -> assertEquals(409, ((ReglaException) e).status().value()))
                .verify();
    }

    @Test
    void servicioConErrorDevuelve503SinFiltrarSuRespuesta() {
        StepVerifier.create(cliente(HttpStatus.INTERNAL_SERVER_ERROR, "{\"mensaje\":\"detalle interno\"}").validarActivo(1))
                .expectErrorSatisfies(e -> {
                    assertEquals(503, ((ReglaException) e).status().value());
                    assertFalse(e.getMessage().contains("detalle interno"));
                }).verify();
    }

    @Test
    void tiempoDeEsperaDevuelve503() {
        var http = WebClient.builder().baseUrl("http://pacientes-service")
                .exchangeFunction(request -> Mono.never()).build();
        var client = new PacienteClient(http, Duration.ofSeconds(3));
        StepVerifier.withVirtualTime(() -> client.validarActivo(1))
                .thenAwait(Duration.ofSeconds(4))
                .expectErrorSatisfies(e -> assertEquals(503, ((ReglaException) e).status().value()))
                .verify();
    }

    @Test
    void respuestaConOtroIdentificadorNoValidaElPaciente() {
        StepVerifier.create(cliente(HttpStatus.OK, "{\"id\":2,\"estado\":\"ACTIVO\"}").validarActivo(1))
                .expectErrorSatisfies(e -> assertEquals(503, ((ReglaException) e).status().value()))
                .verify();
    }
}

