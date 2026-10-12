package pe.edu.vidasalud.pacientes;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitasAuditoriaConsumerTest {
    CitasAuditoriaConsumer.EventoCita evento(String origen, String estado) {
        return new CitasAuditoriaConsumer.EventoCita(1, UUID.randomUUID(), "CitaConfirmada",
                1L, "CITA-DEMO", 1L, "PENDIENTE", estado,
                OffsetDateTime.parse("2027-01-04T10:00:00-05:00"), OffsetDateTime.now(), origen);
    }

    @Test void aceptaContratoDeCitaConfirmada() {
        assertDoesNotThrow(() -> CitasAuditoriaConsumer.validarContrato(evento("citas-service", "CONFIRMADA")));
    }

    @Test void rechazaOrigenDesconocido() {
        assertThrows(IllegalArgumentException.class,
                () -> CitasAuditoriaConsumer.validarContrato(evento("otro-service", "CONFIRMADA")));
    }

    @Test void rechazaEstadoQueNoCorrespondeAlEvento() {
        assertThrows(IllegalArgumentException.class,
                () -> CitasAuditoriaConsumer.validarContrato(evento("citas-service", "CANCELADA")));
    }
}

