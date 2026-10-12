package pe.edu.vidasalud.pacientes;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CitasRabbitConfig {
    public static final String EXCHANGE = "hospital.citas";
    public static final String QUEUE = "pacientes.citas.auditoria";
    public static final String DLX = "hospital.citas.dlx";
    public static final String DLQ = "pacientes.citas.auditoria.dlq";

    @Bean
    Declarables topologiaCitas() {
        var exchange = new TopicExchange(EXCHANGE, true, false);
        var dlx = new DirectExchange(DLX, true, false);
        var queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey("fallido").build();
        var dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(exchange).with("cita.*"),
                BindingBuilder.bind(dlq).to(dlx).with("fallido"));
    }
}

