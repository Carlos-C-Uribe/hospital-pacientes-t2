package pe.edu.vidasalud.pacientes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
@Component
public class OutboxWorker {
 private final Eventos eventos; private final RabbitTemplate rabbit;
 public OutboxWorker(Eventos eventos,RabbitTemplate rabbit) { this.eventos=eventos; this.rabbit=rabbit; }
 @Scheduled(fixedDelay=2000)
 public Mono<Void> publicarPendientes() {
  return Mono.defer(()->eventos.pendientes().concatMap(p->
   enviar(p).then(eventos.enviado(p.id())).onErrorResume(e->{
    LoggerFactory.getLogger(OutboxWorker.class).warn("Evento {} pendiente; error {}",p.id(),e.getClass().getSimpleName());
    return eventos.reintentar(p);
   })).then()).onErrorResume(e->{
    LoggerFactory.getLogger(OutboxWorker.class).warn("No se pudo revisar outbox: {}",e.getClass().getSimpleName());
    return Mono.empty();
   });
 }
 private Mono<Void> enviar(Eventos.Pendiente p) {
  return Mono.defer(()->{
   var correlation=new CorrelationData(p.id().toString());
   var message=MessageBuilder.withBody(p.payload().getBytes(StandardCharsets.UTF_8))
    .setContentType("application/json").setMessageId(p.id().toString())
    .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
   // RabbitTemplate usa una API bloqueante: se aisla del hilo reactivo de HTTP.
   return Mono.fromRunnable(()->rabbit.send(RabbitConfig.EXCHANGE,p.routingKey(),message,correlation))
    .subscribeOn(Schedulers.boundedElastic())
    .then(Mono.fromFuture(correlation.getFuture())).timeout(Duration.ofSeconds(5))
    .flatMap(confirm->{
     if(confirm.isAck() && correlation.getReturned()==null) return Mono.<Void>empty();
     return Mono.error(new IllegalStateException("El broker no confirmo el enrutamiento."));
    });
  });
 }
}
