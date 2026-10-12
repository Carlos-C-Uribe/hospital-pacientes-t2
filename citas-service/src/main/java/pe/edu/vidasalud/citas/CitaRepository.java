package pe.edu.vidasalud.citas;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface CitaRepository extends ReactiveCrudRepository<Cita, Long> {
    @Query("""
        SELECT * FROM cita
        WHERE (:paciente = 0 OR paciente_id = :paciente)
          AND (:estado = 'TODOS' OR estado = :estado)
        ORDER BY fecha_hora, id LIMIT :size OFFSET :offset
        """)
    Flux<Cita> pagina(@Param("paciente") long paciente, @Param("estado") String estado,
                     @Param("size") int size, @Param("offset") long offset);
}

