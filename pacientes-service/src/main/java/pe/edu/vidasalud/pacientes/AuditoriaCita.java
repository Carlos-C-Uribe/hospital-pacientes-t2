package pe.edu.vidasalud.pacientes;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("auditoria_cita")
public record AuditoriaCita(@Id Long id, UUID eventoId, Long citaId, String codigoCita,
        Long pacienteId, String tipo, String estadoAnterior, String estadoNuevo,
        OffsetDateTime fechaHora, OffsetDateTime fechaEvento, OffsetDateTime fechaConsumo,
        String origen) {}

