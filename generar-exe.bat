@echo off
setlocal
rem Carpeta donde queda el ejecutable (antes se llamaba dist)
set "DEST=Ejecutable"
cd /d "%~dp0"
title SysVentaRopa - Generar .jar y .exe
echo ============================================================
echo   SysVentaRopa - Empaquetado en .JAR y .EXE
echo ============================================================
echo.

rem ---------------------------------------------------------------
rem 1) Buscar un JDK 21 o superior que tenga jpackage
rem    (primero JAVA_HOME, luego los JDK que descarga IntelliJ en
rem    C:\Users\<usuario>\.jdks, luego Program Files)
rem ---------------------------------------------------------------
set "JDK="
call :probar "%JAVA_HOME%"
for /d %%d in ("%USERPROFILE%\.jdks\*") do call :probar "%%~d"
for /d %%d in ("C:\Program Files\Java\*" "C:\Program Files\Eclipse Adoptium\*" "C:\Program Files\Microsoft\*" "C:\Program Files\Amazon Corretto\*" "C:\Program Files\Zulu\*" "C:\Program Files\BellSoft\*") do call :probar "%%~d"

if not defined JDK (
    echo [ERROR] No se encontro un JDK 21 o superior.
    echo         En IntelliJ: File ^> Project Structure ^> SDK ^> Download JDK ^(version 21^)
    echo         o configure la variable JAVA_HOME con la carpeta del JDK 21.
    pause
    exit /b 1
)
echo [1/4] JDK encontrado: %JDK%
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%PATH%"

rem ---------------------------------------------------------------
rem Respaldo de la base de datos del ejecutable anterior (fuera de target,
rem porque "mvn clean" borra target)
rem ---------------------------------------------------------------
set "RESPALDO=%TEMP%\SysVentaRopa_data_respaldo"
if exist "%RESPALDO%" rmdir /s /q "%RESPALDO%"
if exist "%DEST%\SysVentaRopa\data" (
    xcopy /e /i /q /y "%DEST%\SysVentaRopa\data" "%RESPALDO%" >nul
    echo       Base de datos respaldada en %RESPALDO%
)

rem ---------------------------------------------------------------
rem 2) Compilar y crear el .jar con todas las librerias
rem ---------------------------------------------------------------
echo [2/4] Compilando y creando target\SysVentaRopa.jar (puede tardar la primera vez)...
call mvnw.cmd -q clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Fallo la compilacion. Cierre la aplicacion si esta abierta y revise los mensajes de arriba.
    pause
    exit /b 1
)
if not exist "target\SysVentaRopa.jar" (
    echo [ERROR] No se genero target\SysVentaRopa.jar
    pause
    exit /b 1
)

rem ---------------------------------------------------------------
rem 3) Crear el .exe (incluye Java: funciona en PCs sin Java instalado)
rem ---------------------------------------------------------------
echo [3/4] Creando el ejecutable .exe con jpackage...
if exist "target\paquete" rmdir /s /q "target\paquete"
mkdir "target\paquete"
copy /y "target\SysVentaRopa.jar" "target\paquete\" >nul
if exist "%DEST%\SysVentaRopa" rmdir /s /q "%DEST%\SysVentaRopa"
if exist "%DEST%\SysVentaRopa" (
    echo [ERROR] No se pudo reemplazar %DEST%\SysVentaRopa. Cierre SysVentaRopa si esta abierto y vuelva a intentar.
    pause
    exit /b 1
)
if not exist "%DEST%" mkdir "%DEST%"
rem Borra la carpeta "dist" de versiones anteriores del script
if exist "dist" rmdir /s /q "dist"

"%JDK%\bin\jpackage.exe" --type app-image ^
  --name SysVentaRopa ^
  --app-version 1.0 ^
  --vendor "Grupo SysVentaRopa" ^
  --description "Sistema de ventas e inventario para tienda de ropa" ^
  --input target\paquete ^
  --main-jar SysVentaRopa.jar ^
  --main-class pe.edu.upeu.sysventas.App ^
  --icon sysventaropa.ico ^
  --add-modules java.se,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,jdk.unsupported,jdk.zipfs,jdk.net,jdk.management ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --dest "%DEST%"
if errorlevel 1 (
    echo [ERROR] jpackage no pudo crear el .exe
    pause
    exit /b 1
)

rem ---------------------------------------------------------------
rem 4) Copias listas para entregar
rem ---------------------------------------------------------------
echo [4/4] Preparando archivos para entregar...
copy /y "target\SysVentaRopa.jar" "%DEST%\SysVentaRopa.jar" >nul
if exist "%DEST%\SysVentaRopa-portable.zip" del /q "%DEST%\SysVentaRopa-portable.zip"
powershell -NoProfile -Command "Compress-Archive -Path '%DEST%\SysVentaRopa' -DestinationPath '%DEST%\SysVentaRopa-portable.zip'" >nul 2>&1
rem El zip se arma SIN la base de datos (asi quien lo reciba empieza con los datos de ejemplo);
rem luego se devuelve la base de datos al ejecutable de esta PC
if exist "%RESPALDO%" xcopy /e /i /q /y "%RESPALDO%" "%DEST%\SysVentaRopa\data" >nul

echo.
echo ============================================================
echo   LISTO
echo   .EXE : %DEST%\SysVentaRopa\SysVentaRopa.exe   (no necesita Java)
echo   .JAR : %DEST%\SysVentaRopa.jar                (necesita Java 21: java -jar SysVentaRopa.jar)
echo   ZIP  : %DEST%\SysVentaRopa-portable.zip       (para copiar a otra PC)
echo ============================================================
explorer "%DEST%"
pause
exit /b 0

rem ---------------------------------------------------------------
rem :probar <carpeta>  -> si es un JDK 21+ con jpackage, lo elige
rem ---------------------------------------------------------------
:probar
if defined JDK goto :eof
if "%~1"=="" goto :eof
if not exist "%~1\bin\jpackage.exe" goto :eof
if not exist "%~1\release" goto :eof
findstr /r /c:"JAVA_VERSION=.2[1-9]" "%~1\release" >nul 2>&1
if errorlevel 1 goto :eof
set "JDK=%~1"
goto :eof
