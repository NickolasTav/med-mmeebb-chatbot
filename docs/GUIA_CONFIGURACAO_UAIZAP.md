# 📲 Guia Prático: Como Configurar o UaiZap (WhatsApp Gateway)

O **UaiZap** é a plataforma de gateway que conecta o número de WhatsApp do seu chatbot ao backend do Spring Boot, permitindo:
1. **Receber mensagens** dos estudantes via **Webhooks HTTP**.
2. **Enviar questões e feedbacks** via **API REST** (`/message/sendText/{instance}`).

---

## 📋 Passo a Passo de Configuração

---

### 1️⃣ Passo 1: Acessar o Painel do UaiZap
1. Acesse o painel web do seu provedor UaiZap (ou do servidor contratado da sua instituição).
2. Faça login com seu e-mail e senha administrativos.

---

### 2️⃣ Passo 2: Criar uma Nova Instância
1. No menu lateral, vá em **Instâncias** (ou *Instances*) -> **Criar Nova Instância**.
2. Preencha os dados:
   - **Nome da Instância**: `unipam_med_bot` *(ou o nome de sua preferência)*.
   - **Descrição**: *Chatbot de Repetição Espaçada MMEEBB - Medicina UNIPAM*.
3. Clique em **Salvar / Criar**.
4. Copie a **Chave de API / Token da Instância** (`apikey` ou `instanceToken`) gerada pelo painel.

---

### 3️⃣ Passo 3: Conectar o Número do WhatsApp (QR Code)
1. Na lista de instâncias, clique sobre a instância criada (`unipam_med_bot`).
2. Clique no botão **Conectar / Gerar QR Code**.
3. No seu celular (com o chip que será o número oficial do bot):
   - Abra o **WhatsApp**.
   - Vá em **Configurações / Opções** (três pontinhos) -> **Aparelhos Conectados**.
   - Toque em **Conectar um aparelho**.
   - Aponte a câmera para o QR Code na tela do UaiZap.
4. Aguarde a confirmação. O status mudará para **Conectado (CONNECTED)**.

---

### 4️⃣ Passo 4: Configurar o Webhook no Painel do UaiZap
O webhook é o endereço para onde o UaiZap enviará as mensagens que os alunos digitarem:

1. Dentro da instância no painel, acesse a aba **Webhooks**.
2. Marque a opção: **Habilitar Webhook (Ativo)**.
3. No campo **URL do Webhook**, informe o endereço da sua aplicação:
   - **Em Desenvolvimento Local (usando Ngrok)**:
     ```http
     https://SUA_URL_NGROK.ngrok-free.app/api/v1/webhooks/uaizap
     ```
   - **Em Produção (Servidor/Cloud)**:
     ```http
     https://seu-dominio.com.br/api/v1/webhooks/uaizap
     ```
4. **Eventos a Escutar**:
   - Selecione: `messages.upsert` (ou `MESSAGES_UPSERT` / Mensagens Recebidas).
5. **Configuração de Segurança (Headers)**:
   - Adicione o header: `X-Webhook-Secret`
   - Valor: `med_secret_key_123` *(deve coincidir com a variável `UAIZAP_WEBHOOK_SECRET` do seu `.env`)*.
6. Clique em **Salvar Configurações**.

---

## ⚙️ 5️⃣ Passo 5: Configurar o Arquivo `.env` do Backend

Abra o arquivo `.env` na raiz do projeto `projeto-tcc` e preencha as variáveis:

```properties
# ==========================================
# Configuração da Integração com UaiZap
# ==========================================
UAIZAP_BASE_URL=https://api.uaizap.com.br
UAIZAP_INSTANCE=unipam_med_bot
UAIZAP_TOKEN=cole_aqui_a_apikey_da_sua_instancia
UAIZAP_WEBHOOK_SECRET=med_secret_key_123
```

---

## 🧪 6️⃣ Passo 6: Testar o Funcionamento de Ponta a Ponta

1. Suba a infraestrutura do banco e filas:
   ```bash
   docker compose up -d
   ```
2. Inicie o túnel do Ngrok:
   ```powershell
   .\scripts\start-tunnel.ps1
   ```
3. Inicie o backend Spring Boot:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
4. Pegue outro celular e envie qualquer mensagem para o número do bot no WhatsApp:
   > *"Olá"* ou *"revisar"*
5. **Resultado**:
   - O UaiZap enviará a requisição para o Ngrok.
   - O Ngrok repassará para o Spring Boot (`localhost:8080`).
   - A mensagem entrará na fila do RabbitMQ, será processada pelo motor MMEEBB / Gemini AI e a resposta será devolvida no WhatsApp em menos de 1 segundo!

---

## 💡 Dúvidas Frequentes:

- **Posso usar outros gateways como Z-API ou Evolution API?**
  Sim! O payload padrão de webhook (`messages.upsert`) e a rota de envio (`/message/sendText/{instance}`) são compatíveis.
- **O que acontece se a internet cair temporariamente?**
  As mensagens ficam seguras na fila do RabbitMQ e são processadas automaticamente assim que a conexão restabelece.
