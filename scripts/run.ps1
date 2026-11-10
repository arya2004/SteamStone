$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    throw 'Set JAVA_HOME to JDK 25 first. See README.md.'
}
& .\mvnw.cmd -q javafx:run
exit $LASTEXITCODE
