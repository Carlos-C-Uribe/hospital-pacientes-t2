# GitHub Actions y demostracion del pipeline

## 1. Que es un pipeline o caneria

Es una secuencia automatica:
codigo -> Java 21 -> Maven -> compilacion -> pruebas -> ejecutables y reportes.
Se implementa con .github/workflows/ci.yml.
Se trata de CI para el alcance aprobado; el resultado final son archivos compilados.

## 2. Crear cuenta y repositorio

1. Instale Git para Windows desde https://git-scm.com/downloads/win.
2. Cree una cuenta en https://github.com/.
3. En GitHub seleccione New repository.
4. Nombre sugerido: hospital-pacientes-t2.
5. Para revision sencilla puede elegir Public, porque solo usaremos datos ficticios.
6. Cree el repositorio vacio, sin README, gitignore ni licencia adicionales.
7. Copie su direccion HTTPS.

## 3. Publicar el proyecto

Antes de usar Git ejecute Preparar.ps1 para obtener Maven Wrapper. Compruebe que
existan mvnw, mvnw.cmd y .mvn/wrapper/maven-wrapper.properties.

Abra PowerShell en la carpeta del README.
Sustituya NOMBRE, CORREO y USUARIO por sus datos de Git, no por datos hospitalarios.
Use su correo de GitHub o su correo privado noreply indicado en la configuracion de GitHub.

```powershell
git config --global user.name "NOMBRE"
git config --global user.email "CORREO"
git init -b main
git add .
git status
git commit -m "Implementar pacientes, citas y pipeline CI"
git remote add origin https://github.com/USUARIO/hospital-pacientes-t2.git
git push -u origin main
```

git status no debe incluir .env, target ni secretos.
La autenticacion puede realizarse en el navegador mediante Git Credential Manager.
No agregue tokens a la URL ni al workflow.

## 4. Revisar la primera ejecucion

En el repositorio abra Actions -> CI - Hospital Pacientes y Citas.
Abra la ejecucion del commit. Espere su resultado.
Revise Preparar JDK 21 y Compilar y ejecutar pruebas de todos los modulos.
Si es exitosa, el resumen incluye reportes-pruebas y ejecutables-java21.

Maven Wrapper obtiene Maven 3.9.11. setup-java prepara Temurin 21 y cache Maven.
Las pruebas Testcontainers crean PostgreSQL y RabbitMQ efimeros con claves aleatorias,
sin depender de un servidor instalado en la computadora del profesor.

## 5. Demostrar un fallo controlado

Desde main y despues de tener una ejecucion exitosa:
```powershell
git switch -c demo/fallo-ci
```

Abra .github/workflows/ci.yml y cambie unicamente:
DEMO_CI_FAILURE: 'false'
por:
DEMO_CI_FAILURE: 'true'

Guarde y ejecute:
```powershell
git add .github/workflows/ci.yml
git commit -m "Demostrar deteccion de fallo de prueba en CI"
git push -u origin demo/fallo-ci
```

Actions se ejecuta porque el workflow acepta push en todas las ramas.
Debe fallar PacienteServiceTest.falloControladoParaDemostrarCI.
Guarde una captura de la ejecucion roja y del mensaje del fallo.

## 6. Corregir y demostrar exito

Restaure DEMO_CI_FAILURE: 'false', guarde y ejecute:
```powershell
git add .github/workflows/ci.yml
git commit -m "Corregir fallo controlado y recuperar CI"
git push
```

Espere una ejecucion exitosa. Capture el resultado verde.
En GitHub cree un pull request desde demo/fallo-ci hacia main.
El evento pull_request ejecuta de nuevo la compilacion y pruebas.
Revise ese resultado antes de integrar la rama.

El fallo es una demostracion controlada mediante una prueba real, no una captura simulada.
No integre la version con DEMO_CI_FAILURE=true.

## 7. Evitar integrar una version defectuosa

Un workflow rojo informa el error; por si solo no impide que una persona haga merge.
Cuando la cuenta y el repositorio permitan la configuracion, proteja main mediante
Settings -> Rules o Branches, y exija el check compilar-probar antes de integrar.
Tambien revise que el cambio corregido tenga una ejecucion verde del commit actual.

## 8. Evidencias

Repositorio accesible; archivo workflow; ejecucion exitosa; logs de Maven;
fallo controlado; correccion; pull request y resultado; reportes y .jar generados.

Fuentes oficiales:
- CI: https://docs.github.com/en/actions/get-started/continuous-integration
- Checkout: https://github.com/actions/checkout
- JDK: https://github.com/actions/setup-java
- Artefactos: https://github.com/actions/upload-artifact
- Maven Wrapper: https://maven.apache.org/tools/wrapper/

## Ampliacion de Citas

El reactor incorpora citas-service despues de pacientes-service. clean verify compila y prueba los cuatro modulos.
CitasIT arranca Pacientes desde su JAR recien construido y verifica HTTP y RabbitMQ entre ambas aplicaciones.
El artefacto ejecutables-java21 incluye tambien citas-service-1.0.0.jar.
Por indicacion del estudiante se omiten las pruebas de horarios ocupados y reservas simultaneas de Citas; sus reglas permanecen.
