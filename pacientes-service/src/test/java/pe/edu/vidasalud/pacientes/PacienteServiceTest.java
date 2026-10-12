package pe.edu.vidasalud.pacientes;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PacienteServiceTest {
 PacienteRepository repo; Eventos eventos; PacienteService service;
 @SuppressWarnings("unchecked")
 @BeforeEach void preparar() {
  repo=mock(PacienteRepository.class); eventos=mock(Eventos.class);
  var tx=mock(TransactionalOperator.class);
  when(tx.transactional(any(Mono.class))).thenAnswer(i->i.getArgument(0));
  service=new PacienteService(repo,eventos,tx);
 }
 PacienteRequest datos() { return new PacienteRequest("DEMO","DEMO-UNIT","Paciente","Ficticio",LocalDate.of(2000,1,1),"DEMO-0001","prueba@example.invalid",null,Paciente.Estado.ACTIVO); }
 Paciente existente(long id,String documento) {
  var p=Paciente.nuevo(datos());
  return new Paciente(id,p.tipoDocumento(),documento,p.nombres(),p.apellidos(),p.fechaNacimiento(),p.telefono(),p.correo(),p.direccion(),p.fechaRegistro(),p.fechaActualizacion(),p.estado(),0L);
 }
 @Test void documentoDuplicadoNoGuardaNiPublica() {
  when(repo.findByTipoDocumentoAndNumeroDocumento("DEMO","DEMO-UNIT")).thenReturn(Mono.just(existente(1,"DEMO-UNIT")));
  StepVerifier.create(service.crear(datos())).expectErrorSatisfies(e->{
   assertInstanceOf(ReglaException.class,e); assertEquals(409,((ReglaException)e).status().value());
  }).verify();
  verify(repo,never()).save(any()); verifyNoInteractions(eventos);
 }
 @Test void pacienteInexistenteDevuelve404() {
  when(repo.findById(99L)).thenReturn(Mono.empty());
  StepVerifier.create(service.obtener(99)).expectErrorSatisfies(e->assertEquals(404,((ReglaException)e).status().value())).verify();
 }
 @Test void actualizarNoPermiteTomarDocumentoDeOtroPaciente() {
  when(repo.findById(1L)).thenReturn(Mono.just(existente(1,"DEMO-ANTERIOR")));
  when(repo.findByTipoDocumentoAndNumeroDocumento("DEMO","DEMO-UNIT")).thenReturn(Mono.just(existente(2,"DEMO-UNIT")));
  StepVerifier.create(service.actualizar(1,datos())).expectErrorSatisfies(e->assertEquals(409,((ReglaException)e).status().value())).verify();
  verify(repo,never()).save(any()); verifyNoInteractions(eventos);
 }
 @Test void eliminarInexistenteNoGeneraEvento() {
  when(repo.findById(99L)).thenReturn(Mono.empty());
  StepVerifier.create(service.eliminar(99)).expectError(ReglaException.class).verify();
  verify(repo,never()).delete(any(Paciente.class)); verifyNoInteractions(eventos);
 }
 @Test void rechazaTamanoDePaginaInvalido() {
  StepVerifier.create(service.listar(0,0)).expectErrorSatisfies(e->assertEquals(400,((ReglaException)e).status().value())).verify();
 }
 @Test void falloControladoParaDemostrarCI() {
  assertFalse(Boolean.getBoolean("demo.ci.failure"),"Fallo intencional: vuelva a configurar demo.ci.failure=false.");
 }
}
