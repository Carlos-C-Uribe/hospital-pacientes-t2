package pe.edu.vidasalud.pacientes;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
@Table("auditoria")
public record Auditoria(@Id Long id,UUID eventoId,Long pacienteId,String tipo,
 String estadoAnterior,String estadoNuevo,OffsetDateTime fechaEvento,
 OffsetDateTime fechaConsumo,String origen) {}
