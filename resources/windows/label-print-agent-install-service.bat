rem see https://commons.apache.org/proper/commons-daemon/procrun.html

set SERVICE_HOME=%~dp0%

IF %SERVICE_HOME:~-1%==\ SET SERVICE_HOME=%SERVICE_HOME:~0,-1%

"%SERVICE_HOME%\label-print-agent.exe" install label-print-agent ^
--DisplayName="Label Print Agent" ^
--Description="Label print agent for Maximo label printing" ^
--Startup=auto ^
--Jvm=auto ^
--JvmMs=512 ^
--JvmMx=1024 ^
--Classpath "label-print-agent.jar" ^
--LogPath="logs" ^
--LogLevel=Info ^
--StdOutput=auto ^
--StdError=auto ^
--StartMode="jvm" ^
--StartClass="io.sharptree.maximo.app.label.ApplicationKt" ^
--StartParams="monitor" ^
--StopMode="jvm" ^
--StopClass="io.sharptree.maximo.app.label.ApplicationKt" ^
--StopParams="stop" ^
--StopTimeout=5 ^
--LogPrefix="label-print-agent" ^
--PidFile="label-print-agent.pid" ^
--StopPath="%SERVICE_HOME%" ^
--StartPath="%SERVICE_HOME%"