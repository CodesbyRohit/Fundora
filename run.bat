@echo off
echo ==========================================================
echo Starting Fundora Social FinTech Web Application...
echo ==========================================================
cd /d "%~dp0"

rem Prefer Maven on PATH; fall back to the copy bundled with IntelliJ IDEA Community 2025.1.3
set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"

call "%MVN%" spring-boot:run
pause
