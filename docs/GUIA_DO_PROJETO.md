# Guia do Projeto — ProjetoSD2025 (Chat TUI + RabbitMQ)

Este documento descreve **como o projeto funciona**, como o código está **organizado**, e **onde** é mais seguro/adequado colocar novas funcionalidades.

## 1) O que este projeto faz

- É um chat em **interface de terminal** (TUI) usando **Lanterna**.
- As mensagens são trocadas via **RabbitMQ (AMQP)**.
- Cada usuário possui uma **fila com o seu próprio nome**. Para enviar uma mensagem para `bob`, o app publica na fila `bob`.

## 2) Como o app executa (fluxo de inicialização)

Fluxo simplificado:

1. `br.com.tocka.App` inicializa o terminal e o `Screen` (Lanterna).
2. Abre um modal (`UsernameModal`) para coletar o nome de usuário.
3. Conecta no RabbitMQ via `ConnectionManager` (host/porta/credenciais).
4. Abre a janela principal de chat via `ChatWindow.showChatWindow(...)`.
5. Na janela principal:
   - Cria `Sender` para publicar mensagens.
   - Cria `Receiver` para consumir mensagens da fila do usuário.
   - Cria `ChatController` para a lógica (destinatário atual, histórico, validações).

Ponto importante: o `Receiver` faz callback em thread do RabbitMQ; a UI é atualizada usando `gui.getGUIThread().invokeLater(...)`.

## 3) Como as mensagens funcionam (RabbitMQ)

### 3.1 Fila por usuário

- Nome da fila: **igual ao username** (ex.: `alice`, `bob`).
- Criação: tanto `Sender` quanto `Receiver` chamam `queueDeclare(queueName, true, false, false, null)`.
  - `durable=true` (fila durável)
  - `exclusive=false`
  - `autoDelete=false`

### 3.2 Publicação

- `Sender.sendMessage(recipientQueue, message)` publica com:
  - exchange: `""` (default exchange)
  - routing key: `recipientQueue`

### 3.3 Consumo

- `Receiver` consome a fila do usuário com `basicConsume(queueName, true, ...)`.
  - `autoAck=true` (não há ack manual hoje)

### 3.4 Formato de mensagem

Atualmente o formato é **texto**:

- Ao enviar: `ChatController` monta `formattedMessage = "@" + username + " diz: " + content`.
- Ao receber: `Receiver.extractSender(...)` tenta extrair o remetente buscando o padrão `@<nome> diz:`.

Isso funciona, mas é um ponto clássico para evoluir para **JSON** (ex.: `{type, from, to, body, ts}`) se você quiser adicionar features como: reações, anexos, comandos, etc.

## 4) Estrutura do código (onde fica cada responsabilidade)

### 4.1 Entrada da aplicação

- `src/main/java/br/com/tocka/App.java`
  - Inicializa Lanterna
  - Pergunta username
  - Conecta no RabbitMQ
  - Abre `ChatWindow`

### 4.2 UI (Lanterna)

- `src/main/java/br/com/tocka/gui/UsernameModal.java`
  - Modal de login (coleta username)

- `src/main/java/br/com/tocka/gui/ChatWindow.java`
  - Monta layout da UI (painéis de mensagens / notificações / input)
  - Liga UI com o `ChatController`
  - Inicializa `Receiver` e integra callback com a thread da UI

### 4.3 Controller (lógica)

- `src/main/java/br/com/tocka/controller/ChatController.java`
  - Regras de input
    - `@usuario` define destinatário atual
    - Mensagem sem destinatário gera notificação
  - Envio e recebimento
  - Histórico por destinatário via `Map<String, List<ChatMessage>> conversations`

### 4.4 Model

- `src/main/java/br/com/tocka/model/ChatMessage.java`
  - Estrutura de mensagem (producer/consumer/content/timestamp)

