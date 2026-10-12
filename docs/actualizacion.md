# Actualizar el proyecto existente

Esta ampliacion agrega Citas al proyecto que ya funciona. Conserve el nombre y la ubicacion
de la carpeta original para continuar con su mismo repositorio Git.

## Si los archivos ya fueron actualizados por Codex

1. Abra Docker Desktop.
2. Abra PowerShell en Desktop/hospital-pacientes-t2.
3. Ejecute scripts/Verify.ps1.
4. Si termina en BUILD SUCCESS, ejecute scripts/Start.ps1.
5. Abra Eureka: debe mostrar API-GATEWAY, PACIENTES-SERVICE y CITAS-SERVICE como UP.
6. Abra Bruno y seleccione la coleccion de la carpeta bruno.
7. Siga docs/bruno.md antes de publicar.

## Si va a utilizar el ZIP

1. Extraiga el ZIP en una carpeta temporal.
2. Copie el contenido de hospital-pacientes-citas-t2 dentro de su carpeta original,
   aceptando reemplazar los archivos del proyecto.
3. La entrega no contiene .env, .git, .vs, target ni datos de las bases.
4. Su .git y .env locales permanecen en la carpeta original.
5. Siga los pasos de verificacion e inicio anteriores.

## Conservacion de datos

Compose mantiene el nombre hospital-pacientes y los volumenes postgres-data y rabbit-data.
La nueva base utiliza citas-postgres-data. El inicio agrega unicamente la clave faltante
CITAS_DB_PASSWORD; conserva las claves anteriores que ya tienen valor.
El schema.sql de Pacientes agrega la tabla auditoria_cita mediante CREATE TABLE IF NOT EXISTS.

No elimine los volumenes para actualizar. Stop.ps1 usa docker compose down y los conserva.
Mantenga .env excluido de Git, y deje .vs/ en .gitignore.

## Retomar Git

Como habia archivos preparados para el primer commit, vuelva a ejecutar git add . despues de
la actualizacion y de verificar las pruebas. git status mostrara la version nueva preparada.
El commit y el push siguen pendientes hasta que complete la comprobacion local.
