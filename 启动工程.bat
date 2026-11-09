@echo off
title KaiEdu Launcher
echo ==========================================
echo   KaiEdu startup: backend(8080) + frontend(5173)
echo ==========================================
echo.
echo [1/2] starting backend edu-server ...
start "KaiEdu-backend" /d "F:\98_eyasclaw\07_edu\edu-server" cmd /k C:\Users\kaifu\.jdks\corretto-1.8.0_432\bin\java.exe -jar target\edu-server-1.0.0.jar
echo [2/2] starting frontend edu-web ...
start "KaiEdu-frontend" /d "F:\98_eyasclaw\07_edu\edu-web" cmd /k npm run dev
echo.
echo Two windows opened. DO NOT close them.
echo  backend OK when:   Started EduServerApplication
echo  frontend OK when:  Local: http://localhost:5173/
echo  (if it shows 5174, open http://localhost:5174 instead)
echo.
echo  Then open: http://localhost:5173   (admin / 123456)
echo  If backend prints "Communications link failure",
echo  start MySQL first (Admin PowerShell): net start MySQL80
echo.
pause
