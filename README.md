# ProjetoSD2025 — Chat em Terminal com RabbitMQ

Aplicação Java (TUI) construída com Lanterna que permite enviar e receber mensagens entre usuários via RabbitMQ. O app roda em terminal, solicita um nome de usuário e abre uma janela de chat. As mensagens são trocadas por filas no RabbitMQ.

## Sumário
- [Arquitetura em alto nível](#arquitetura-em-alto-nível)
- [Pré‑requisitos](#pré-requisitos)
- [Como executar localmente](#como-executar-localmente)
  - [1) Subir o RabbitMQ com Docker](#1-subir-o-rabbitmq-com-docker)
  - [2) Construir o JAR com Maven](#2-construir-o-jar-com-maven)
  - [3) Executar a aplicação](#3-executar-a-aplicação)
  - [4) Testar conversa entre usuários](#4-testar-conversa-entre-usuários)
- [Como usar a interface de chat](#como-usar-a-interface-de-chat)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Configurações e variáveis](#configurações-e-variáveis)
- [Solução de problemas (FAQ)](#solução-de-problemas-faq)

## Arquitetura em alto nível
- Interface em terminal usando a biblioteca [Lanterna](https://github.com/mabe02/lanterna).
- Mensageria baseada em [RabbitMQ](https://www.rabbitmq.com/), via client AMQP.
- Cada usuário publica mensagens para o destinatário informado e ouve a própria fila para receber mensagens endereçadas a ele.
- Principais componentes:
  - `App`: inicializa o terminal, pergunta o nome do usuário, conecta ao RabbitMQ e abre a janela de chat.
  - `ChatWindow`/`ChatController`: UI e lógica de envio/recebimento, histórico e notificações.
  - `ConnectionManager`, `Sender`, `Receiver`: gerenciamento de conexão AMQP, publicação e consumo de mensagens.

## Pré‑requisitos
- Java JDK 11+ (recomendado 17+)
- Maven 3.8+
- Docker e Docker Compose (para subir o RabbitMQ localmente)

Verifique versões:
```
java -version
mvn -v
docker --version
docker compose version   # ou: docker-compose --version
```

## Como executar localmente

### 1) Subir o RabbitMQ com Docker
O projeto já inclui um `docker-compose.yml` preparado.
```
docker compose up -d
```
Isso irá expor:
- AMQP: `localhost:5672`
- Management UI: `http://localhost:15672` (usuário: `guest`, senha: `guest`)

Para ver os logs do serviço:
```
docker compose logs -f rabbitmq
```

Para parar:
```
docker compose down
```

### 2) Construir o JAR com Maven
Gerar um JAR “fat” (com dependências) usando o plugin de assembly:
```
mvn compile assembly:single
```
O artefato resultante geralmente fica em `target/ProjetoSD2025-1.0-SNAPSHOT-jar-with-dependencies.jar`.

Observação: já existe uma configuração mínima do `maven-assembly-plugin` no `pom.xml`.

### 3) Executar a aplicação
Com o RabbitMQ rodando, execute o JAR:
```
java -jar target/ProjetoSD2025-1.0-SNAPSHOT-jar-with-dependencies.jar
```
Ao iniciar, o app abrirá uma UI no terminal e perguntará o seu nome de usuário.

Dica: você também pode rodar via IDE (IntelliJ IDEA) executando a classe `br.com.tocka.App`.

### 4) Testar conversa entre usuários
Para simular uma conversa, abra duas janelas de terminal e rode a aplicação em ambas, escolhendo usuários diferentes (ex.: `alice` e `bob`). Em seguida, dentro da UI:
- Em `alice`, defina o destinatário com `@bob` e envie mensagens.
- Em `bob`, defina o destinatário com `@alice`.

As mensagens devem aparecer nos respectivos painéis de mensagens e notificações.

## Como usar a interface de chat
- Definir destinatário: digite `@usuarioDestino` e pressione Enter. Ex.: `@bob`.
- Enviar mensagem: digite o texto e pressione Enter. Ex.: `Olá!`.
- Painéis:
  - Mensagens: histórico de mensagens formatado com horário.
  - Notificações: feedbacks como “mensagem enviada”, “mensagem recebida”, erros, etc.
- Prompt: exibe `@destinatario<< ` quando o destinatário está definido; caso contrário, `<< `.

## Estrutura do projeto
```
/home/tocka/IdeaProjects/ProjetoSD2025
├── docker-compose.yml                 # Sobe RabbitMQ (broker + UI de gestão)
├── pom.xml                            # Build Maven e dependências
├── src
│   └── main
│       └── java
│           └── br
│               └── com
│                   └── tocka
│                       ├── App.java                      # Bootstrap da aplicação
│                       ├── controller
│                       │   └── ChatController.java       # Lógica de chat
│                       ├── gui
│                       │   ├── ChatWindow.java           # Janela/Componentes Lanterna
│                       │   └── UsernameModal.java        # Coleta nome de usuário
│                       ├── model
│                       │   ├── ChatMessage.java
│                       │   ├── HistoryEvent.java
│                       │   └── User.java
│                       └── rabbitmq
│                           ├── ConnectionManager.java    # Conexão AMQP
│                           ├── Receiver.java             # Consumo de msgs
│                           └── Sender.java               # Publicação de msgs
└── target/...
```

## Configurações e variáveis
- Host/porta/credenciais do RabbitMQ estão definidos em `App.java` ao criar o `ConnectionManager`:
  - Host: `localhost`
  - Porta: `5672`
  - Usuário: `guest`
  - Senha: `guest`

Se precisar rodar RabbitMQ em outro host/porta/credencial, ajuste em `App.java` ou torne-os configuráveis via variáveis de ambiente/args (futuro incremento sugerido).
