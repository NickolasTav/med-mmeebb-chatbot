# 📊 Guia de Observabilidade: Prometheus & Grafana

Este guia documenta a stack de observabilidade e monitoramento em tempo real do **Chatbot de Repetição Espaçada MMEEBB** para Medicina.

---

## 🏗️ Arquitetura da Stack

```mermaid
flowchart LR
    subgraph Backend["Spring Boot 3 (localhost:8080)"]
        Actuator["Spring Boot Actuator"]
        Micrometer["Micrometer Prometheus Registry"]
        CustomMetrics["Métricas MMEEBB / WhatsApp / Gemini"]
        Actuator --> Micrometer
        CustomMetrics --> Micrometer
        Micrometer --> Endpoint["/actuator/prometheus"]
    end

    subgraph Monitoring["Docker Compose"]
        Prometheus["Prometheus (:9090)\nScraping a cada 5s"] -->|Coleta Métricas| Endpoint
        Grafana["Grafana (:3000)\nDashboards Automáticos"] -->|Consulta PromQL| Prometheus
    end

    User["👨‍💻 Desenvolvedor / Banca"] -->|Visualiza| Grafana
```

---

## 🚀 Como Executar a Stack de Monitoramento

### 1. Subir os Containers Docker
Execute no terminal na raiz do projeto:
```bash
docker compose up -d
```

Containers ativos:
- **PostgreSQL + pgvector**: porta `5432`
- **RabbitMQ Management**: portas `5672` (AMQP) e `15672` (Web UI)
- **Redis**: porta `6379`
- **Prometheus**: porta `9090` (Scraper de métricas)
- **Grafana**: porta `3000` (Painéis e gráficos)

---

## 🌐 Links e Credenciais de Acesso

| Serviço | URL | Credenciais Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| **Grafana** | `http://localhost:3000` | `admin` / `admin` | Painel visual de métricas em tempo real com dashboard pré-carregado |
| **Prometheus** | `http://localhost:9090` | Sem autenticação | Console de consultas PromQL e status dos *targets* |
| **Actuator Endpoint** | `http://localhost:8080/actuator/prometheus` | Sem autenticação | Endpoint OpenMetrics exposto pelo Spring Boot |
| **RabbitMQ Management** | `http://localhost:15672` | `guest` / `guest` | Monitoramento das filas AMQP (`q.uaizap.*`, `q.med.*`) |

---

## 📈 Painéis do Dashboard Provisionado

O Grafana já inicia com o dashboard **"Med-MMEEBB Chatbot — Painel de Observabilidade"** automaticamente configurado:

### 1. 🧠 Visão Geral de Negócio (MMEEBB & WhatsApp)
- **Total de Revisões Processadas**: Quantidade acumulada de respostas de flashcards processadas pelo algoritmo $2^n$.
- **Taxa de Acerto Global (%)**: Gauge visual com faixas de retenção da memória ($>70\%$ verde, $50-70\%$ amarelo, $<50\%$ vermelho).
- **Taxa de Respostas (Acerto vs Erro)**: Gráfico de série temporal diferenciando acertos de erros ao longo do tempo.
- **Vazão de Mensagens WhatsApp**: Gráfico de vazão diferenciando mensagens recebidas (`INBOUND`) e despachadas com delay Anti-Ban (`OUTBOUND`).
- **Interações com IA (Google Gemini)**: Total de acionamentos do Tutor Clínico e NLU.

### 2. ⚙️ Performance da JVM & Sistema (Spring Boot 3)
- **Memória Heap da JVM**: Uso de memória Heap em tempo real vs limite máximo configurado.
- **Uso de CPU**: Consumo de processador da aplicação e do sistema operacional.

### 3. 🌐 Tráfego HTTP & Webhooks
- **Taxa de Requisições por Status**: Gráfico de RPS segmentado por código HTTP (200, 4xx, 5xx).
- **Latência de Resposta HTTP**: Percentis de latência **P95** e **P99** para controle de SLA.

---

## 🧪 Métricas Customizadas de Domínio (Micrometer)

| Nome da Métrica | Tipo | Tags | Descrição |
| :--- | :--- | :--- | :--- |
| `med_mmeebb_reviews_total` | `Counter` | `result` (`correct`, `incorrect`), `specialty` | Respostas registradas no algoritmo de repetição espaçada |
| `med_mmeebb_uaizap_messages_total` | `Counter` | `direction` (`INBOUND`, `OUTBOUND`), `type` (`TEXT`, `QUESTION`) | Volume de mensagens trafegadas no gateway WhatsApp |
| `med_mmeebb_ai_interactions_total` | `Counter` | `type` (`INTENT_CLASSIFIER`, `CLINICAL_TUTOR`), `fallback` (`true`, `false`) | Interações cognitivas com o Google Gemini |
