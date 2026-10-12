package pe.edu.vidasalud.citas;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CitasIT {
    static final String PASSWORD = UUID.randomUUID().toString();
    static final GenericContainer<?> PACIENTES_DB = postgres("hospital_pacientes");
    static final GenericContainer<?> CITAS_DB = postgres("hospital_citas");
    static final GenericContainer<?> MQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:4-management"))
            .withEnv("RABBITMQ_DEFAULT_USER", "hospital")
            .withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD).withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    static Process pacientesProceso;
    static WebTestClient pacientesHttp;
    static int pacientesPuerto;

    static GenericContainer<?> postgres(String nombre) {
        return new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withEnv("POSTGRES_USER", nombre).withEnv("POSTGRES_DB", nombre)
                .withEnv("POSTGRES_PASSWORD", PASSWORD).withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) throws Exception {
        PACIENTES_DB.start();
        CITAS_DB.start();
        MQ.start();
        try (var socket = new ServerSocket(0)) { pacientesPuerto = socket.getLocalPort(); }
        Path jar = Path.of("../pacientes-service/target/pacientes-service-1.0.0.jar").toAbsolutePath();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Ejecute clean verify desde el pom.xml principal para compilar ambos servicios.");
        }
        Path log = Path.of("target/pacientes-proceso-it.log");
        var proceso = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-jar", jar.toString(), "--server.port=" + pacientesPuerto,
                "--eureka.client.enabled=false");
        var env = proceso.environment();
        env.put("DB_HOST", PACIENTES_DB.getHost());
        env.put("DB_PORT", PACIENTES_DB.getMappedPort(5432).toString());
        env.put("DB_PASSWORD", PASSWORD);
        env.put("RABBIT_HOST", MQ.getHost());
        env.put("RABBIT_PORT", MQ.getMappedPort(5672).toString());
        env.put("RABBIT_PASSWORD", PASSWORD);
        pacientesProceso = proceso.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (pacientesProceso != null) pacientesProceso.destroy();
        }));
        pacientesHttp = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + pacientesPuerto)
                .responseTimeout(Duration.ofSeconds(15)).build();
        try {
            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                    .ignoreExceptions().untilAsserted(() ->
                        pacientesHttp.get().uri("/actuator/health").exchange().expectStatus().isOk());
        } catch (RuntimeException | AssertionError error) {
            pacientesProceso.destroy();
            throw new IllegalStateException("Pacientes no arranco. Revise target/pacientes-proceso-it.log.", error);
        }
        r.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + CITAS_DB.getHost() + ":"
                + CITAS_DB.getMappedPort(5432) + "/hospital_citas");
        r.add("spring.r2dbc.password", () -> PASSWORD);
        r.add("spring.rabbitmq.host", MQ::getHost);
        r.add("spring.rabbitmq.port", () -> MQ.getMappedPort(5672));
        r.add("spring.rabbitmq.password", () -> PASSWORD);
        r.add("eureka.client.enabled", () -> false);
        // Descubrimiento estatico aislado para las pruebas; Compose usa Eureka real.
        r.add("spring.cloud.discovery.client.simple.instances.pacientes-service[0].uri",
                () -> "http://127.0.0.1:" + pacientesPuerto);
    }

    @Value("${local.server.port}") int port;
    @Autowired CitaRepository repo;
    @Autowired Eventos eventos;
    @Autowired DatabaseClient db;
    @Autowired TransactionalOperator tx;
    WebTestClient http;

    @BeforeEach void cliente() {
        http = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(15)).build();
    }

    Map<String, Object> datosPaciente(String estado) {
        return Map.of("tipoDocumento", "DEMO",
                "numeroDocumento", "DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "nombres", "Paciente", "apellidos", "Ficticio", "fechaNacimiento", "2000-01-01",
                "telefono", "DEMO-0001", "correo", "prueba@example.invalid", "estado", estado);
    }

    long paciente(String estado) {
        var response = pacientesHttp.post().uri("/api/pacientes").bodyValue(datosPaciente(estado))
                .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult().getResponseBody();
        assertNotNull(response);
        return ((Number) response.get("id")).longValue();
    }

    OffsetDateTime fecha() {
        var dia = LocalDate.now(Cita.ZONA).plusDays(3);
        while (dia.getDayOfWeek() == DayOfWeek.SUNDAY) dia = dia.plusDays(1);
        return dia.atTime(10, 0).atOffset(Cita.ZONA);
    }

    CitaRequest datos(long pacienteId) {
        return new CitaRequest(pacienteId, "Medicina general", fecha(),
                "DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Reserva administrativa ficticia");
    }

    Cita crear(long pacienteId) {
        Cita cita = http.post().uri("/api/citas").bodyValue(datos(pacienteId))
                .exchange().expectStatus().isCreated().expectHeader().exists("Location")
                .expectBody(Cita.class).returnResult().getResponseBody();
        assertNotNull(cita);
        return cita;
    }

    @Test void flujoCompletoValidaPacientePersisteYPublicaEventoConsumidoPorPacientes() {
        long pacienteId = paciente("ACTIVO");
        var cita = crear(pacienteId);
        http.get().uri("/api/citas/" + cita.id()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.pacienteId").isEqualTo((int) pacienteId);
        http.get().uri("/api/citas?pacienteId=" + pacienteId + "&estado=PENDIENTE")
                .exchange().expectStatus().isOk().expectBodyList(Cita.class).hasSize(1);
        http.patch().uri("/api/citas/" + cita.id() + "/estado")
                .bodyValue(new CitaEstadoRequest(Cita.Estado.CONFIRMADA)).exchange().expectStatus().isOk();
        assertEquals(Cita.Estado.CONFIRMADA, repo.findById(cita.id()).block(Duration.ofSeconds(5)).estado());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var recibidos = pacientesHttp.get().uri("/api/pacientes/" + pacienteId + "/eventos-citas")
                    .exchange().expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
            assertNotNull(recibidos);
            assertTrue(recibidos.stream().anyMatch(e -> "CitaConfirmada".equals(e.get("tipo"))
                    && "citas-service".equals(e.get("origen"))));
        });
    }

    @Test void reprogramacionDeConfirmadaRegresaAPendiente() {
        var cita = crear(paciente("ACTIVO"));
        http.patch().uri("/api/citas/" + cita.id() + "/estado")
                .bodyValue(new CitaEstadoRequest(Cita.Estado.CONFIRMADA)).exchange().expectStatus().isOk();
        var nuevaFecha = cita.fechaHora().plusWeeks(1);
        http.put().uri("/api/citas/" + cita.id())
                .bodyValue(new CitaReprogramacionRequest("Medicina general", nuevaFecha,
                        cita.profesionalCodigo(), "Cambio administrativo"))
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("PENDIENTE");
        assertEquals(nuevaFecha, repo.findById(cita.id()).block(Duration.ofSeconds(5)).fechaHora());
    }

    @Test void cancelarConservaLaCitaYNoPermiteConfirmarla() {
        var cita = crear(paciente("ACTIVO"));
        http.delete().uri("/api/citas/" + cita.id()).exchange().expectStatus().isNoContent();
        http.delete().uri("/api/citas/" + cita.id()).exchange().expectStatus().isNoContent();
        http.get().uri("/api/citas/" + cita.id()).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.estado").isEqualTo("CANCELADA");
        http.patch().uri("/api/citas/" + cita.id() + "/estado")
                .bodyValue(new CitaEstadoRequest(Cita.Estado.CONFIRMADA)).exchange().expectStatus().isEqualTo(409);
    }

    @Test void pacienteInexistenteImpideCrearCita() {
        http.post().uri("/api/citas").bodyValue(datos(Long.MAX_VALUE)).exchange()
                .expectStatus().isNotFound().expectBody().jsonPath("$.codigo").isEqualTo("PACIENTE_NO_ENCONTRADO");
    }

    @Test void pacienteInactivoImpideCrearCita() {
        http.post().uri("/api/citas").bodyValue(datos(paciente("INACTIVO"))).exchange()
                .expectStatus().isEqualTo(409).expectBody().jsonPath("$.codigo").isEqualTo("PACIENTE_INACTIVO");
    }

    @Test void fechaPasadaYDatosAusentesDevuelven400() {
        var r = datos(paciente("ACTIVO"));
        http.post().uri("/api/citas").bodyValue(new CitaRequest(r.pacienteId(), r.especialidad(),
                fecha().minusYears(1), r.profesionalCodigo(), r.motivo())).exchange().expectStatus().isBadRequest();
        http.post().uri("/api/citas").header("Content-Type", "application/json")
                .bodyValue("{}").exchange().expectStatus().isBadRequest();
    }

    @Test void pacienteEliminadoConservaHistoriaPeroImpideConfirmacion() {
        long pacienteId = paciente("ACTIVO");
        var cita = crear(pacienteId);
        pacientesHttp.delete().uri("/api/pacientes/" + pacienteId).exchange().expectStatus().isNoContent();
        http.patch().uri("/api/citas/" + cita.id() + "/estado")
                .bodyValue(new CitaEstadoRequest(Cita.Estado.CONFIRMADA)).exchange().expectStatus().isNotFound();
        http.delete().uri("/api/citas/" + cita.id()).exchange().expectStatus().isNoContent();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var recibidos = pacientesHttp.get().uri("/api/pacientes/" + pacienteId + "/eventos-citas")
                    .exchange().expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
            assertNotNull(recibidos);
            assertTrue(recibidos.stream().anyMatch(e -> "CitaCancelada".equals(e.get("tipo"))
                    && ((Number) e.get("citaId")).longValue() == cita.id()));
        });
    }

    @Test void falloDeTransaccionRevierteCitaYOutbox() {
        long pacienteId = paciente("ACTIVO");
        String codigo = "CITA-" + UUID.randomUUID();
        var request = datos(pacienteId);
        var nueva = Cita.nueva(request, OffsetDateTime.now());
        var cita = new Cita(null, codigo, pacienteId, nueva.especialidad(), nueva.fechaHora(),
                nueva.profesionalCodigo(), nueva.motivo(), nueva.estado(),
                nueva.fechaCreacion(), nueva.fechaActualizacion(), null);
        assertThrows(RuntimeException.class, () -> repo.save(cita)
                .flatMap(c -> eventos.registrar(c, Eventos.Tipo.CREADA, "NO_EXISTE")
                        .then(Mono.error(new IllegalStateException("Rollback intencional"))))
                .as(tx::transactional).block(Duration.ofSeconds(10)));
        Long citas = db.sql("SELECT COUNT(*) AS n FROM cita WHERE codigo=:codigo").bind("codigo", codigo)
                .map((row, meta) -> row.get("n", Long.class)).one().block(Duration.ofSeconds(5));
        Long pendientes = db.sql("SELECT COUNT(*) AS n FROM outbox WHERE payload LIKE :codigo")
                .bind("codigo", "%" + codigo + "%").map((row, meta) -> row.get("n", Long.class))
                .one().block(Duration.ofSeconds(5));
        assertEquals(0L, citas);
        assertEquals(0L, pendientes);
    }

    @AfterAll static void cerrarPaciente() throws InterruptedException {
        if (pacientesProceso != null) {
            pacientesProceso.destroy();
            if (!pacientesProceso.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                pacientesProceso.destroyForcibly();
            }
        }
    }
}

