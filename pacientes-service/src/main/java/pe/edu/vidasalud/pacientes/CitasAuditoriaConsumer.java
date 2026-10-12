package pe.edu.vidasalud.pacientes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
public class CitasAuditoriaConsumer {
    // Contrato JSON independiente: Pacientes no depende del codigo Java de Citas.
    public record EventoCita(int version, UUID eventoId, String tipo, Long citaId,
            String codigoCita, Long pacienteId, String estadoAnterior, String estadoNuevo,
            OffsetDateTime fechaHora, OffsetDateTime fecha, String origen) {}

    private static final Map<String, String> DESTINOS = Map.of(
            "CitaCreada", "PENDIENTE", "CitaReprogramada", "PENDIENTE",
            "CitaConfirmada", "CONFIRMADA", "CitaCancelada", "CANCELADA",
            "CitaAtendida", "ATENDIDA", "CitaAusente", "AUSENTE");
    private static final Set<String> ANTERIORES = Set.of("NO_EXISTE", "PENDIENTE", "CONFIRMADA");
    private final DatabaseClient db;
    private final ObjectMapper mapper;

    public CitasAuditoriaConsumer(DatabaseClient db, ObjectMapper mapper) {
        this.db = db; this.mapper = mapper;
    }

    @RabbitListener(queues = CitasRabbitConfig.QUEUE)
    public Mono<Void> recibir(Message message) {
        return Mono.defer(() -> Mono.fromCallable(() ->
                mapper.readValue(message.getBody(), EventoCita.class)).flatMap(evento -> {
            validarContrato(evento);
            return db.sql("""
                INSERT INTO auditoria_cita(evento_id, cita_id, codigo_cita, paciente_id, tipo,
                    estado_anterior, estado_nuevo, fecha_hora, fecha_evento, origen)
                VALUES(:evento, :cita, :codigo, :paciente, :tipo, :anterior, :nuevo, :horario, :fecha, :origen)
                ON CONFLICT(evento_id) DO NOTHING
                """).bind("evento", evento.eventoId()).bind("cita", evento.citaId())
                    .bind("codigo", evento.codigoCita()).bind("paciente", evento.pacienteId())
                    .bind("tipo", evento.tipo()).bind("anterior", evento.estadoAnterior())
                    .bind("nuevo", evento.estadoNuevo()).bind("horario", evento.fechaHora())
                    .bind("fecha", evento.fecha()).bind("origen", evento.origen())
                    .fetch().rowsUpdated().then();
        }));
    }

    public static void validarContrato(EventoCita evento) {
        if (evento == null || evento.version() != 1 || evento.eventoId() == null
                || evento.citaId() == null || evento.citaId() < 1
                || evento.pacienteId() == null || evento.pacienteId() < 1
                || evento.codigoCita() == null || !evento.codigoCita().startsWith("CITA-")
                || evento.codigoCita().length() > 50 || evento.fecha() == null
                || evento.fechaHora() == null || evento.tipo() == null
                || evento.estadoAnterior() == null || evento.estadoNuevo() == null
                || !"citas-service".equals(evento.origen())
                || !ANTERIORES.contains(evento.estadoAnterior())
                || !evento.estadoNuevo().equals(DESTINOS.get(evento.tipo()))
                || ("CitaCreada".equals(evento.tipo()) != "NO_EXISTE".equals(evento.estadoAnterior()))) {
            throw new IllegalArgumentException("Contrato de evento de cita invalido.");
        }
    }
}

