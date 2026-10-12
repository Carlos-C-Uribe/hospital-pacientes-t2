package pe.edu.vidasalud.pacientes;
import java.util.UUID;
import java.time.OffsetDateTime;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
@Component
public class Eventos {
 public enum Tipo {
  REGISTRADO("PacienteRegistrado","paciente.registrado"),
  ACTUALIZADO("PacienteActualizado","paciente.actualizado"),
  ELIMINADO("PacienteEliminado","paciente.eliminado");
  public final String nombre,routingKey;
  Tipo(String nombre,String routingKey) { this.nombre=nombre; this.routingKey=routingKey; }
 }
 public record Evento(int version,UUID eventoId,String tipo,Long pacienteId,
  String estadoAnterior,String estadoNuevo,OffsetDateTime fecha,String origen) {}
 public record Pendiente(UUID id,String routingKey,String payload,int intentos) {}
 private final DatabaseClient db; private final ObjectMapper mapper;
 public Eventos(DatabaseClient db,ObjectMapper mapper) { this.db=db; this.mapper=mapper; }
 public Mono<Void> registrar(Paciente p,Tipo tipo,String anterior,String nuevo) {
  return Mono.defer(()->{
   var evento=new Evento(1,UUID.randomUUID(),tipo.nombre,p.id(),anterior,nuevo,OffsetDateTime.now(),"pacientes-service");
   return Mono.fromCallable(()->mapper.writeValueAsString(evento))
    .flatMap(json->db.sql("INSERT INTO outbox(id,routing_key,payload) VALUES(:id,:key,:payload)")
     .bind("id",evento.eventoId()).bind("key",tipo.routingKey).bind("payload",json).fetch().rowsUpdated()).then();
  });
 }
 public Flux<Pendiente> pendientes() {
  return db.sql("SELECT id,routing_key,payload,intentos FROM outbox WHERE enviado=false AND siguiente_intento<=CURRENT_TIMESTAMP ORDER BY creado_en LIMIT 50")
   .map((row,meta)->new Pendiente(row.get("id",UUID.class),row.get("routing_key",String.class),row.get("payload",String.class),row.get("intentos",Integer.class))).all();
 }
 public Mono<Void> enviado(UUID id) {
  return db.sql("UPDATE outbox SET enviado=true WHERE id=:id").bind("id",id).fetch().rowsUpdated().then();
 }
 public Mono<Void> reintentar(Pendiente p) {
  var siguiente=OffsetDateTime.now().plusSeconds(Math.min(60,2L*(p.intentos()+1)));
  return db.sql("UPDATE outbox SET intentos=intentos+1,siguiente_intento=:fecha WHERE id=:id AND enviado=false")
   .bind("fecha",siguiente).bind("id",p.id()).fetch().rowsUpdated().then();
 }
}
