package pe.edu.vidasalud.pacientes;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface AuditoriaCitaRepository extends ReactiveCrudRepository<AuditoriaCita, Long> {
    @Query("""
        SELECT * FROM auditoria_cita WHERE paciente_id=:paciente
        ORDER BY fecha_evento, id LIMIT :size OFFSET :offset
        """)
    Flux<AuditoriaCita> pagina(@Param("paciente") long paciente,
                              @Param("size") int size, @Param("offset") long offset);
}

