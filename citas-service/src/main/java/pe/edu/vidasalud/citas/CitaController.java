package pe.edu.vidasalud.citas;

import java.net.URI;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/citas")
public class CitaController {
    private final CitaService service;
    public CitaController(CitaService service) { this.service = service; }

    @PostMapping
    public Mono<ResponseEntity<Cita>> crear(@Valid @RequestBody CitaRequest request) {
        return service.crear(request).map(cita -> ResponseEntity
                .created(URI.create("/api/citas/" + cita.id())).body(cita));
    }

    @GetMapping
    public Flux<Cita> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long pacienteId,
            @RequestParam(required = false) Cita.Estado estado) {
        return service.listar(page, size, pacienteId, estado);
    }

    @GetMapping("/{id}")
    public Mono<Cita> obtener(@PathVariable long id) { return service.obtener(id); }

    @PutMapping("/{id}")
    public Mono<Cita> reprogramar(@PathVariable long id,
            @Valid @RequestBody CitaReprogramacionRequest request) {
        return service.reprogramar(id, request);
    }

    @PatchMapping("/{id}/estado")
    public Mono<Cita> cambiarEstado(@PathVariable long id,
            @Valid @RequestBody CitaEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> cancelar(@PathVariable long id) {
        return service.cancelar(id).thenReturn(ResponseEntity.noContent().build());
    }
}

