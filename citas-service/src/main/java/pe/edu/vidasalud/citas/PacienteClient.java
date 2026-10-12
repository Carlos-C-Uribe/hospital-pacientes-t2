package pe.edu.vidasalud.citas;

import java.time.Duration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class PacienteClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Referencia(Long id, String estado) {}

    private final WebClient http;
    private final Duration timeout;

    public PacienteClient(@Qualifier("pacientesWebClient") WebClient http,
            @Value("${hospital.pacientes.timeout:3s}") Duration timeout) {
        this.http = http;
        this.timeout = timeout;
    }

    public Mono<Referencia> validarActivo(long id) {
        return Mono.defer(() -> http.get().uri("/api/pacientes/{id}", id)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return response.releaseBody().then(Mono.error(new ReglaException(
                                HttpStatus.NOT_FOUND, "PACIENTE_NO_ENCONTRADO", "El paciente no existe.")));
                    }
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().then(Mono.error(indisponible()));
                    }
                    return response.bodyToMono(Referencia.class);
                })
                .switchIfEmpty(Mono.error(indisponible()))
                .timeout(timeout)
                .flatMap(paciente -> {
                    if (paciente.id() == null || paciente.id() != id
                            || !("ACTIVO".equals(paciente.estado()) || "INACTIVO".equals(paciente.estado()))) {
                        return Mono.error(indisponible());
                    }
                    if (!"ACTIVO".equals(paciente.estado())) {
                        return Mono.error(new ReglaException(HttpStatus.CONFLICT,
                                "PACIENTE_INACTIVO", "El paciente debe estar ACTIVO."));
                    }
                    return Mono.just(paciente);
                })
                .onErrorMap(error -> !(error instanceof ReglaException), error -> indisponible()));
    }

    private static ReglaException indisponible() {
        return new ReglaException(HttpStatus.SERVICE_UNAVAILABLE, "PACIENTES_NO_DISPONIBLE",
                "No se pudo validar al paciente. Vuelva a intentarlo mas tarde.");
    }
}

