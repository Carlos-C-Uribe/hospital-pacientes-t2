package pe.edu.vidasalud.pacientes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
@Component
public class AuditoriaConsumer {
 private final DatabaseClient db; private final ObjectMapper mapper;
 public AuditoriaConsumer(DatabaseClient db,ObjectMapper mapper) { this.db=db; this.mapper=mapper; }
 @RabbitListener(queues=RabbitConfig.QUEUE)
 public Mono<Void> recibir(Message message) {
  return Mono.defer(()->Mono.fromCallable(()->mapper.readValue(new String(message.getBody(),StandardCharsets.UTF_8),Eventos.Evento.class))
   .flatMap(e->{
    boolean tipoValido=Arrays.stream(Eventos.Tipo.values()).anyMatch(t->t.nombre.equals(e.tipo()));
    if(e.version()!=1 || e.eventoId()==null || e.pacienteId()==null || e.pacienteId()<1 ||
       e.fecha()==null || e.estadoAnterior()==null || e.estadoNuevo()==null ||
       !"pacientes-service".equals(e.origen()) || !tipoValido)
     return Mono.error(new IllegalArgumentException("Contrato de evento invalido."));
    // Idempotencia por evento_id: una entrega repetida no duplica la traza.
    return db.sql("INSERT INTO auditoria(evento_id,paciente_id,tipo,estado_anterior,estado_nuevo,fecha_evento,origen) VALUES(:evento,:paciente,:tipo,:anterior,:nuevo,:fecha,:origen) ON CONFLICT(evento_id) DO NOTHING")
     .bind("evento",e.eventoId()).bind("paciente",e.pacienteId()).bind("tipo",e.tipo())
     .bind("anterior",e.estadoAnterior()).bind("nuevo",e.estadoNuevo())
     .bind("fecha",e.fecha()).bind("origen",e.origen()).fetch().rowsUpdated().then();
   }));
 }
}
