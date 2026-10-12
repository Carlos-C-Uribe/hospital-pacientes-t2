# Probar Pacientes y Citas con Bruno

## Abrir la coleccion

1. Inicie el proyecto con scripts/Start.ps1 y espere APLICACION DISPONIBLE.
2. En Bruno seleccione Open Collection (Abrir coleccion).
3. Seleccione la carpeta bruno de su proyecto; contiene bruno.json.
4. Seleccione el entorno Local.
5. Abra las solicitudes numeradas en orden y pulse Send.

La primera solicitud genera un documento ficticio, un codigo de profesional y una fecha futura
valida. Guarda automaticamente pacienteId; crear la cita guarda citaId.
Puede repetir toda la secuencia comenzando por la solicitud 01, que genera otros datos.
Las variables se mantienen durante la sesion; si cierra Bruno, comience otra vez por 01.

## Resultados esperados

| Solicitudes | Resultado |
|---|---|
| 01 crear paciente | 201 |
| 02 consultar paciente / 03 listar | 200 |
| 04 paciente duplicado | 409 |
| 05 crear cita | 201 y PENDIENTE |
| 06 consultar / 07 listar citas | 200 |
| 08 confirmar | 200 y CONFIRMADA |
| 09 reprogramar | 200 y PENDIENTE |
| 10 confirmar nuevamente | 200 y CONFIRMADA |
| 11 cancelar | 204 |
| 12 consultar cancelada | 200 y CANCELADA |
| 13 intentar confirmar cancelada | 409 |
| 14 auditoria de citas | 200 y lista de eventos recibidos |
| 15 actualizar paciente | 200 e INACTIVO |
| 16 eliminar paciente | 204 |
| 17 consultar eliminado | 404 |
| 18 auditoria del paciente | 200 |
| 19 auditoria de citas tras eliminar paciente | 200 |
| 20 cita inexistente | 404 |
| 21 paciente inexistente al crear cita | 404 |
| 22 datos obligatorios ausentes | 400 |

El DELETE de citas conserva la cita CANCELADA. El DELETE de pacientes elimina su registro,
mientras ambas auditorias permanecen consultables.

## Verificar RabbitMQ

Las solicitudes de auditoria pueden necesitar repetir Send unos segundos despues,
porque los eventos se publican y consumen de forma asincrona.
En la auditoria de citas deben aparecer CitaCreada, CitaConfirmada,
CitaReprogramada, otra CitaConfirmada y CitaCancelada, con fechaConsumo.

Abra http://localhost:15672. Use el usuario hospital y la clave RABBIT_PASSWORD de su .env local.
Revise exchange hospital.citas, cola pacientes.citas.auditoria y binding cita.*.
Una cola vacia por si sola no demuestra consumo: muestre tambien la auditoria recibida.

## Evidencias

Capture URL, metodo, cuerpo ficticio y respuesta. Para auditoria incluya tipo, citaId,
pacienteId, origen y fechaConsumo. No capture contrasenas.
Las pruebas de horarios ocupados y reservas simultaneas de Citas se excluyeron por indicacion
del estudiante. Las reglas y restricciones de agenda siguen implementadas.
