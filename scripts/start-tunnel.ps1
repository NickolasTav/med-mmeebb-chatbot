<#
.SYNOPSIS
    Inicia o túnel Ngrok para desenvolvimento local do Chatbot MMEEBB.
.DESCRIPTION
    Verifica se o Ngrok está instalado e inicia o encaminhamento de portas
    para a aplicação Spring Boot (localhost:8080).
#>

[CmdletBinding()]
param (
    [int]$Port = 8080,
    [string]$Domain = ""
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   🩺 Chatbot MMEEBB - Inicializador de Túnel Ngrok" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Verifica se o ngrok está instalado no sistema
$ngrokPath = Get-Command ngrok -ErrorAction SilentlyContinue

if (-not $ngrokPath) {
    Write-Host "`n[!] O executável do Ngrok não foi encontrado no PATH do sistema." -ForegroundColor Yellow
    Write-Host "Para instalar rapidamente no Windows via Winget, execute:" -ForegroundColor White
    Write-Host "   winget install ngrok.ngrok`n" -ForegroundColor Cyan
    Write-Host "Ou baixe diretamente em: https://ngrok.com/download" -ForegroundColor White
    Write-Host "Consulte o guia completo em: docs/GUIA_NGROK_WEBHOOK.md`n" -ForegroundColor Gray
    Read-Host "Pressione Enter para sair..."
    exit 1
}

Write-Host "`n[✓] Ngrok detectado: $($ngrokPath.Source)" -ForegroundColor Green
Write-Host "[i] Apontando túnel para: http://localhost:$Port" -ForegroundColor Cyan
Write-Host "[i] Inspecione requisições em tempo real em: http://localhost:4040`n" -ForegroundColor Yellow

# 2. Executa o túnel com ou sem domínio estático
if ($Domain -ne "") {
    Write-Host "[i] Utilizando domínio estático customizado: $Domain`n" -ForegroundColor Magenta
    ngrok http $Port --domain=$Domain
} else {
    ngrok http $Port
}
