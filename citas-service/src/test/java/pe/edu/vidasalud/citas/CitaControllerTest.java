package pe.edu.vidasalud.citas;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;

class CitaControllerTest {
    CitaService service;
    WebTestClient http;

    @BeforeEach
    void preparar() {
        service = mock(CitaService.class);
        http = WebTestClient.bindToController(new CitaController(service))
                .controllerAdvice(new ApiErrors()).build();
    }

    @Test
    void crearDevuelve201YLocation() {
        var fecha = OffsetDateTime.parse("2027-01-04T10:00:00-05:00");
        var request = new CitaRequest(1L, "Medicina general", fecha, "DEMO-MEDICO01", "Reserva ficticia");
        var c = Cita.nueva(request, fecha.minusDays(1));
        var guardada = new Cita(1L, c.codigo(), 1L, c.especialidad(), c.fechaHora(),
                c.profesionalCodigo(), c.motivo(), c.estado(), c.fechaCreacion(), c.fechaActualizacion(), 0L);
        when(service.crear(any())).thenReturn(Mono.just(guardada));
        http.post().uri("/api/citas").bodyValue(request).exchange()
                .expectStatus().isCreated().expectHeader().valueEquals("Location", "/api/citas/1")
                .expectBody().jsonPath("$.estado").isEqualTo("PENDIENTE");
    }

    @Test
    void datosObligatoriosAusentesDevuelven400() {
        http.post().uri("/api/citas").header("Content-Type", "application/json")
                .bodyValue("{}").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
        verifyNoInteractions(service);
    }

    @Test
    void estadoDesconocidoDevuelve400() {
        http.patch().uri("/api/citas/1/estado").header("Content-Type", "application/json")
                .bodyValue("{\"estado\":\"OTRO\"}").exchange().expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }

    @Test
    void citaInexistenteDevuelve404ConCodigo() {
        when(service.obtener(99)).thenReturn(Mono.error(ReglaException.inexistente()));
        http.get().uri("/api/citas/99").exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.codigo").isEqualTo("CITA_NO_ENCONTRADA");
    }
}

