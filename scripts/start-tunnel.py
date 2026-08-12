#!/usr/bin/env python3
"""
=============================================================================
  🩺 Chatbot MMEEBB - Inicializador de Túnel Ngrok (Python / Pyngrok)
=============================================================================
Este script inicializa o túnel Ngrok para desenvolvimento local, expondo a
aplicação Spring Boot (porta 8080) e exibindo a URL exata do Webhook do UaiZap.
"""

import os
import sys
import time
from pathlib import Path

# Carrega variáveis de ambiente do .env se existir
def load_env():
    env_path = Path(__file__).resolve().parent.parent / ".env"
    env_vars = {}
    if env_path.exists():
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    env_vars[k.strip()] = v.strip()
    return env_vars

def main():
    print("\n" + "=" * 65)
    print("   🩺 Chatbot MMEEBB - Inicializador de Túnel Ngrok (Python)")
    print("=" * 65 + "\n")

    # 1. Verifica se pyngrok está instalado
    try:
        from pyngrok import ngrok, conf
    except ImportError:
        print("[!] A biblioteca 'pyngrok' não foi encontrada.")
        print("    Instalando automaticamente via pip...\n")
        import subprocess
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pyngrok"])
        from pyngrok import ngrok, conf

    env_vars = load_env()
    port = int(env_vars.get("SERVER_PORT", 8080))
    domain = env_vars.get("NGROK_DOMAIN", "")
    token = env_vars.get("NGROK_AUTHTOKEN", "")

    # 2. Configura o Authtoken se fornecido no .env
    if token and token != "seu_authtoken_ngrok_aqui":
        ngrok.set_auth_token(token)

    # 3. Conecta o túnel
    try:
        print(f"[*] Iniciando túnel para http://localhost:{port}...")
        
        connect_kwargs = {"addr": port}
        if domain:
            print(f"[*] Utilizando domínio estático customizado: {domain}")
            connect_kwargs["domain"] = domain

        tunnel = ngrok.connect(**connect_kwargs)
        public_url = tunnel.public_url
        webhook_url = f"{public_url}/api/v1/webhooks/uaizap"

        print("\n" + "╔" + "═" * 63 + "╗")
        print("║" + " 🚀 TÚNEL NGROK ATIVO COM SUCESSO! ".center(63) + "║")
        print("╠" + "═" * 63 + "╣")
        print(f"║ 🌐 URL Pública : {public_url:<44} ║")
        print(f"║ 🩺 Backend Local: http://localhost:{port:<34} ║")
        print(f"║ 🔍 Painel Ngrok: http://localhost:4040{'':<28} ║")
        print("╠" + "═" * 63 + "╣")
        print("║ 📲 COPIE E COLE NO PAINEL DO UAIZAP:                          ║")
        print(f"║ 👉 URL Webhook : {webhook_url:<44} ║")
        print("║ 👉 Evento      : messages.upsert                              ║")
        print("╚" + "═" * 63 + "╝\n")
        print("[i] Pressione Ctrl+C para encerrar o túnel.\n")

        # Mantém processo rodando
        ngrok_process = ngrok.get_ngrok_process()
        ngrok_process.proc.wait()

    except KeyboardInterrupt:
        print("\n[!] Encerrando túnel Ngrok...")
        ngrok.kill()
        print("[✓] Túnel finalizado com sucesso.\n")
    except Exception as e:
        error_msg = str(e)
        print(f"\n[❌] Erro ao iniciar o Ngrok: {error_msg}\n")
        if "authentication failed" in error_msg.lower() or "authtoken" in error_msg.lower():
            print("💡 DICA: Configure seu authtoken com o comando:")
            print("   pyngrok config add-authtoken SEU_TOKEN_AQUI")
            print("   Ou adicione 'NGROK_AUTHTOKEN=seu_token' no arquivo .env do projeto.\n")
        sys.exit(1)

if __name__ == "__main__":
    main()
