package pe.edu.vidasalud.citas;

import jakarta.validation.constraints.NotNull;

public record CitaEstadoRequest(@NotNull Cita.Estado estado) {}

