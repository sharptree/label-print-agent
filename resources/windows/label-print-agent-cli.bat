@echo off

set EXECJAVA=

if not "%JAVA_HOME%" == "" goto useJavaHome

set EXECJAVA=java
where %EXECJAVA% >nul 2>nul
@rem error code 9009 is file not found, indicating that the java.exe was not found.
if %errorlevel%==9009 (
    @echo The JAVA_HOME environment variable has not been set and java.exe is not on the system PATH.
    goto quit
)
goto run

:useJavaHome
set EXECJAVA="%JAVA_HOME%\bin\java.exe"

if not exist %EXECJAVA% (
    @echo The java executable does not exist within the path specified by the JAVA_HOME
    goto quit
)

goto run

:run
%EXECJAVA% -jar label-print-agent.jar  %1 %2 %3

:quit