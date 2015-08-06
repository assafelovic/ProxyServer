@ECHO OFF
SET BINDIR=%~dp0
CD /D "%BINDIR%"
java -jar "Proxy.jar" "policy.ini"
PAUSE