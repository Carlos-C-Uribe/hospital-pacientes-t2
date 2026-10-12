package pe.edu.vidasalud.pacientes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
@Table("paciente")
public record Paciente(@Id Long id, String tipoDocumento, String numeroDocumento,
 String nombres, String apellidos, LocalDate fechaNacimiento, String telefono,
 String correo, String direccion, OffsetDateTime fechaRegistro,
 OffsetDateTime fechaActualizacion, Estado estado, @Version Long version) {
 public enum Estado { ACTIVO, INACTIVO }
 public static Paciente nuevo(PacienteRequest r) {
  var ahora=OffsetDateTime.now();
  return new Paciente(null,r.tipoDocumento(),r.numeroDocumento(),r.nombres().trim(),
   r.apellidos().trim(),r.fechaNacimiento(),r.telefono(),r.correo(),r.direccion(),
   ahora,ahora,r.estado(),null);
 }
 public Paciente actualizar(PacienteRequest r) {
  return new Paciente(id,r.tipoDocumento(),r.numeroDocumento(),r.nombres().trim(),
   r.apellidos().trim(),r.fechaNacimiento(),r.telefono(),r.correo(),r.direccion(),
   fechaRegistro,OffsetDateTime.now(),r.estado(),version);
 }
}
