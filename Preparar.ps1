$ErrorActionPreference='Stop'
$utf8=New-Object Text.UTF8Encoding($false)
$downloads=Join-Path $PSScriptRoot '.bootstrap'
[IO.Directory]::CreateDirectory($downloads) | Out-Null
$archive=Join-Path $downloads 'maven-wrapper.zip'
$unpacked=Join-Path $downloads ([Guid]::NewGuid().ToString('N'))
[Net.ServicePointManager]::SecurityProtocol=[Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
$downloaded=$false
foreach($hostName in @('repo.maven.apache.org','repo1.maven.org')) {
 try {
  $url="https://$hostName/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-only-script.zip"
  Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing
  $downloaded=$true
  break
 } catch { Write-Host "No se pudo descargar desde $hostName. Probando otra direccion oficial..." }
}
if(-not $downloaded) { throw 'No se pudo obtener Maven Wrapper. Revise la conexion a Internet.' }
Expand-Archive -LiteralPath $archive -DestinationPath $unpacked
$cmd=Get-ChildItem -LiteralPath $unpacked -Recurse -File -Filter 'mvnw.cmd' | Select-Object -First 1
if(-not $cmd) { throw 'La descarga no contiene Maven Wrapper.' }
Copy-Item -LiteralPath $cmd.FullName -Destination (Join-Path $PSScriptRoot 'mvnw.cmd')
Copy-Item -LiteralPath (Join-Path $cmd.DirectoryName 'mvnw') -Destination (Join-Path $PSScriptRoot 'mvnw')
$wrapperDir=Join-Path $PSScriptRoot '.mvn\wrapper'
[IO.Directory]::CreateDirectory($wrapperDir) | Out-Null
$lines=@('wrapperVersion=3.3.4','distributionType=only-script','distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip')
[IO.File]::WriteAllText((Join-Path $wrapperDir 'maven-wrapper.properties'), ($lines -join [Environment]::NewLine) + [Environment]::NewLine, $utf8)
Write-Host 'MAVEN WRAPPER PREPARADO'
Write-Host 'Ahora ejecute scripts/Verify.ps1 con JDK 21 y Docker Desktop.'
