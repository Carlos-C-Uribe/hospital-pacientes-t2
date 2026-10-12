package pe.edu.vidasalud.pacientes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class PacientesApplication {
 public static void main(String[] args) { SpringApplication.run(PacientesApplication.class,args); }
}
