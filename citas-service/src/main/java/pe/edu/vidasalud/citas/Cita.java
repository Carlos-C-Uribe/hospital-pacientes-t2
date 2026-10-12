package pe.edu.vidasalud.citas;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Table("cita")
public record Cita(
        @Id Long id, String codigo, Long pacienteId, String especialidad,
        OffsetDateTime fechaHora, String profesionalCodigo, String motivo,
        Estado estado, OffsetDateTime fechaCreacion, OffsetDateTime fechaActualizacion,
        @Version Long version) {

    public static final ZoneOffset ZONA = ZoneOffset.ofHours(-5);

    public enum Estado { PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA, AUSENTE }

    public Cita {
        if (fechaHora != null) fechaHora = fechaHora.withOffsetSameInstant(ZONA);
        if (fechaCreacion != null) fechaCreacion = fechaCreacion.withOffsetSameInstant(ZONA);
        if (fechaActualizacion != null) fechaActualizacion = fechaActualizacion.withOffsetSameInstant(ZONA);
    }

    public static Cita nueva(CitaRequest request, OffsetDateTime ahora) {
        return new Cita(null, "CITA-" + UUID.randomUUID(), request.pacienteId(),
                request.especialidad().trim().toUpperCase(Locale.ROOT), request.fechaHora(),
                request.profesionalCodigo(), request.motivo().trim(), Estado.PENDIENTE,
                ahora, ahora, null);
    }

    public Cita reprogramar(CitaReprogramacionRequest request, OffsetDateTime ahora) {
        return new Cita(id, codigo, pacienteId,
                request.especialidad().trim().toUpperCase(Locale.ROOT), request.fechaHora(),
                request.profesionalCodigo(), request.motivo().trim(), Estado.PENDIENTE,
                fechaCreacion, ahora, version);
    }

    public Cita cambiarEstado(Estado nuevo, OffsetDateTime ahora) {
        return new Cita(id, codigo, pacienteId, especialidad, fechaHora, profesionalCodigo,
                motivo, nuevo, fechaCreacion, ahora, version);
    }
}

