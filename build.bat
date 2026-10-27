@echo off
rem Builds the Baritone fork and the UntilDawn mod, numbers them, files them in .\Versions and installs them.
rem
rem   Versions\UntilDawn-X.X.jar   the newest build (X.X counts up 1.0, 1.1 ... 1.9, 2.0 ...)
rem   Versions\Baritone-X.X.jar    the newest Baritone build, numbered the same way on its own
rem   Versions\Old\                every earlier build is moved here
rem
rem Each new jar is then copied to every folder in DEST1 / DEST2 below, after the old UntilDawn and Baritone jars
rem in that folder are deleted.
rem
rem Extra arguments go to UntilDawn's build, e.g.  build.bat -x runGameTest  to skip the game tests.
setlocal EnableDelayedExpansion

rem ---- Settings: where the finished mods are installed ----
set "DEST1=C:\Users\tavin\Desktop\UntilDawn Server\mods"
set "DEST2=C:\Users\tavin\AppData\Roaming\PrismLauncher\instances\UntilDawn\minecraft\mods"
rem ---------------------------------------------------------

set "ROOT=%~dp0"
set "VERSIONS=%ROOT%Versions"
set "OLD=%VERSIONS%\Old"
if not exist "%OLD%" mkdir "%OLD%"

call :nextversion UntilDawn MOD_VER
call :nextversion Baritone BARITONE_VER
echo UntilDawn %MOD_VER%, Baritone %BARITONE_VER%

rem The mod's own version is read from gradle.properties, so the jar and its fabric.mod.json both carry it.
powershell -NoProfile -Command "$p='%ROOT%UntilDawn\gradle.properties'; (Get-Content $p) -replace '^mod_version=.*','mod_version=%MOD_VER%' | Set-Content $p"
if errorlevel 1 (
    echo Could not set mod_version in UntilDawn\gradle.properties.
    exit /b 1
)

echo === Building Baritone ===
pushd "%ROOT%Baritone"
call .\gradlew.bat :fabric:build
if errorlevel 1 (
    popd
    echo Baritone build failed.
    exit /b 1
)
popd

echo === Building UntilDawn ===
pushd "%ROOT%UntilDawn"
call .\gradlew.bat build %*
if errorlevel 1 (
    popd
    echo UntilDawn build failed.
    exit /b 1
)

rem The Baritone jar UntilDawn compiles against (baritone_jar in gradle.properties), not the other variants beside it.
set "BARITONE_JAR="
for /f "tokens=1,* delims==" %%a in ('findstr /b "baritone_jar=" gradle.properties') do set "BARITONE_JAR=%%b"
if not defined BARITONE_JAR (
    popd
    echo baritone_jar is not set in UntilDawn\gradle.properties.
    exit /b 1
)
set "BARITONE_JAR=!BARITONE_JAR:/=\!"
for %%i in ("!BARITONE_JAR!") do set "BARITONE_JAR=%%~fi"
popd

set "MOD_JAR=%ROOT%UntilDawn\build\libs\UntilDawn-%MOD_VER%.jar"
if not exist "%MOD_JAR%" (
    echo Expected %MOD_JAR% but it was not built.
    exit /b 1
)
if not exist "%BARITONE_JAR%" (
    echo Expected %BARITONE_JAR% but it was not built.
    exit /b 1
)

echo === Publishing ===
call :publish UntilDawn %MOD_VER% "%MOD_JAR%" Monster
if errorlevel 1 exit /b 1
call :publish Baritone %BARITONE_VER% "%BARITONE_JAR%" baritone-fabric
if errorlevel 1 exit /b 1

echo === Done: UntilDawn-%MOD_VER%.jar and Baritone-%BARITONE_VER%.jar are in %VERSIONS% ===
endlocal
exit /b 0

rem ---------------------------------------------------------------------------------------------------------------
rem :nextversion NAME VARIABLE - puts the number after the newest NAME-X.X.jar (in Versions or Old) into VARIABLE.
rem Minor numbers run 0-9, so 1.9 is followed by 2.0. With no jars yet, the result is 1.0.
:nextversion
set "best=-1"
for %%f in ("%VERSIONS%\%~1-*.jar" "%OLD%\%~1-*.jar") do (
    set "v=%%~nf"
    set "v=!v:%~1-=!"
    for /f "tokens=1,2 delims=." %%a in ("!v!") do (
        set /a n=%%a*10+%%b
        if !n! gtr !best! set "best=!n!"
    )
)
set /a next=best+1
if !best! lss 0 set "next=10"
set /a major=next/10, minor=next%%10
set "%~2=!major!.!minor!"
exit /b 0

rem :publish NAME VERSION JAR LEGACY_PREFIX - files the jar as NAME-VERSION.jar in Versions (older ones go to Old),
rem then replaces the old NAME jars (and any LEGACY_PREFIX-*.jar from before the rename) in DEST1 and DEST2.
:publish
for %%f in ("%VERSIONS%\%~1-*.jar") do move /y "%%f" "%OLD%\" >nul
copy /y "%~3" "%VERSIONS%\%~1-%~2.jar" >nul
if errorlevel 1 (
    echo Could not copy %~3 to %VERSIONS%.
    exit /b 1
)
for %%d in ("%DEST1%" "%DEST2%") do (
    if exist "%%~d\" (
        del /q "%%~d\%~1-*.jar" 2>nul
        del /q "%%~d\%~4-*.jar" 2>nul
        copy /y "%VERSIONS%\%~1-%~2.jar" "%%~d\" >nul
        if errorlevel 1 (
            echo Could not copy %~1-%~2.jar to %%~d.
            exit /b 1
        )
        echo   %~1-%~2.jar -^> %%~d
    ) else (
        echo   Skipped %%~d: the folder does not exist.
    )
)
exit /b 0
