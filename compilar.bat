@echo off
setlocal EnableExtensions EnableDelayedExpansion

title PSDK - Compilador
color 0A

rem Sempre usa a pasta onde este .bat esta
set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"

echo ============================================
echo           PSDK - Compilando...
echo ============================================
echo.
echo Projeto: %CD%
echo.

call :FindJava
if errorlevel 1 goto :fail

call :RunBuild
set "BUILD_CODE=!ERRORLEVEL!"
if not "!BUILD_CODE!"=="0" goto :fail

set "JAR="
if exist "build\libs\psdk-1.0.0.jar" set "JAR=build\libs\psdk-1.0.0.jar"
if exist "target\psdk-1.0.0.jar" set "JAR=target\psdk-1.0.0.jar"

if not defined JAR (
    echo.
    echo [AVISO] Build terminou, mas nenhum JAR foi encontrado.
    echo         Procurando em build\libs ...
    if exist "build\libs\" dir /b "build\libs\"
    goto :fail
)

if not exist "dist" mkdir "dist"
copy /y "!JAR!" "dist\psdk.jar" >nul
for %%F in ("!JAR!") do set "JAR_FULL=%%~fF"
for %%F in ("dist\psdk.jar") do set "DIST_FULL=%%~fF"

echo.
echo ============================================
echo        BUILD SUCESSO!
echo.
echo  Gradle:  !JAR_FULL!
echo  Copia:   !DIST_FULL!
echo ============================================
echo.
echo Abrindo pasta dist...
start "" explorer /select,"!DIST_FULL!"
echo.
pause
exit /b 0

:fail
color 0C
echo.
echo ============================================
echo        ERRO NA COMPILACAO!
echo        Verifique Java 21 e os erros acima.
echo ============================================
echo.
pause
exit /b 1

:FindJava
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    echo Java: %JAVA_HOME%
    exit /b 0
)

for /f "delims=" %%J in ('where java 2^>nul') do (
    for %%I in ("%%~dpJ..") do set "JAVA_HOME=%%~fI"
    if exist "!JAVA_HOME!\bin\java.exe" (
        echo Java: !JAVA_HOME! ^(PATH^)
        exit /b 0
    )
)

if exist "D:\tools\jdk-21.0.11+10\bin\java.exe" (
    set "JAVA_HOME=D:\tools\jdk-21.0.11+10"
    echo Java: %JAVA_HOME%
    exit /b 0
)

if exist "D:\tools\jdk-21\bin\java.exe" (
    set "JAVA_HOME=D:\tools\jdk-21"
    echo Java: %JAVA_HOME%
    exit /b 0
)

for /d %%D in ("D:\tools\jdk*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        echo Java: !JAVA_HOME!
        exit /b 0
    )
)

for /d %%D in ("C:\Program Files\Java\jdk*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        echo Java: !JAVA_HOME!
        exit /b 0
    )
)

for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk*") do (
    if exist "%%D\bin\java.exe" (
        set "JAVA_HOME=%%D"
        echo Java: !JAVA_HOME!
        exit /b 0
    )
)

echo [ERRO] Java 21 nao encontrado. Instale JDK 21 ou defina JAVA_HOME.
exit /b 1

:RunBuild
if exist "gradlew.bat" if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Build: Gradle Wrapper ^(clean shadowJar^)
    call "gradlew.bat" clean shadowJar --no-daemon
    exit /b %ERRORLEVEL%
)

where mvn >nul 2>&1
if not errorlevel 1 (
    echo Build: Maven ^(PATH^)
    call mvn clean package -DskipTests
    exit /b %ERRORLEVEL%
)

for /d %%M in ("D:\tools\apache-maven*") do (
    if exist "%%M\bin\mvn.cmd" (
        echo Build: Maven ^(%%M^)
        call "%%M\bin\mvn.cmd" clean package -DskipTests
        exit /b %ERRORLEVEL%
    )
)

echo [ERRO] Gradle Wrapper ou Maven nao encontrado.
exit /b 1
