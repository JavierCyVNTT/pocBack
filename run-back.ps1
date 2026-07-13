param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"

$javaHome = "C:\Users\dapoggio\javaportatil\java11\zulu11.88.17-ca-jdk11.0.31-win_x64"
$javaExe = Join-Path $javaHome "bin\java.exe"

if (-not (Test-Path -LiteralPath $javaExe)) {
    throw "No se encontro java.exe en: $javaExe"
}

$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

Set-Location -LiteralPath $PSScriptRoot

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    $MavenArgs = @("clean", "install", "-PautoInstallSinglePackage")
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "MavenArgs=$($MavenArgs -join ' ')"
& java -version

& mvn @MavenArgs
exit $LASTEXITCODE
