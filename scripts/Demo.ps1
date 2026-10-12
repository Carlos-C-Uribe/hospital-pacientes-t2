$ErrorActionPreference='Stop'
$base='http://localhost:8080/api/pacientes'
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
$data=@{
 tipoDocumento='DEMO'; numeroDocumento=('DEMO-'+[Guid]::NewGuid().ToString('N').Substring(0,12).ToUpper())
 nombres='Paciente'; apellidos='Ficticio'; fechaNacimiento='2000-01-01'
 telefono='DEMO-0001'; correo='prueba@example.invalid'; direccion='Direccion de demostracion'; estado='ACTIVO'
}
$p=Request 'POST' $base $data 201
$id=$p.id
Request 'GET' "$base/$id" $null 200 | Out-Null
Request 'GET' "$($base)?page=0&size=20" $null 200 | Out-Null
Request 'POST' $base $data 409 | Out-Null
$data.nombres=''
Request 'POST' $base $data 400 | Out-Null
$data.nombres='Paciente Actualizado'; $data.estado='INACTIVO'
Request 'PUT' "$base/$id" $data 200 | Out-Null
Request 'DELETE' "$base/$id" $null 204 | Out-Null
Request 'GET' "$base/$id" $null 404 | Out-Null
Request 'PUT' "$base/$id" $data 404 | Out-Null
Request 'DELETE' "$base/$id" $null 404 | Out-Null
$received=@()
for($i=0; $i -lt 80; $i++) {
 $received=@(Invoke-RestMethod "$base/$id/eventos" -TimeoutSec 10)
 if($received.Count -ge 3) { break }
 Start-Sleep -Milliseconds 500
}
foreach($type in @('PacienteRegistrado','PacienteActualizado','PacienteEliminado')) {
 if(-not ($received | Where-Object { $_.tipo -eq $type })) { throw "No se recibio $type. Revise RabbitMQ y outbox." }
}
Write-Host 'DEMO CRUD Y RABBITMQ EXITOSA'
Write-Host "Paciente utilizado: $id"
Write-Host "Eventos consumidos: $base/$id/eventos"
$received | Select-Object eventoId,tipo,estadoAnterior,estadoNuevo,fechaEvento,fechaConsumo | Format-Table -AutoSize
