package pe.edu.vidasalud.pacientes;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
@Service
public class PacienteService {
 private final PacienteRepository repo; private final Eventos eventos; private final TransactionalOperator tx;
 public PacienteService(PacienteRepository repo,Eventos eventos,TransactionalOperator tx) {
  this.repo=repo; this.eventos=eventos; this.tx=tx;
 }
 public Mono<Paciente> obtener(long id) {
  if(id<1) return Mono.error(ReglaException.invalido("El ID debe ser positivo."));
  return repo.findById(id).switchIfEmpty(Mono.error(ReglaException.inexistente()));
 }
 public Flux<Paciente> listar(int page,int size) {
  if(page<0 || page>1000000 || size<1 || size>100) return Flux.error(ReglaException.invalido("Use page >= 0 y size entre 1 y 100."));
  return repo.pagina(size,(long)page*size);
 }
 public Mono<Paciente> crear(PacienteRequest r) {
  return repo.findByTipoDocumentoAndNumeroDocumento(r.tipoDocumento(),r.numeroDocumento())
   .flatMap(p->Mono.<Paciente>error(ReglaException.duplicado()))
   .switchIfEmpty(Mono.defer(()->repo.save(Paciente.nuevo(r))
    .flatMap(p->eventos.registrar(p,Eventos.Tipo.REGISTRADO,"NO_EXISTE",p.estado().name()).thenReturn(p))))
   .as(tx::transactional)
   .onErrorMap(DataIntegrityViolationException.class,e->ReglaException.duplicado());
 }
 public Mono<Paciente> actualizar(long id,PacienteRequest r) {
  return obtener(id).flatMap(anterior->
   repo.findByTipoDocumentoAndNumeroDocumento(r.tipoDocumento(),r.numeroDocumento())
    .filter(otro->!otro.id().equals(id))
    .flatMap(otro->Mono.<Paciente>error(ReglaException.duplicado()))
    .switchIfEmpty(Mono.defer(()->repo.save(anterior.actualizar(r))
     .flatMap(p->eventos.registrar(p,Eventos.Tipo.ACTUALIZADO,anterior.estado().name(),p.estado().name()).thenReturn(p)))))
   .as(tx::transactional)
   .onErrorMap(DataIntegrityViolationException.class,e->ReglaException.duplicado());
 }
 public Mono<Void> eliminar(long id) {
  return obtener(id).flatMap(p->repo.delete(p)
   .then(eventos.registrar(p,Eventos.Tipo.ELIMINADO,p.estado().name(),"ELIMINADO")))
   .as(tx::transactional);
 }
}
