package pe.edu.vidasalud.pacientes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.*;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
@Configuration
public class RabbitConfig {
 public static final String EXCHANGE="hospital.pacientes";
 public static final String QUEUE="pacientes.auditoria";
 public static final String DLX="hospital.pacientes.dlx";
 public static final String DLQ="pacientes.auditoria.dlq";
 @Bean Declarables topologia() {
  var exchange=new TopicExchange(EXCHANGE,true,false);
  var dlx=new DirectExchange(DLX,true,false);
  var queue=QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey("fallido").build();
  var dlq=QueueBuilder.durable(DLQ).build();
  return new Declarables(exchange,dlx,queue,dlq,
   BindingBuilder.bind(queue).to(exchange).with("paciente.*"),
   BindingBuilder.bind(dlq).to(dlx).with("fallido"));
 }
 @Bean TransactionalOperator transacciones(ReactiveTransactionManager manager) {
  return TransactionalOperator.create(manager);
 }
}
