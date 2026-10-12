package pe.edu.vidasalud.citas;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.slf4j.LoggerFactory;
@RestControllerAdvice
public class ApiErrors {
 public record ErrorRespuesta(OffsetDateTime fecha,int status,String codigo,String mensaje) {}
 private ResponseEntity<ErrorRespuesta> respuesta(int status,String codigo,String mensaje) {
  return ResponseEntity.status(status).body(new ErrorRespuesta(OffsetDateTime.now(),status,codigo,mensaje));
 }
 @ExceptionHandler(ReglaException.class) ResponseEntity<ErrorRespuesta> regla(ReglaException e) {
  return respuesta(e.status().value(),e.codigo(),e.getMessage());
 }
 @ExceptionHandler(WebExchangeBindException.class) ResponseEntity<ErrorRespuesta> validacion(WebExchangeBindException e) {
  var mensaje=e.getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).collect(Collectors.joining("; "));
  return respuesta(400,"DATOS_INVALIDOS",mensaje);
 }
 @ExceptionHandler(ServerWebInputException.class) ResponseEntity<ErrorRespuesta> entrada(ServerWebInputException e) {
  return respuesta(400,"DATOS_INVALIDOS","Revise el JSON, los tipos de datos y el estado de la cita.");
 }
 @ExceptionHandler(OptimisticLockingFailureException.class) ResponseEntity<ErrorRespuesta> concurrencia(OptimisticLockingFailureException e) {
  return respuesta(409,"CAMBIO_CONCURRENTE","La cita cambio durante la operacion. Consulte sus datos y vuelva a intentarlo.");
 }
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorRespuesta> inesperado(Exception e) {
  LoggerFactory.getLogger(ApiErrors.class).error("Error interno de tipo {}",e.getClass().getSimpleName());
  return respuesta(500,"ERROR_INTERNO","No se pudo completar la operacion.");
 }
}
