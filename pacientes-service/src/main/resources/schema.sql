CREATE TABLE IF NOT EXISTS paciente (
 id BIGSERIAL PRIMARY KEY,
 tipo_documento VARCHAR(30) NOT NULL,
 numero_documento VARCHAR(30) NOT NULL,
 nombres VARCHAR(80) NOT NULL,
 apellidos VARCHAR(100) NOT NULL,
 fecha_nacimiento DATE NOT NULL,
 telefono VARCHAR(30) NOT NULL,
 correo VARCHAR(160) NOT NULL,
 direccion VARCHAR(200),
 fecha_registro TIMESTAMPTZ NOT NULL,
 fecha_actualizacion TIMESTAMPTZ NOT NULL,
 estado VARCHAR(10) NOT NULL CHECK (estado IN ('ACTIVO','INACTIVO')),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_paciente_documento UNIQUE(tipo_documento,numero_documento)
);
CREATE TABLE IF NOT EXISTS outbox (
 id UUID PRIMARY KEY,
 routing_key VARCHAR(60) NOT NULL,
 payload TEXT NOT NULL,
 enviado BOOLEAN NOT NULL DEFAULT FALSE,
 intentos INTEGER NOT NULL DEFAULT 0,
 creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 siguiente_intento TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_outbox_pendiente ON outbox(siguiente_intento,creado_en) WHERE enviado=false;
CREATE TABLE IF NOT EXISTS auditoria (
 id BIGSERIAL PRIMARY KEY,
 evento_id UUID NOT NULL UNIQUE,
 paciente_id BIGINT NOT NULL,
 tipo VARCHAR(60) NOT NULL,
 estado_anterior VARCHAR(30) NOT NULL,
 estado_nuevo VARCHAR(30) NOT NULL,
 fecha_evento TIMESTAMPTZ NOT NULL,
 fecha_consumo TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 origen VARCHAR(60) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_auditoria_paciente ON auditoria(paciente_id,fecha_consumo);
CREATE TABLE IF NOT EXISTS auditoria_cita (
 id BIGSERIAL PRIMARY KEY,
 evento_id UUID NOT NULL UNIQUE,
 cita_id BIGINT NOT NULL,
 codigo_cita VARCHAR(50) NOT NULL,
 paciente_id BIGINT NOT NULL,
 tipo VARCHAR(60) NOT NULL,
 estado_anterior VARCHAR(30) NOT NULL,
 estado_nuevo VARCHAR(30) NOT NULL,
 fecha_hora TIMESTAMPTZ NOT NULL,
 fecha_evento TIMESTAMPTZ NOT NULL,
 fecha_consumo TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 origen VARCHAR(60) NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_auditoria_cita_paciente ON auditoria_cita(paciente_id,fecha_evento,id);
