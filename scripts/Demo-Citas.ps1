$ErrorActionPreference='Stop'
$base='http://localhost:8080'
function Request([string]$method,[string]$url,$data,[int]$expected) {
 $requestArgs=@{Method=$method; Uri=$url; UseBasicParsing=$true; TimeoutSec=15}
 if($null -ne $data) { $requestArgs.ContentType='application/json'; $requestArgs.Body=($data | ConvertTo-Json -Depth 8 -Compress) }
 try { $response=Invoke-WebRequest @requestArgs }
 catch {
  if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $expected) {
   Write-Host "$method -> $expected (esperado)"; return $null
  }
  throw
 }
 if([int]$response.StatusCode -ne $expected) { throw "Se esperaba HTTP $expected." }
 Write-Host "$method -> $expected"
 if($response.Content) { return ($response.Content | ConvertFrom-Json) }
 return $null
}
$paciente=@{
 tipoDocumento='DEMO'; numeroDocumento=('DEMO-'+[Guid]::NewGuid().ToString('N').Substring(0,12).ToUpper())
 nombres='Paciente'; apellidos='Ficticio'; fechaNacimiento='2000-01-01'
 telefono='DEMO-0001'; correo='prueba@example.invalid'; estado='ACTIVO'
}
$p=Request 'POST' "$base/api/pacientes" $paciente 201
$pacienteId=$p.id
$dia=[DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(-5)).Date.AddDays(3)
while($dia.DayOfWeek -eq [DayOfWeek]::Sunday) { $dia=$dia.AddDays(1) }
$cita=@{
 pacienteId=$pacienteId; especialidad='Medicina general'
 fechaHora=($dia.ToString('yyyy-MM-dd')+'T10:00:00-05:00')
 profesionalCodigo=('DEMO-'+[Guid]::NewGuid().ToString('N').Substring(0,12).ToUpper())
 motivo='Reserva administrativa ficticia'
}
$c=Request 'POST' "$base/api/citas" $cita 201
$citaId=$c.id
Request 'GET' "$base/api/citas/$citaId" $null 200 | Out-Null
Request 'GET' "$base/api/citas?pacienteId=$pacienteId" $null 200 | Out-Null
Request 'PATCH' "$base/api/citas/$citaId/estado" @{estado='CONFIRMADA'} 200 | Out-Null
$reprogramacion=@{
 especialidad=$cita.especialidad; profesionalCodigo=$cita.profesionalCodigo
 fechaHora=($dia.AddDays(7).ToString('yyyy-MM-dd')+'T10:00:00-05:00')
 motivo='Reprogramacion administrativa ficticia'
}
$reprogramada=Request 'PUT' "$base/api/citas/$citaId" $reprogramacion 200
if($reprogramada.estado -ne 'PENDIENTE') { throw 'La reprogramacion debe quedar PENDIENTE.' }
Request 'PATCH' "$base/api/citas/$citaId/estado" @{estado='CONFIRMADA'} 200 | Out-Null
Request 'DELETE' "$base/api/citas/$citaId" $null 204 | Out-Null
$cancelada=Request 'GET' "$base/api/citas/$citaId" $null 200
if($cancelada.estado -ne 'CANCELADA') { throw 'La cita cancelada debe conservarse.' }
Request 'PATCH' "$base/api/citas/$citaId/estado" @{estado='CONFIRMADA'} 409 | Out-Null
$recibidos=@()
for($i=0; $i -lt 80; $i++) {
 $recibidos=@(Invoke-RestMethod "$base/api/pacientes/$pacienteId/eventos-citas?size=100" -TimeoutSec 10)
 if($recibidos.Count -ge 5) { break }
 Start-Sleep -Milliseconds 500
}
foreach($tipo in @('CitaCreada','CitaConfirmada','CitaReprogramada','CitaCancelada')) {
 if(-not ($recibidos | Where-Object { $_.tipo -eq $tipo -and $_.origen -eq 'citas-service' -and $_.fechaConsumo })) {
  throw "No se consumio $tipo. Revise RabbitMQ y outbox."
 }
}
if(@($recibidos | Where-Object { $_.tipo -eq 'CitaConfirmada' }).Count -ne 2) {
 throw 'Deben existir dos confirmaciones, una antes y otra despues de reprogramar.'
}
Request 'DELETE' "$base/api/pacientes/$pacienteId" $null 204 | Out-Null
Request 'GET' "$base/api/pacientes/$pacienteId" $null 404 | Out-Null
Request 'GET' "$base/api/pacientes/$pacienteId/eventos-citas" $null 200 | Out-Null
Write-Host 'DEMO PACIENTES, CITAS Y RABBITMQ EXITOSA'
Write-Host "Paciente utilizado: $pacienteId; cita: $citaId"
$recibidos | Select-Object citaId,tipo,estadoAnterior,estadoNuevo,origen,fechaConsumo | Format-Table -AutoSize

