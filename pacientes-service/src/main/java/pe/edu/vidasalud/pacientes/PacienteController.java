package pe.edu.vidasalud.pacientes;
import java.net.URI;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {
 private final PacienteService service; private final AuditoriaRepository auditoria;
 private final AuditoriaCitaRepository auditoriaCitas;
 public PacienteController(PacienteService service,AuditoriaRepository auditoria,AuditoriaCitaRepository auditoriaCitas) {
  this.service=service; this.auditoria=auditoria; this.auditoriaCitas=auditoriaCitas;
 }
 @PostMapping public Mono<ResponseEntity<Paciente>> crear(@Valid @RequestBody PacienteRequest r) {
  return service.crear(r).map(p->ResponseEntity.created(URI.create("/api/pacientes/"+p.id())).body(p));
 }
 @GetMapping public Flux<Paciente> listar(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
  return service.listar(page,size);
 }
 @GetMapping("/{id}") public Mono<Paciente> obtener(@PathVariable long id) { return service.obtener(id); }
 @PutMapping("/{id}") public Mono<Paciente> actualizar(@PathVariable long id,@Valid @RequestBody PacienteRequest r) { return service.actualizar(id,r); }
 @DeleteMapping("/{id}") public Mono<ResponseEntity<Void>> eliminar(@PathVariable long id) {
  return service.eliminar(id).thenReturn(ResponseEntity.noContent().build());
 }
 @GetMapping("/{id}/eventos-citas")
 public Flux<AuditoriaCita> eventosCitas(@PathVariable long id,
   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
  if(id<1 || page<0 || page>1000000 || size<1 || size>100)
   return Flux.error(ReglaException.invalido("Use ID positivo, page >= 0 y size entre 1 y 100."));
  return auditoriaCitas.pagina(id,size,(long)page*size);
 }
 @GetMapping("/{id}/eventos") public Flux<Auditoria> eventos(@PathVariable long id) {
  if(id<1) return Flux.error(ReglaException.invalido("El ID debe ser positivo."));
  // La traza permanece consultable despues de eliminar el paciente.
  return auditoria.findByPacienteIdOrderByFechaConsumoAsc(id);
 }
}
