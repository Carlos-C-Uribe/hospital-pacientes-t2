package pe.edu.vidasalud.pacientes;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record PacienteRequest(
 @NotBlank(message="Es obligatorio") @Pattern(regexp="DEMO|DNI_FICTICIO|PASAPORTE_FICTICIO",message="Use un tipo de documento ficticio") String tipoDocumento,
 @NotBlank(message="Es obligatorio") @Pattern(regexp="DEMO-[A-Z0-9-]{1,24}",message="Use DEMO- seguido de letras mayusculas o numeros") String numeroDocumento,
 @NotBlank(message="Es obligatorio") @Size(max=80) String nombres,
 @NotBlank(message="Es obligatorio") @Size(max=100) String apellidos,
 @NotNull @Past(message="Debe ser anterior a hoy") LocalDate fechaNacimiento,
 @NotBlank @Pattern(regexp="DEMO-[0-9]{3,12}",message="Use un telefono ficticio como DEMO-0001") String telefono,
 @NotBlank @Email(message="Correo invalido") @Size(max=160) String correo,
 @Size(max=200) String direccion,
 @NotNull(message="Use ACTIVO o INACTIVO") Paciente.Estado estado) {}
