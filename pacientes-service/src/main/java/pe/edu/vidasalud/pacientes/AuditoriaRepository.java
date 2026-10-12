package pe.edu.vidasalud.pacientes;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface AuditoriaRepository extends ReactiveCrudRepository<Auditoria,Long> {
 Flux<Auditoria> findByPacienteIdOrderByFechaConsumoAsc(Long pacienteId);
 Mono<Auditoria> findByEventoId(UUID eventoId);
}
