@echo off

::指定程序工作路径
set SERVICE_DIR="%~dp0"
::指定jar包
set JARNAME="%~dp0springboot-mqtt-merge-1.0.jar"
set "SHORTJAR=springboot-mqtt-merge-1.0.jar"

 
::流程控制
if "%1"=="start" (
  call:START
) else (
  if "%1"=="stop" ( 
    call:STOP 
  ) else ( 
    if "%1"=="restart" (
	  call:RESTART 
	) else ( 
	  call:DEFAULT 
	)
  )
)
goto:eof
 
 
::启动jar包
:START
echo function "start" starting...
cd /d %SERVICE_DIR%

echo %JARNAME%
cmd /c start javaw.exe -Dfile.encoding=UTF-8 -Xms1024m -Xmx2048m -XX:+UseConcMarkSweepGC -XX:OnOutOfMemoryError="start.bat" -jar %JARNAME%
echo == service start success
goto:eof
 
 
::停止java程序运行
:STOP
echo function "stop" starting...
call:shutdown
echo == service stop success
goto:eof
 
 
::重启jar包
:RESTART
echo function "restart" starting...
call:STOP
call:sleep2
call:START
echo == service restart success
exit
 
 
::执行默认方法--重启jar包
:DEFAULT
echo Now choose default item : restart
call:RESTART
exit
 
 
::关掉对应的程序
:shutdown
curl -X POST http://127.0.0.1:9633/actuator/shutdown
call:sleep30
WMIC PROCESS WHERE "name like 'java%%' and CommandLine like '%%%SHORTJAR%%%'" CALL Terminate
goto:eof


::延时30秒
:sleep30
TIMEOUT /t 30 /nobreak
goto:eof


::延时2秒
:sleep2
TIMEOUT /t 2 /nobreak
goto:eof

