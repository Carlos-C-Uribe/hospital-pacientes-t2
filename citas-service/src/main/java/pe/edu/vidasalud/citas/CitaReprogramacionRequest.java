package pe.edu.vidasalud.citas;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.*;

public record CitaReprogramacionRequest(
        @NotBlank @Size(max = 80) String especialidad,
        @NotNull OffsetDateTime fechaHora,
        @NotBlank @Pattern(regexp = "DEMO-[A-Z0-9-]{1,24}",
                message = "Use un codigo ficticio como DEMO-MEDICO01") String profesionalCodigo,
        @NotBlank @Size(max = 200) String motivo) {}

