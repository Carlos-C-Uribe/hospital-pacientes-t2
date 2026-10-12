package pe.edu.vidasalud.pacientes;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
public interface PacienteRepository extends ReactiveCrudRepository<Paciente,Long> {
 Mono<Paciente> findByTipoDocumentoAndNumeroDocumento(String tipoDocumento,String numeroDocumento);
 @Query("SELECT * FROM paciente ORDER BY id LIMIT :size OFFSET :offset")
 Flux<Paciente> pagina(@Param("size") int size,@Param("offset") long offset);
}
