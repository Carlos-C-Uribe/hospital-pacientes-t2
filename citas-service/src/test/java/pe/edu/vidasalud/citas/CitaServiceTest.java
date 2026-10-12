package pe.edu.vidasalud.citas;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitaServiceTest {
    CitaRepository repo;
    PacienteClient pacientes;
    Eventos eventos;
    CitaService service;
    final Clock reloj = Clock.fixed(Instant.parse("2026-10-12T14:00:00Z"), ZoneOffset.UTC);
    final OffsetDateTime futura = OffsetDateTime.parse("2026-10-13T10:00:00-05:00");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparar() {
        repo = mock(CitaRepository.class);
        pacientes = mock(PacienteClient.class);
        eventos = mock(Eventos.class);
        var tx = mock(TransactionalOperator.class);
        when(tx.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(pacientes.validarActivo(1)).thenReturn(Mono.just(new PacienteClient.Referencia(1L, "ACTIVO")));
        when(eventos.registrar(any(), any(), anyString())).thenReturn(Mono.empty());
        when(repo.findById(anyLong())).thenReturn(Mono.empty());
        when(repo.save(any(Cita.class))).thenAnswer(i -> {
            Cita c = i.getArgument(0);
            return Mono.just(new Cita(c.id() == null ? 1L : c.id(), c.codigo(), c.pacienteId(),
                    c.especialidad(), c.fechaHora(), c.profesionalCodigo(), c.motivo(), c.estado(),
                    c.fechaCreacion(), c.fechaActualizacion(), c.version() == null ? 0L : c.version() + 1));
        });
        service = new CitaService(repo, pacientes, eventos, tx, reloj);
    }

    CitaRequest datos(OffsetDateTime fecha) {
        return new CitaRequest(1L, "Medicina general", fecha, "DEMO-MEDICO01", "Reserva de demostracion");
    }

    Cita existente(Cita.Estado estado, OffsetDateTime fecha) {
        var c = Cita.nueva(datos(fecha), OffsetDateTime.now(reloj));
        return new Cita(1L, c.codigo(), 1L, c.especialidad(), c.fechaHora(), c.profesionalCodigo(),
                c.motivo(), estado, c.fechaCreacion(), c.fechaActualizacion(), 0L);
    }

    @Test
    void crearValidaPacienteGuardaPendienteYRegistraEvento() {
        StepVerifier.create(service.crear(datos(futura)))
                .assertNext(c -> {
                    assertNotNull(c.id());
                    assertEquals(Cita.Estado.PENDIENTE, c.estado());
                    assertEquals(1L, c.pacienteId());
                }).verifyComplete();
        verify(pacientes).validarActivo(1);
        verify(eventos).registrar(any(Cita.class), eq(Eventos.Tipo.CREADA), eq("NO_EXISTE"));
    }

    @Test
    void fechaPasadaNoGuardaNiConsultaPaciente() {
        StepVerifier.create(service.crear(datos(futura.minusYears(1))))
                .expectErrorSatisfies(e -> assertEquals(400, ((ReglaException) e).status().value()))
                .verify();
        verifyNoInteractions(pacientes);
        verify(repo, never()).save(any());
    }

    @Test
    void domingoNoEsDiaDeAgenda() {
        StepVerifier.create(service.crear(datos(OffsetDateTime.parse("2026-10-18T10:00:00-05:00"))))
                .expectError(ReglaException.class).verify();
        verify(repo, never()).save(any());
    }

    @Test
    void pacienteInactivoImpideRegistro() {
        when(pacientes.validarActivo(1)).thenReturn(Mono.error(new ReglaException(
                org.springframework.http.HttpStatus.CONFLICT, "PACIENTE_INACTIVO", "Paciente inactivo")));
        StepVerifier.create(service.crear(datos(futura)))
                .expectErrorSatisfies(e -> assertEquals("PACIENTE_INACTIVO", ((ReglaException) e).codigo()))
                .verify();
        verify(repo, never()).save(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void citaCanceladaNoPuedeConfirmarse() {
        when(repo.findById(1L)).thenReturn(Mono.just(existente(Cita.Estado.CANCELADA, futura)));
        StepVerifier.create(service.cambiarEstado(1, Cita.Estado.CONFIRMADA))
                .expectErrorSatisfies(e -> assertEquals(409, ((ReglaException) e).status().value()))
                .verify();
        verify(repo, never()).save(any());
    }

    @Test
    void reprogramarConfirmadaExigeNuevaConfirmacion() {
        when(repo.findById(1L)).thenReturn(Mono.just(existente(Cita.Estado.CONFIRMADA, futura)));
        var r = new CitaReprogramacionRequest("Medicina general", futura.plusDays(1),
                "DEMO-MEDICO01", "Reprogramacion administrativa");
        StepVerifier.create(service.reprogramar(1, r)).assertNext(c -> {
            assertEquals(Cita.Estado.PENDIENTE, c.estado());
            assertEquals(1L, c.pacienteId());
            assertEquals(r.fechaHora(), c.fechaHora());
        }).verifyComplete();
        verify(eventos).registrar(any(), eq(Eventos.Tipo.REPROGRAMADA), eq("CONFIRMADA"));
    }

    @Test
    void eliminarCancelaSinBorrarElRegistro() {
        when(repo.findById(1L)).thenReturn(Mono.just(existente(Cita.Estado.PENDIENTE, futura)));
        StepVerifier.create(service.cancelar(1)).verifyComplete();
        verify(repo, never()).delete(any(Cita.class));
        verify(eventos).registrar(argThat(c -> c.estado() == Cita.Estado.CANCELADA),
                eq(Eventos.Tipo.CANCELADA), eq("PENDIENTE"));
    }

    @Test
    void noSePuedeMarcarAtendidaAntesDelHorario() {
        when(repo.findById(1L)).thenReturn(Mono.just(existente(Cita.Estado.CONFIRMADA, futura)));
        StepVerifier.create(service.cambiarEstado(1, Cita.Estado.ATENDIDA))
                .expectErrorSatisfies(e -> assertEquals(400, ((ReglaException) e).status().value()))
                .verify();
    }

    @Test
    void citaConfirmadaPuedeMarcarseAtendidaAlLlegarSuHorario() {
        when(repo.findById(1L)).thenReturn(Mono.just(existente(Cita.Estado.CONFIRMADA,
                OffsetDateTime.parse("2026-10-12T08:00:00-05:00"))));
        StepVerifier.create(service.cambiarEstado(1, Cita.Estado.ATENDIDA))
                .assertNext(c -> assertEquals(Cita.Estado.ATENDIDA, c.estado())).verifyComplete();
    }

    @Test
    void repetirConfirmacionNoDuplicaElEvento() {
        when(repo.findById(1L)).thenReturn(Mono.just(existente(Cita.Estado.CONFIRMADA, futura)));
        StepVerifier.create(service.cambiarEstado(1, Cita.Estado.CONFIRMADA)).expectNextCount(1).verifyComplete();
        verifyNoInteractions(eventos, pacientes);
        verify(repo, never()).save(any());
    }

    @Test
    void citaInexistenteDevuelve404() {
        StepVerifier.create(service.obtener(99))
                .expectErrorSatisfies(e -> assertEquals(404, ((ReglaException) e).status().value()))
                .verify();
    }

    @Test
    void paginacionInvalidaDevuelve400() {
        StepVerifier.create(service.listar(0, 0, null, null))
                .expectErrorSatisfies(e -> assertEquals(400, ((ReglaException) e).status().value()))
                .verify();
    }
}

