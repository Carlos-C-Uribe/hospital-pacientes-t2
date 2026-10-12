package pe.edu.vidasalud.citas;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    WebClient pacientesWebClient(ReactorLoadBalancerExchangeFilterFunction balanceador,
            @Value("${hospital.pacientes-base-url:http://pacientes-service}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).filter(balanceador).build();
    }
}

