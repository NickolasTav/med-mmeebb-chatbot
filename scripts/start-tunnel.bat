@echo off
title Chatbot MMEEBB - Ngrok Tunnel
color 0A

echo ============================================================
echo    Chatbot MMEEBB - Inicializador de Tunel Ngrok (Porta 8080)
echo ============================================================
echo.

where ngrok >nul 2>nul
if %errorlevel% neq 0 (
    color 0E
    echo [!] O executavel do Ngrok nao foi encontrado no PATH.
    echo.
    echo Para instalar no Windows, abra o PowerShell e execute:
    echo    winget install ngrok.ngrok
    echo.
    echo Ou consulte o guia completo em: docs\GUIA_NGROK_WEBHOOK.md
    echo.
    pause
    exit /b 1
)

echo [OK] Ngrok detectado no sistema!
echo [i] Iniciando tunel HTTP para a porta 8080...
echo [i] Painel de inspecao de webhooks em tempo real: http://localhost:4040
echo.
ngrok http 8080
pause
