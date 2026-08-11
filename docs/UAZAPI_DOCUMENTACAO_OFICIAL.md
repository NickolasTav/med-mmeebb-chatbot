# 📚 Guia e Documentação da API: Uazapi (uazapi.dev / uazapiGO)

Esta documentação consolida as especificações oficiais da **Uazapi (uazapi.dev / uazapiGO)** para integração com o chatbot de repetição espaçada do TCC (**MMEEBB**).

---

## 🌐 1. Visão Geral e Endpoints Base

- **Portal Oficial**: [https://uazapi.dev](https://uazapi.dev)
- **Base URL Padrão**: `https://{subdominio}.uazapi.com` (ou servidor *self-hosted*)
- **Formato dos Dados**: `application/json` (UTF-8)
- **Formato de Telefones**: Internacional com DDI + DDD + Número (ex: `5534999999999`)

---

## 🔐 2. Autenticação (Headers HTTP)

A autenticação é feita via headers HTTP personalizados:

| Header | Escopo de Uso | Descrição |
| :--- | :--- | :--- |
| **`token`** | Instância & Envio | Token único de autenticação gerado para a instância conectada ao WhatsApp |
| **`admintoken`** | Administrativo | Token mestre para criar instâncias, deletar ou gerenciar a infraestrutura |

```http
Content-Type: application/json
token: seu_token_de_instancia_aqui
```

---

## 🚀 3. Endpoints de Envio de Mensagens

### 3.1. Enviar Mensagem de Texto Simples
* **Rota**: `POST /send/text`
* **Descrição**: Envia mensagem formatada com emojis e Markdown do WhatsApp.

#### Request Payload:
```json
{
  "number": "5534999999999",
  "text": "📚 *MMEEBB - REVISÃO DIÁRIA*\n\nQual a conduta inicial recomendada para suspeita de SCA com supra de ST?",
  "linkPreview": false,
  "readchat": true,
  "delay": 1200
}
```

#### Parâmetros:
- `number` *(string, obrigatório)*: Telefone do destinatário (`55` + DDD + número).
- `text` *(string, obrigatório)*: Conteúdo da mensagem (suporta `\n` para quebras de linha).
- `linkPreview` *(boolean, opcional)*: Ativa/desativa prévia de links.
- `readchat` *(boolean, opcional)*: Marca a conversa como lida ao responder.
- `delay` *(integer, opcional)*: Delay em milissegundos antes do envio.

---

### 3.2. Enviar Menu Interativo / Botões / Enquetes
* **Rota**: `POST /send/menu`
* **Descrição**: Envia mensagens interativas com botões rápidos de múltipla escolha para o aluno responder com um toque.

#### Request Payload:
```json
{
  "number": "5534999999999",
  "type": "button",
  "text": "📋 *Caso Clínico 01 - Cardiologia*\n\nEscolha a conduta correta:",
  "footerText": "TCC Medicina UNIPAM • Algoritmo MMEEBB",
  "choices": [
    { "id": "opt_A", "text": "A) Realizar ECG < 10 min" },
    { "id": "opt_B", "text": "B) Aguardar dosagem de Troponina" },
    { "id": "opt_C", "text": "C) Prescrever analgésico e alta" }
  ]
}
```

*Tipos suportados em `type`*: `"button"`, `"list"`, `"poll"`, `"carousel"`.

---

### 3.3. Enviar Mídia (Imagens / ECGs / Raios-X / PDFs)
* **Rota**: `POST /send/media`
* **Descrição**: Envio de imagens clínicas ou materiais de apoio em PDF.

#### Request Payload:
```json
{
  "number": "5534999999999",
  "type": "image",
  "url": "https://servidor-unipam.edu.br/exames/ecg-01.jpg",
  "caption": "🔍 *Analise o ECG:* Observe o segmento ST nas derivações DII, DIII e aVF."
}
```

---

### 3.4. Simulação de Presença ("Digitando..." / "Gravando...")
* **Rota**: `POST /chat/presence`
* **Descrição**: Exibe o status de digitação no WhatsApp do aluno enquanto a I.A processa a resposta.

#### Request Payload:
```json
{
  "number": "5534999999999",
  "presence": "composing"
}
```
*Valores de `presence`*: `"composing"` (digitando), `"recording"` (gravando áudio), `"paused"` (parado).

---

## 📡 4. Gerenciamento da Instância e Conexão (QR Code)

### 4.1. Conectar e Gerar QR Code
* **Rota**: `POST /instance/connect`
* **Headers**: `token: {{token}}`
* **Descrição**: Retorna o QR Code em base64/ASCII para emparelhamento inicial. Se passado o `number` no corpo, pode gerar o código de pareamento numérico (*pair code*).

### 4.2. Consultar Status da Conexão
* **Rota**: `GET /instance/status`
* **Headers**: `token: {{token}}`

#### Response:
```json
{
  "status": "connected",
  "instance": "unipam_med_bot",
  "phone": "5534999999999"
}
```
*Estados possíveis*:
- `connected`: Conectado e operando normalmente.
- `connecting`: Tentando conectar/reconectar.
- `disconnected`: Desconectado (requer novo QR Code).
- `hibernated`: Sessão suspensa para economia de recursos.

---

## 📥 5. Webhook Inbound (Recebimento de Mensagens)

Quando o estudante responde no WhatsApp, a Uazapi faz uma chamada HTTP `POST` para a URL cadastrada no painel da instância:

### URL do Webhook no Backend:
`https://{sua-url}/api/v1/webhooks/uaizap`

### Exemplo de Payload Recebido (`messages.upsert`):
```json
{
  "event": "messages.upsert",
  "instance": "unipam_med_bot",
  "data": {
    "key": {
      "remoteJid": "5534999999999@s.whatsapp.net",
      "fromMe": false,
      "id": "3EB0C4A95B14"
    },
    "pushName": "Níckolas Tavares",
    "message": {
      "conversation": "A"
    },
    "messageType": "conversation",
    "messageTimestamp": 1723390000
  }
}
```

---

## ⚙️ 6. Variáveis de Configuração no `.env`

```properties
# ==========================================
# Uazapi Configuration (uazapi.dev)
# ==========================================
UAIZAP_BASE_URL=https://sua-instancia.uazapi.com
UAIZAP_TOKEN=seu_token_da_instancia_aqui
UAIZAP_WEBHOOK_SECRET=med_secret_key_123

# Anti-Ban e Delay Humanizado
UAIZAP_SEND_DELAY_SECONDS=6
UAIZAP_PRESENCE_ENABLED=true
```

---

## 🔗 Referências Oficiais
- **Site Oficial**: [uazapi.dev](https://uazapi.dev)
- **Postman Collection**: [Uazapi Public Workspace no Postman](https://postman.com)
