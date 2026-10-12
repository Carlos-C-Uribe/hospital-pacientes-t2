# Evidencias de Pacientes y Citas

Guarde capturas reales en una carpeta evidencias aparte del codigo.
No capture .env, tokens ni contrasenas.

| Evidencia | Debe mostrar |
|---|---|
| Compilacion y pruebas | clean verify, resumen y BUILD SUCCESS |
| Eureka | API-GATEWAY, PACIENTES-SERVICE y CITAS-SERVICE como UP |
| Gateway | Bruno utiliza localhost:8080 para ambos servicios |
| Pacientes | Crear 201, consultar 200, duplicado 409, actualizar 200, eliminar 204 y consultar eliminado 404 |
| Citas | Crear PENDIENTE, consultar, confirmar, reprogramar a PENDIENTE y cancelar |
| Estado invalido | Intentar confirmar CANCELADA y recibir 409 |
| Validacion sincrona | Paciente inexistente 404 o INACTIVO 409 al intentar crear cita |
| Persistencia | SELECT de paciente en hospital_pacientes y cita en hospital_citas |
| RabbitMQ | Exchange hospital.citas, binding cita.*, cola pacientes.citas.auditoria y consumidor |
| Evento consumido | Auditoria con CitaConfirmada, origen citas-service y fechaConsumo |
| Outbox | Evento enviado=true en hospital_citas |
| Historia | Cita CANCELADA conservada y auditoria consultable tras eliminar paciente |
| Pipeline | Workflow con cuatro modulos, ejecucion verde y reportes |
| Fallo controlado | Rama de demostracion, prueba roja y su correccion verde |
| Diagramas | Arquitectura y secuencia del documento tecnico |

Por indicacion del estudiante no se solicitan pruebas de horario ocupado ni de reservas
simultaneas de Citas. Sus reglas siguen implementadas.

SQL desde la carpeta del proyecto:
```powershell
docker compose exec -T postgres psql -U hospital_pacientes -d hospital_pacientes -c "SELECT id,numero_documento,nombres,estado FROM paciente ORDER BY id;"
docker compose exec -T postgres-citas psql -U hospital_citas -d hospital_citas -c "SELECT id,codigo,paciente_id,fecha_hora,estado FROM cita ORDER BY id;"
docker compose exec -T postgres psql -U hospital_pacientes -d hospital_pacientes -c "SELECT cita_id,paciente_id,tipo,estado_anterior,estado_nuevo,fecha_consumo FROM auditoria_cita ORDER BY id;"
docker compose exec -T postgres-citas psql -U hospital_citas -d hospital_citas -c "SELECT routing_key,enviado,intentos FROM outbox ORDER BY creado_en;"
```

Un SELECT demuestra datos persistidos; para demostrar acceso reactivo muestre tambien
r2dbc-postgresql, ReactiveCrudRepository, Mono/Flux y WebClient.
Una cola vacia por si sola no prueba consumo; muestre la auditoria con fechaConsumo.
