$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
Push-Location $projectDir
try {
 if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Instale Docker Desktop antes de iniciar.' }
 $engine = & docker info --format '{{.OSType}}'
 if ($LASTEXITCODE -ne 0 -or $engine -ne 'linux') { throw 'Abra Docker Desktop y seleccione contenedores Linux.' }
 $envPath = Join-Path $projectDir '.env'
 $lines = @()
 if (Test-Path -LiteralPath $envPath) { $lines = @([IO.File]::ReadAllLines($envPath)) }
 foreach ($name in @('DB_PASSWORD','RABBIT_PASSWORD','CITAS_DB_PASSWORD')) {
  $configured = @($lines | Where-Object { $_ -match ('^' + $name + '=.+$') })
  if ($configured.Count -eq 0) {
   $lines = @($lines | Where-Object { $_ -notmatch ('^' + $name + '=') })
   $lines += ($name + '=' + [Guid]::NewGuid().ToString('N'))
  }
 }
 [IO.File]::WriteAllText($envPath, ($lines -join [Environment]::NewLine) + [Environment]::NewLine,
   (New-Object Text.UTF8Encoding($false)))
 & docker compose up --build --detach
 if ($LASTEXITCODE -ne 0) { throw 'Docker no pudo compilar o iniciar. Revise el mensaje anterior.' }
 Write-Host 'Esperando el registro en Eureka y las rutas de pacientes y citas...'
 $ready = $false
 for ($i=0; $i -lt 120; $i++) {
  try {
   $response = Invoke-WebRequest 'http://localhost:8080/api/pacientes?page=0&size=1' -UseBasicParsing -TimeoutSec 5
   $citas = Invoke-WebRequest 'http://localhost:8080/api/citas?page=0&size=1' -UseBasicParsing -TimeoutSec 5
   if ($response.StatusCode -eq 200 -and $citas.StatusCode -eq 200) { $ready=$true; break }
  } catch { Start-Sleep -Seconds 3 }
 }
 if (-not $ready) { throw 'La ruta aun no responde. Ejecute docker compose ps y docker compose logs --tail 80.' }
 Write-Host 'APLICACION DISPONIBLE'
 Write-Host 'API: http://localhost:8080/api/pacientes'
 Write-Host 'Citas: http://localhost:8080/api/citas'
 Write-Host 'Eureka: http://localhost:8761'
 Write-Host 'RabbitMQ: http://localhost:15672 (usuario: hospital)'
 Write-Host 'La clave de RabbitMQ esta en .env. No comparta ese archivo.'
} finally { Pop-Location }
