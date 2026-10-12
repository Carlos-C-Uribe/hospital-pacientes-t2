# Hospital Vida Salud - Pacientes, Citas e integracion continua

Proyecto academico individual de Desarrollo de Aplicaciones Web II, evaluacion T2.
Alcance aprobado: CRUD de Pacientes, gestion administrativa de Citas, Spring Cloud Gateway,
Eureka, RabbitMQ, PostgreSQL/R2DBC y GitHub Actions.
Java 21, Spring Boot 4.0.8 y Spring Cloud 2025.1.2.

Repositorio: [hospital-pacientes-t2](https://github.com/Carlos-C-Uribe/hospital-pacientes-t2).

## Estado verificable

Consulte ESTADO_VERIFICACION.md para distinguir las comprobaciones realizadas de las pendientes.
La version anterior de Pacientes supero 16 pruebas locales. Ese resultado corresponde a esa version.
La ampliacion incorpora pruebas de Citas y de su integracion con Pacientes.
Por indicacion del estudiante, se conservan las reglas de agenda, pero se omiten las pruebas
de horarios ocupados y reservas simultaneas de Citas.

## Aplicaciones y responsabilidades

| Modulo | Responsabilidad | Puerto interno |
|---|---|---|
| pacientes-service | CRUD de pacientes y consulta de auditoria administrativa | 8081 |
| citas-service | Programacion, consulta, confirmacion, reprogramacion y cancelacion | 8082 |
| api-gateway | Entrada HTTP y enrutamiento por nombres de Eureka | 8080 |
| eureka-server | Registro y descubrimiento de instancias | 8761 |

Cada servicio de negocio dispone de su propia base PostgreSQL.
Citas consulta a Pacientes con WebClient antes de crear, reprogramar o confirmar.
Citas publica sus eventos por RabbitMQ; Pacientes consume una copia para auditoria.
El productor guarda el cambio y su evento pendiente en una misma transaccion R2DBC.

## Requisitos

- JDK 21 y JAVA_HOME configurado.
- Docker Desktop abierto, con contenedores Linux.
- Internet para las primeras descargas.
- Git y cuenta GitHub para publicar y demostrar CI.
- Bruno para las pruebas manuales.

PostgreSQL y RabbitMQ se inician con Docker Compose.

## Actualizar la version existente

Lea docs/actualizacion.md. La carpeta original puede seguir llamandose hospital-pacientes-t2.
La configuracion de Compose mantiene el nombre hospital-pacientes y sus volumenes originales.
La base hospital_citas usa un volumen nuevo; Start.ps1 agrega CITAS_DB_PASSWORD a .env
y conserva DB_PASSWORD y RABBIT_PASSWORD cuando ya tienen valor.

## Compilar y probar

Abra PowerShell en la carpeta que contiene este README y el pom.xml principal.

Si Maven Wrapper todavia no esta preparado:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Preparar.ps1
```

Con Docker Desktop abierto:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify.ps1
```

Equivalente:
```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Solo pruebas unitarias y de controlador, sin Docker:
```powershell
.\mvnw.cmd test
```

Ejecute verify desde la raiz. CitasIT utiliza el ejecutable de Pacientes que el mismo reactor
compila previamente y crea dos bases PostgreSQL y RabbitMQ temporales con Testcontainers.
Mockito se carga como agente al iniciar las JVM de pruebas para evitar la conexion dinamica
que algunas sesiones de Windows restringen.

## Iniciar y detener

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start.ps1
```

Espere APLICACION DISPONIBLE.

- Pacientes: http://localhost:8080/api/pacientes
- Citas: http://localhost:8080/api/citas
- Eureka: http://localhost:8761
- RabbitMQ: http://localhost:15672

RabbitMQ utiliza el usuario hospital y la clave RABBIT_PASSWORD de su .env local.
Los servicios se publican solo en 127.0.0.1; las bases y el puerto AMQP permanecen internos.
Nunca suba .env ni comparta sus claves.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Stop.ps1
```

Los volumenes conservan los datos.

## Probar con Bruno

Abra la coleccion incluida en la carpeta bruno y seleccione el entorno Local.
Siga docs/bruno.md. Las solicitudes numeradas crean sus datos ficticios y guardan los ID
para las siguientes solicitudes. La fecha de cita se genera en el futuro.

## Demostraciones automaticas

CRUD original de Pacientes:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Demo.ps1
```

Flujo de Pacientes y Citas, incluyendo eventos consumidos:
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Demo-Citas.ps1
```

## Endpoints de Citas

| Metodo | Ruta | Resultado |
|---|---|---|
| POST | /api/citas | 201 y Location |
| GET | /api/citas?page=0&size=20&pacienteId=1&estado=PENDIENTE | 200; filtros opcionales |
| GET | /api/citas/{id} | 200 o 404 |
| PUT | /api/citas/{id} | 200; reprogramar devuelve a PENDIENTE |
| PATCH | /api/citas/{id}/estado | 200; validar transicion |
| DELETE | /api/citas/{id} | 204; cancela conservando el registro |
| GET | /api/pacientes/{id}/eventos-citas?page=0&size=20 | 200; auditoria recibida por RabbitMQ |

La agenda usa UTC-5, lunes a sabado, turnos de 30 minutos, de 08:00 a 17:30.
CANCELADA, ATENDIDA y AUSENTE son finales. Las citas canceladas liberan su horario.
Un paciente eliminado o INACTIVO impide nuevas reservas, reprogramaciones y confirmaciones.
Las referencias historicas se conservan; la eliminacion del paciente no cancela automaticamente sus citas.
No se implementan decisiones clinicas.

## Consultar SQL

```powershell
docker compose exec -T postgres psql -U hospital_pacientes -d hospital_pacientes -c "SELECT id,numero_documento,estado FROM paciente ORDER BY id;"
docker compose exec -T postgres-citas psql -U hospital_citas -d hospital_citas -c "SELECT id,codigo,paciente_id,fecha_hora,estado FROM cita ORDER BY id;"
docker compose exec -T postgres psql -U hospital_pacientes -d hospital_pacientes -c "SELECT cita_id,paciente_id,tipo,fecha_consumo FROM auditoria_cita ORDER BY id;"
docker compose exec -T postgres-citas psql -U hospital_citas -d hospital_citas -c "SELECT id,routing_key,enviado,intentos FROM outbox ORDER BY creado_en;"
```

## GitHub Actions

El workflow .github/workflows/ci.yml se ejecuta ante push y pull request hacia main.
Ejecuta clean verify de los cuatro modulos y guarda los cuatro .jar y reportes.
docs/ci.md explica el fallo controlado y su correccion. El alcance es CI; no incluye despliegue automatico.

## Documentacion y diagnostico

- docs/tecnico.md: analisis, arquitectura, datos, contratos y decisiones.
- docs/bruno.md: guia para probar paso a paso.
- docs/actualizacion.md: instalar la ampliacion sobre la carpeta existente.
- docs/evidencias.md: capturas reales requeridas.
- docs/solicitudes.http: ejemplos HTTP.
- docs/ci.md: GitHub y pipeline.

```powershell
docker compose ps
docker compose logs --tail 80 citas-service
docker compose logs --tail 80 pacientes-service
docker compose logs --tail 80 api-gateway
```

Si una ruta devuelve 503 al iniciar, espere el registro y propagacion en Eureka.