- `src/main/java/br/com/tocka/model/HistoryEvent.java`
- `src/main/java/br/com/tocka/model/User.java`
  - Estruturas prontas para evoluções (ex.: lista de eventos, usuário como entidade)
  - Observação: hoje elas ainda não estão integradas ao fluxo principal.

### 4.5 Infra RabbitMQ

- `src/main/java/br/com/tocka/rabbitmq/ConnectionManager.java`
  - Configura e abre conexão AMQP

- `src/main/java/br/com/tocka/rabbitmq/Sender.java`
  - Publica mensagens em filas

- `src/main/java/br/com/tocka/rabbitmq/Receiver.java`
  - Consome a fila do usuário e entrega mensagens via callback

## 5) Onde colocar novas funcionalidades (pontos de extensão)

Abaixo estão os “lugares naturais” para evoluir sem bagunçar responsabilidades.

### 5.1 Novos comandos de chat (ex.: `/help`, `/whoami`, `/quit`)

- Melhor lugar: `ChatController.processInput(String input)`.
  - Hoje ele trata `@destinatario` e texto puro.
  - Você pode adicionar comandos do tipo `input.startsWith("/")`.

Sugestão: manter uma enum/comando (ex.: `ChatCommand`) ou um pequeno roteador para não crescer demais o método.

### 5.2 Validação/normalização do username

- Melhor lugar: `UsernameModal`.
  - Ex.: impedir espaços, limitar tamanho, impedir vazio, etc.

### 5.3 Melhorias na UI

- Melhor lugar: `ChatWindow`.
  - Ex.: atalhos de teclado, painel de contatos, trocar tema/bordas.

Se criar novas telas/janelas, mantenha em `br.com.tocka.gui`.

### 5.4 Persistência de histórico

Hoje o histórico fica em memória (`conversations`). Se quiser persistir:

- Crie um novo pacote: `br.com.tocka.storage` (ou `repository`).
- Introduza uma interface (ex.: `ChatHistoryStore`) e implemente em arquivo/SQLite.
- Integre no `ChatController` (carregar ao abrir conversa; salvar ao enviar/receber).

`HistoryEvent` pode virar base para um “log” de ações.

### 5.5 Evoluir protocolo de mensagem (texto → JSON)

- Melhor lugar para mudar: `ChatController.sendMessage(...)` (serialização) e `Receiver` (desserialização).

Passos típicos:
- Criar um modelo (ex.: `WireMessage`) em `model`.
- Codificar/decodificar como JSON.
- Manter retrocompatibilidade opcional (detectar texto antigo).

### 5.6 Grupos / salas

Hoje o destinatário é uma fila por usuário. Para grupos, opções:

- Criar filas nomeadas por sala (ex.: `room.dev`) e todos publicam/consomem dessa fila.
- Ou usar **exchange** (fanout/topic) e bindings por usuário.

Isso mexe mais no pacote `rabbitmq`.

### 5.7 Configuração por variáveis de ambiente / args

Hoje host/porta/credenciais estão hardcoded em `App`.

- Melhor lugar: `App` + um pequeno helper `Config`.
- Exemplos de env vars: `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASS`.

## 6) Como rodar (checklist rápido)

1. Suba RabbitMQ:
   - `docker compose up -d`
2. Gere o JAR com dependências:
   - `mvn -DskipTests compile assembly:single`
3. Rode:
   - `java -jar target/ProjetoSD2025-1.0-SNAPSHOT-jar-with-dependencies.jar`
4. Abra dois terminais para testar conversa entre dois usuários.

## 7) Ideias rápidas de melhorias (sugestões)

- Comandos (`/help`, `/clear`, `/quit`, `/history`)
- Melhorar exibição: mostrar remetente/destinatário de forma consistente
- Evitar “imprimir tudo” no painel de mensagens quando chega msg de outro contato (mostrar por conversa)
- Persistir histórico
- Trocar formato de mensagem para JSON
- Ajustar `autoAck` (ack manual) se quiser garantias maiores
