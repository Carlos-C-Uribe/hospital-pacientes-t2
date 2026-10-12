package pe.edu.vidasalud.pacientes;
import org.springframework.http.HttpStatus;
public class ReglaException extends RuntimeException {
 private final HttpStatus status; private final String codigo;
 public ReglaException(HttpStatus status,String codigo,String mensaje) {
  super(mensaje); this.status=status; this.codigo=codigo;
 }
 public HttpStatus status() { return status; }
 public String codigo() { return codigo; }
 public static ReglaException inexistente() { return new ReglaException(HttpStatus.NOT_FOUND,"PACIENTE_NO_ENCONTRADO","El paciente no existe."); }
 public static ReglaException duplicado() { return new ReglaException(HttpStatus.CONFLICT,"DOCUMENTO_DUPLICADO","Ya existe un paciente con ese tipo y numero de documento."); }
 public static ReglaException invalido(String mensaje) { return new ReglaException(HttpStatus.BAD_REQUEST,"DATOS_INVALIDOS",mensaje); }
}
