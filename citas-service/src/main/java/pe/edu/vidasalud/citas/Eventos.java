package pe.edu.vidasalud.citas;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
public class Eventos {
    public enum Tipo {
        CREADA("CitaCreada", "cita.creada"),
        REPROGRAMADA("CitaReprogramada", "cita.reprogramada"),
        CONFIRMADA("CitaConfirmada", "cita.confirmada"),
        CANCELADA("CitaCancelada", "cita.cancelada"),
        ATENDIDA("CitaAtendida", "cita.atendida"),
        AUSENTE("CitaAusente", "cita.ausente");

        public final String nombre;
        public final String routingKey;
        Tipo(String nombre, String routingKey) { this.nombre = nombre; this.routingKey = routingKey; }

        public static Tipo paraEstado(Cita.Estado estado) {
            return switch (estado) {
                case CONFIRMADA -> CONFIRMADA;
                case CANCELADA -> CANCELADA;
                case ATENDIDA -> ATENDIDA;
                case AUSENTE -> AUSENTE;
                default -> throw ReglaException.transicion();
            };
        }
    }

    public record Evento(int version, UUID eventoId, String tipo, Long citaId,
            String codigoCita, Long pacienteId, String estadoAnterior, String estadoNuevo,
            OffsetDateTime fechaHora, OffsetDateTime fecha, String origen) {}
    public record Pendiente(UUID id, String routingKey, String payload, int intentos) {}

    private final DatabaseClient db;
    private final ObjectMapper mapper;
    private final Clock reloj;
    public Eventos(DatabaseClient db, ObjectMapper mapper, Clock reloj) {
        this.db = db; this.mapper = mapper; this.reloj = reloj;
    }

    public Mono<Void> registrar(Cita cita, Tipo tipo, String anterior) {
        return Mono.defer(() -> {
            var evento = new Evento(1, UUID.randomUUID(), tipo.nombre, cita.id(), cita.codigo(),
                    cita.pacienteId(), anterior, cita.estado().name(), cita.fechaHora(),
                    OffsetDateTime.now(reloj), "citas-service");
            return Mono.fromCallable(() -> mapper.writeValueAsString(evento))
                    .flatMap(json -> db.sql("""
                        INSERT INTO outbox(id, routing_key, payload) VALUES(:id, :key, :payload)
                        """).bind("id", evento.eventoId()).bind("key", tipo.routingKey)
                            .bind("payload", json).fetch().rowsUpdated()).then();
        });
    }

    public Flux<Pendiente> pendientes() {
        return db.sql("""
            SELECT id, routing_key, payload, intentos FROM outbox
            WHERE enviado=false AND siguiente_intento<=CURRENT_TIMESTAMP
            ORDER BY creado_en LIMIT 50
            """).map((row, meta) -> new Pendiente(row.get("id", UUID.class),
                    row.get("routing_key", String.class), row.get("payload", String.class),
                    row.get("intentos", Integer.class))).all();
    }

    public Mono<Void> enviado(UUID id) {
        return db.sql("UPDATE outbox SET enviado=true WHERE id=:id")
                .bind("id", id).fetch().rowsUpdated().then();
    }

    public Mono<Void> reintentar(Pendiente evento) {
        var siguiente = OffsetDateTime.now(reloj).plusSeconds(Math.min(60, 2L * (evento.intentos() + 1)));
        return db.sql("""
            UPDATE outbox SET intentos=intentos+1, siguiente_intento=:fecha
            WHERE id=:id AND enviado=false
            """).bind("fecha", siguiente).bind("id", evento.id()).fetch().rowsUpdated().then();
    }
}

