CREATE TABLE IF NOT EXISTS cita (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    paciente_id BIGINT NOT NULL CHECK (paciente_id > 0),
    especialidad VARCHAR(80) NOT NULL,
    fecha_hora TIMESTAMPTZ NOT NULL,
    profesional_codigo VARCHAR(30) NOT NULL,
    motivo VARCHAR(200) NOT NULL,
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('PENDIENTE','CONFIRMADA','ATENDIDA','CANCELADA','AUSENTE')),
    fecha_creacion TIMESTAMPTZ NOT NULL,
    fecha_actualizacion TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cita_agenda
    ON cita(profesional_codigo, fecha_hora) WHERE estado <> 'CANCELADA';
CREATE UNIQUE INDEX IF NOT EXISTS uq_cita_paciente_horario
    ON cita(paciente_id, fecha_hora) WHERE estado <> 'CANCELADA';
CREATE INDEX IF NOT EXISTS ix_cita_paciente ON cita(paciente_id, fecha_hora);
CREATE INDEX IF NOT EXISTS ix_cita_estado ON cita(estado, fecha_hora);

CREATE TABLE IF NOT EXISTS outbox (
    id UUID PRIMARY KEY,
    routing_key VARCHAR(60) NOT NULL,
    payload TEXT NOT NULL,
    enviado BOOLEAN NOT NULL DEFAULT FALSE,
    intentos INTEGER NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    siguiente_intento TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_outbox_pendiente
    ON outbox(siguiente_intento, creado_en) WHERE enviado=false;

