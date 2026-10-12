$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
 & docker compose down
 if($LASTEXITCODE -ne 0) { throw 'No se pudo detener el proyecto.' }
 Write-Host 'Servicios detenidos. Los datos se conservan en los volumenes.'
} finally { Pop-Location }
