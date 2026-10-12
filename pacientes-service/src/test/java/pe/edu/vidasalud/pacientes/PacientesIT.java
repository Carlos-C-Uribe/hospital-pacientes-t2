package pe.edu.vidasalud.pacientes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class PacientesIT {
 static final String PASSWORD=UUID.randomUUID().toString();
 static final GenericContainer<?> PG=new GenericContainer<>(DockerImageName.parse("postgres:17-alpine"))
  .withEnv("POSTGRES_USER","hospital_pacientes").withEnv("POSTGRES_PASSWORD",PASSWORD)
  .withEnv("POSTGRES_DB","hospital_pacientes").withExposedPorts(5432);
 static final GenericContainer<?> MQ=new GenericContainer<>(DockerImageName.parse("rabbitmq:4-management"))
  .withEnv("RABBITMQ_DEFAULT_USER","hospital").withEnv("RABBITMQ_DEFAULT_PASS",PASSWORD)
  .withExposedPorts(5672);
 static { PG.start(); MQ.start(); }
 @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
  r.add("spring.r2dbc.url",()->"r2dbc:postgresql://"+PG.getHost()+":"+PG.getMappedPort(5432)+"/hospital_pacientes");
  r.add("spring.r2dbc.password",()->PASSWORD);
  r.add("spring.rabbitmq.host",MQ::getHost);
  r.add("spring.rabbitmq.port",()->MQ.getMappedPort(5672));
  r.add("spring.rabbitmq.password",()->PASSWORD);
  r.add("eureka.client.enabled",()->false);
 }
 @Value("${local.server.port}") int port;
 @Autowired PacienteRepository repo;
 @Autowired AuditoriaRepository auditoria;
 @Autowired DatabaseClient db;
 @Autowired ObjectMapper mapper;
 @Autowired RabbitTemplate rabbit;
 @Autowired org.springframework.transaction.reactive.TransactionalOperator tx;
 WebTestClient http;
 @BeforeEach void cliente() {
  http=WebTestClient.bindToServer().baseUrl("http://localhost:"+port).responseTimeout(Duration.ofSeconds(15)).build();
 }
 PacienteRequest datos(String doc,String nombre,Paciente.Estado estado) {
  return new PacienteRequest("DEMO",doc,nombre,"Ficticio",LocalDate.of(2000,1,1),"DEMO-0001","prueba@example.invalid","Direccion de prueba",estado);
 }
 String documento() { return "DEMO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(); }
 Paciente crear(PacienteRequest r) {
  return http.post().uri("/api/pacientes").bodyValue(r).exchange().expectStatus().isCreated()
   .expectHeader().exists("Location").expectBody(Paciente.class).returnResult().getResponseBody();
 }
 @Test void crudCompletoPersisteCambiosYElimina() {
  var r=datos(documento(),"Paciente",Paciente.Estado.ACTIVO); var p=crear(r); assertNotNull(p);
  http.get().uri("/api/pacientes/"+p.id()).exchange().expectStatus().isOk().expectBody().jsonPath("$.numeroDocumento").isEqualTo(r.numeroDocumento());
  http.get().uri("/api/pacientes?page=0&size=100").exchange().expectStatus().isOk().expectBodyList(Paciente.class).consumeWith(result->assertTrue(result.getResponseBody().stream().anyMatch(x->x.id().equals(p.id()))));
  var cambio=datos(r.numeroDocumento(),"Actualizado",Paciente.Estado.INACTIVO);
  http.put().uri("/api/pacientes/"+p.id()).bodyValue(cambio).exchange().expectStatus().isOk().expectBody().jsonPath("$.estado").isEqualTo("INACTIVO");
  assertEquals("Actualizado",repo.findById(p.id()).block(Duration.ofSeconds(5)).nombres());
  http.delete().uri("/api/pacientes/"+p.id()).exchange().expectStatus().isNoContent();
  assertNull(repo.findById(p.id()).block(Duration.ofSeconds(5)));
  http.get().uri("/api/pacientes/"+p.id()).exchange().expectStatus().isNotFound();
  await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertEquals(3,auditoria.findByPacienteIdOrderByFechaConsumoAsc(p.id()).collectList().block().size()));
 }
 @Test void rechazaDocumentoDuplicadoCon409() {
  var r=datos(documento(),"Paciente",Paciente.Estado.ACTIVO); crear(r);
  http.post().uri("/api/pacientes").bodyValue(r).exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.codigo").isEqualTo("DOCUMENTO_DUPLICADO");
 }
 @Test void registroInvalidoDevuelve400() {
  http.post().uri("/api/pacientes").bodyValue(datos(documento(),"",Paciente.Estado.ACTIVO)).exchange().expectStatus().isBadRequest();
 }
 @Test void recursosInexistentesDevuelven404() {
  long id=Long.MAX_VALUE;
  http.get().uri("/api/pacientes/"+id).exchange().expectStatus().isNotFound();
  http.put().uri("/api/pacientes/"+id).bodyValue(datos(documento(),"Paciente",Paciente.Estado.ACTIVO)).exchange().expectStatus().isNotFound();
  http.delete().uri("/api/pacientes/"+id).exchange().expectStatus().isNotFound();
 }
 @Test void actualizacionNoDuplicaDocumento() {
  var a=crear(datos(documento(),"PacienteA",Paciente.Estado.ACTIVO));
  var b=crear(datos(documento(),"PacienteB",Paciente.Estado.ACTIVO));
  http.put().uri("/api/pacientes/"+b.id()).bodyValue(datos(a.numeroDocumento(),"PacienteB",Paciente.Estado.ACTIVO))
   .exchange().expectStatus().isEqualTo(409);
 }
 @Test void dosRegistrosSimultaneosSoloCreanUnPaciente() {
  var r=datos(documento(),"Concurrente",Paciente.Estado.ACTIVO);
  var cliente=org.springframework.web.reactive.function.client.WebClient.create("http://localhost:"+port);
  var creada=new AtomicInteger(); var rechazada=new AtomicInteger();
  var llamada=Mono.defer(()->cliente.post().uri("/api/pacientes").bodyValue(r).exchangeToMono(res->{
   if(res.statusCode().value()==201) creada.incrementAndGet();
   if(res.statusCode().value()==409) rechazada.incrementAndGet();
   return res.releaseBody();
  }));
  Mono.when(llamada,llamada).block(Duration.ofSeconds(15));
  assertEquals(1,creada.get()); assertEquals(1,rechazada.get());
 }
 @Test void rabbitRecibeEventoYNoDuplicaSuRegistro() throws Exception {
  var p=crear(datos(documento(),"Paciente",Paciente.Estado.ACTIVO));
  await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertEquals(1,auditoria.findByPacienteIdOrderByFechaConsumoAsc(p.id()).collectList().block().size()));
  var a=auditoria.findByPacienteIdOrderByFechaConsumoAsc(p.id()).blockFirst(Duration.ofSeconds(5));
  var evento=new Eventos.Evento(1,a.eventoId(),a.tipo(),a.pacienteId(),a.estadoAnterior(),a.estadoNuevo(),a.fechaEvento(),a.origen());
  var propiedades=new MessageProperties(); propiedades.setContentType("application/json");
  rabbit.send(RabbitConfig.EXCHANGE,"paciente.registrado",new Message(mapper.writeValueAsBytes(evento),propiedades));
  Thread.sleep(1000);
  assertEquals(1,auditoria.findByPacienteIdOrderByFechaConsumoAsc(p.id()).collectList().block().size());
 }
 @Test void transaccionFallidaNoDejaPacienteNiEvento() {
  String doc=documento();
  long antes=db.sql("SELECT COUNT(*) AS n FROM outbox").map((row,meta)->row.get("n",Long.class)).one().block();
  assertThrows(RuntimeException.class,()->repo.save(Paciente.nuevo(datos(doc,"PruebaRollback",Paciente.Estado.ACTIVO)))
   .flatMap(p->db.sql("INSERT INTO outbox(id,routing_key,payload) VALUES(:id,'paciente.registrado','{}')")
    .bind("id",UUID.randomUUID()).fetch().rowsUpdated().then(Mono.<Void>error(new IllegalStateException("rollback intencional"))))
   .as(tx::transactional).block(Duration.ofSeconds(10)));
  assertNull(repo.findByTipoDocumentoAndNumeroDocumento("DEMO",doc).block());
  long despues=db.sql("SELECT COUNT(*) AS n FROM outbox").map((row,meta)->row.get("n",Long.class)).one().block();
  assertEquals(antes,despues);
 }
}
