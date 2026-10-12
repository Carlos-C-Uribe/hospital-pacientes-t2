package pe.edu.vidasalud.citas;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CitaService {
    private final CitaRepository repo;
    private final PacienteClient pacientes;
    private final Eventos eventos;
    private final TransactionalOperator tx;
    private final Clock reloj;

    public CitaService(CitaRepository repo, PacienteClient pacientes, Eventos eventos,
                       TransactionalOperator tx, Clock reloj) {
        this.repo = repo;
        this.pacientes = pacientes;
        this.eventos = eventos;
        this.tx = tx;
        this.reloj = reloj;
    }

    public Mono<Cita> obtener(long id) {
        if (id < 1) return Mono.error(ReglaException.invalido("El ID debe ser positivo."));
        return repo.findById(id).switchIfEmpty(Mono.error(ReglaException.inexistente()));
    }

    public Flux<Cita> listar(int page, int size, Long pacienteId, Cita.Estado estado) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100
                || (pacienteId != null && pacienteId < 1)) {
            return Flux.error(ReglaException.invalido(
                    "Use page >= 0, size entre 1 y 100 y un pacienteId positivo."));
        }
        return repo.pagina(pacienteId == null ? 0 : pacienteId,
                estado == null ? "TODOS" : estado.name(), size, (long) page * size);
    }

    public Mono<Cita> crear(CitaRequest request) {
        return Mono.defer(() -> {
            validarHorario(request.fechaHora());
            return pacientes.validarActivo(request.pacienteId())
                    .then(Mono.defer(() -> guardarConEvento(
                            Cita.nueva(request, ahora()), Eventos.Tipo.CREADA, "NO_EXISTE")))
                    .onErrorMap(DataIntegrityViolationException.class, this::traducirConflicto);
        });
    }

    public Mono<Cita> reprogramar(long id, CitaReprogramacionRequest request) {
        return obtener(id).flatMap(anterior -> {
            if (!editable(anterior) || !anterior.fechaHora().isAfter(ahora())) {
                return Mono.error(ReglaException.transicion());
            }
            validarHorario(request.fechaHora());
            return pacientes.validarActivo(anterior.pacienteId())
                    .then(Mono.defer(() -> guardarConEvento(
                            anterior.reprogramar(request, ahora()), Eventos.Tipo.REPROGRAMADA,
                            anterior.estado().name())));
        }).onErrorMap(DataIntegrityViolationException.class, this::traducirConflicto);
    }

    public Mono<Cita> cambiarEstado(long id, Cita.Estado nuevo) {
        if (nuevo == null) return Mono.error(ReglaException.invalido("El estado es obligatorio."));
        return obtener(id).flatMap(anterior -> {
            if (anterior.estado() == nuevo) return Mono.just(anterior);
            validarTransicion(anterior, nuevo);
            Mono<?> validacion = nuevo == Cita.Estado.CONFIRMADA
                    ? pacientes.validarActivo(anterior.pacienteId()) : Mono.empty();
            return validacion.then(Mono.defer(() -> guardarConEvento(
                    anterior.cambiarEstado(nuevo, ahora()), Eventos.Tipo.paraEstado(nuevo),
                    anterior.estado().name())));
        });
    }

    public Mono<Void> cancelar(long id) {
        return cambiarEstado(id, Cita.Estado.CANCELADA).then();
    }

    private Mono<Cita> guardarConEvento(Cita cita, Eventos.Tipo tipo, String anterior) {
        return repo.save(cita).flatMap(guardada ->
                eventos.registrar(guardada, tipo, anterior).thenReturn(guardada))
                .as(tx::transactional);
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj);
    }

    private boolean editable(Cita cita) {
        return cita.estado() == Cita.Estado.PENDIENTE || cita.estado() == Cita.Estado.CONFIRMADA;
    }

    private void validarHorario(OffsetDateTime fecha) {
        if (fecha == null || !fecha.isAfter(ahora())) {
            throw ReglaException.invalido("La fecha de la cita debe estar en el futuro.");
        }
        var local = fecha.withOffsetSameInstant(Cita.ZONA);
        var hora = local.toLocalTime();
        if (local.getDayOfWeek() == DayOfWeek.SUNDAY
                || hora.isBefore(LocalTime.of(8, 0)) || !hora.isBefore(LocalTime.of(18, 0))
                || (hora.getMinute() != 0 && hora.getMinute() != 30)
                || hora.getSecond() != 0 || hora.getNano() != 0) {
            throw ReglaException.invalido(
                    "Use lunes a sabado, de 08:00 a 17:30, en intervalos de 30 minutos (UTC-5).");
        }
    }

    private void validarTransicion(Cita anterior, Cita.Estado nuevo) {
        boolean permitida = switch (anterior.estado()) {
            case PENDIENTE -> nuevo == Cita.Estado.CONFIRMADA
                    || nuevo == Cita.Estado.CANCELADA || nuevo == Cita.Estado.AUSENTE;
            case CONFIRMADA -> nuevo == Cita.Estado.ATENDIDA
                    || nuevo == Cita.Estado.CANCELADA || nuevo == Cita.Estado.AUSENTE;
            default -> false;
        };
        if (!permitida) throw ReglaException.transicion();
        if (nuevo == Cita.Estado.CONFIRMADA && !anterior.fechaHora().isAfter(ahora())) {
            throw ReglaException.invalido("La confirmacion debe realizarse antes de la cita.");
        }
        if ((nuevo == Cita.Estado.ATENDIDA || nuevo == Cita.Estado.AUSENTE)
                && anterior.fechaHora().isAfter(ahora())) {
            throw ReglaException.invalido("ATENDIDA y AUSENTE se permiten desde el horario de la cita.");
        }
    }

    private Throwable traducirConflicto(DataIntegrityViolationException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            String mensaje = causa.getMessage();
            if (mensaje != null && (mensaje.contains("uq_cita_agenda")
                    || mensaje.contains("uq_cita_paciente_horario"))) {
                return ReglaException.horario();
            }
        }
        return error;
    }
}

