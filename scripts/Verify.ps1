$ErrorActionPreference='Stop'
$projectDir=Split-Path -Parent $PSScriptRoot
Push-Location $projectDir
try {
 if(-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Configure JDK 21 en PATH y JAVA_HOME.' }
 if(-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Las pruebas de integracion necesitan Docker Desktop.' }
 & docker info --format '{{.OSType}}'
 if($LASTEXITCODE -ne 0) { throw 'Abra Docker Desktop antes de ejecutar las pruebas.' }
 & .\mvnw.cmd --batch-mode --no-transfer-progress clean verify
 if($LASTEXITCODE -ne 0) { throw 'La verificacion fallo. Revise el primer error de Maven.' }
 $tests=0; $failures=0; $errors=0; $skipped=0
 foreach($module in @('eureka-server','api-gateway','pacientes-service','citas-service')) {
  foreach($kind in @('surefire-reports','failsafe-reports')) {
   $reportDir=Join-Path $projectDir "$module\target\$kind"
   if(Test-Path -LiteralPath $reportDir) {
    foreach($report in (Get-ChildItem -LiteralPath $reportDir -Filter 'TEST-*.xml' -File)) {
     [xml]$xml=[IO.File]::ReadAllText($report.FullName)
     $tests += [int]$xml.testsuite.tests
     $failures += [int]$xml.testsuite.failures
     $errors += [int]$xml.testsuite.errors
     $skipped += [int]$xml.testsuite.skipped
    }
   }
  }
 }
 $lines=@('# Verificacion local - Pacientes y Citas','','Compilacion y pruebas: exitosas.',
  ('Fecha: '+(Get-Date -Format o)),'Comando: mvnw.cmd clean verify',
  "Pruebas: $tests; fallos: $failures; errores: $errors; omitidas: $skipped.",
  'Las pruebas de horarios ocupados y reservas simultaneas de Citas no se incluyeron por indicacion del estudiante.',
  'Sus reglas y restricciones SQL se conservan.',
  'GitHub Actions y capturas de la ampliacion: pendientes de comprobar.')
 [IO.File]::WriteAllText((Join-Path $projectDir 'ESTADO_VERIFICACION.md'), ($lines -join [Environment]::NewLine), (New-Object Text.UTF8Encoding($false)))
 Write-Host 'COMPILACION Y PRUEBAS EXITOSAS'
 Write-Host 'Ejecutables disponibles en las carpetas target de cada modulo.'
} finally { Pop-Location }
