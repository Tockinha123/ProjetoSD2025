# Implementação do Sistema de Envio de Arquivos

## Resumo das Alterações

Este documento descreve a implementação do sistema de envio de arquivos para o chat distribuído usando RabbitMQ e Protocol Buffers.

## Requisitos Implementados

### 1. Comando `!upload`
- **Formato**: `@usuario<< !upload /path/to/file` ou `#grupo<< !upload /path/to/file`
- **Validações**:
  - Verifica se o arquivo existe
  - Verifica se um destinatário foi definido (@usuario ou #grupo)
  - Exibe mensagens de erro apropriadas

### 2. Mensagens não-bloqueantes
- O envio de arquivos é executado em uma **thread separada**
- Exibe imediatamente: `Enviando "/path/to/file" para @usuario.`
- O chat retorna ao prompt instantaneamente
- Processo 100% assíncrono em background

### 3. Confirmação de Envio
- Após transferência para RabbitMQ: `Arquivo "/path/to/file" foi enviado para @usuario !`
- Enviado via notificações sem bloquear o chat

### 4. Recebimento de Arquivos
- Executado em **thread separada** (não-bloqueante)
- Download automático em: `~/chat/downloads`
- Pasta criada automaticamente se não existir
- Mensagem com timestamp: `(21/09/2024 às 20:55) Arquivo "aula1.pdf" recebido de @tarcisio !`

## Arquitetura Implementada

### Filas RabbitMQ

#### Mensagens de Texto
- **Queue individual**: `{username}` - para mensagens de texto
- **Exchange de grupo**: `{groupname}` - para mensagens em grupo

#### Arquivos (Separados Completamente)

**Para usuários individuais:**
- **Queue individual**: `{username}_files` - para recebimento de arquivos

**Para grupos (criado dinamicamente):**
- **Exchange fanout**: `{groupname}_files` - para distribuir arquivos do grupo
- **Queue individual por grupo**: `{username}_{groupname}_files` - fila exclusiva por usuário/grupo

**Exemplo prático:**
- Usuário `tarcisio` recebendo arquivo de `marcio`: queue `tarcisio_files`
- Usuário `tarcisio` recebendo arquivo do grupo `ufs`: queue `tarcisio_ufs_files` (conectada ao exchange `ufs_files`)
- Usuário `tarcisio` recebendo arquivo do grupo `engenharia`: queue `tarcisio_engenharia_files` (conectada ao exchange `engenharia_files`)

### Classes Modificadas

#### 1. **payload.proto**
```protobuf
message Content {
  string type       = 1;      // "text" ou "file"
  bytes body        = 2;      // Conteúdo (texto ou bytes do arquivo)
  string name       = 3;      // Nome do arquivo
  string mimeType   = 4;      // NOVO: Tipo MIME do arquivo
}
```

#### 2. **FileTransferManager.java** (Nova Classe)
Classe responsável pelo gerenciamento de transfers de arquivo:
- `ExecutorService uploadExecutor` com single thread para fila FIFO
- `sendFile(recipientUsername, filePath)` - Enviar arquivo para usuário (enfileirado)
- `sendFileToGroup(groupName, filePath)` - Enviar arquivo para grupo (enfileirado)
- Interface `FileCallback` para notificações de envio/recebimento
- Cada upload cria seu próprio `channel` para evitar contenção
- Detecção automática de tipo MIME usando `Files.probeContentType()`
- Cria diretório de downloads automaticamente

**Fila FIFO:**
```java
private ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();

// Cada chamada a sendFile() ou sendFileToGroup() enfileira a tarefa
uploadExecutor.submit(() -> {
    Channel fileChannel = connection.createChannel();  // Novo channel por upload
    // ... envio do arquivo ...
    fileChannel.close();
});
```

**Garantias:**
- Se `arquivo1.pdf` é enviado antes de `arquivo2.pdf`, ele chega primeiro no receptor
- Arquivos são enviados sequencialmente, não em paralelo
- GUI nunca fica bloqueada (tarefa vai para fila de ExecutorService)

#### 3. **ChatController.java**
Adições:
- Campo `fileTransferManager` para gerenciar transfers
- Método `setFileTransferManager()` para injeção de dependência
- Novo tratamento para comando `!upload`:
  - Validação de arquivo e destinatário
  - Execução assíncrona
  - Mensagens não-bloqueantes
- Novos métodos:
  - `receiveFile(sender, fileName, timestamp)` - Receber arquivo
  - `fileSent(receiver, fileName)` - Notificar envio

#### 4. **Sender.java**
Adições:
- Métodos `getChannel()` e `getConnection()` para acesso ao FileTransferManager
- Sem mudança na funcionalidade existente (compatível)

#### 5. **Receiver.java**
Adições:
- Interface `FileCallback` para notificações de recebimento de arquivo
- Método `setFileCallback()` para injeção de callback
- Nova fila `{username}_files` para recebimento de arquivos individuais
- **Novo método `subscribeToGroupFiles(groupName)`** para se conectar dinamicamente a exchanges de arquivos de grupo
  - Cria exchange fanout `{groupname}_files`
  - Cria fila exclusiva `{username}_{groupname}_files`
  - Liga fila ao exchange
  - Configura consumer para receber arquivos do grupo
- **Novo método `getUniqueFileName()`** para gerar nomes únicos de arquivo
  - Se arquivo já existe, adiciona sufixo: `documento_1.pdf`, `documento_2.pdf`, etc.
  - Preserva extensão original
  - Incrementa até encontrar nome disponível
- Download automático com criação de diretório
- Parsing automático de timestamp
- Aplicado tanto para arquivos individuais quanto de grupo

#### 6. **ChatController.java**
Modificações:
- Campo `messageReceiver` para gerenciar inscrição em grupos
- Campo `connectedGroups` (Map) para rastrear quais grupos o usuário está conectado
- Método `setMessageReceiver()` para injeção de dependência
- **Comando `!addGroup`** agora chama `messageReceiver.subscribeToGroupFiles(groupName)` automaticamente
- **Comando `!addUser`** agora chama `messageReceiver.subscribeToGroupFiles(groupName)` automaticamente quando usuário atual é adicionado
- **Novo método `autoConnectToGroupFileExchanges(sender, content)`** - Auto-conexão dinâmica
  - Detecta quando mensagens de grupo são recebidas (padrão: `usuario#groupname diz: ...`)
  - Chama automaticamente `subscribeToGroupFiles()` para cada novo grupo
  - Permite que usuários recebam arquivos mesmo que não tenham sido os que criaram o grupo
  - **Resolve problema:** Todos os usuários do grupo recebem arquivos enviados para o grupo
- Instanciação de `FileTransferManager`
- Passagem de `FileCallback` implementado
- Injeção de `FileTransferManager` no `ChatController`
- Injeção de `FileCallback` no `Receiver`
- Limpeza adequada de recursos no fechamento

#### 7. **ChatWindow.java**
Modificações:
- Instanciação de `FileTransferManager`
- Passagem de `FileCallback` implementado
- Injeção de `FileTransferManager` no `ChatController`
- **Injeção de `Receiver` no `ChatController`** para permitir subscriptions dinâmicas
- Injeção de `FileCallback` no `Receiver`
- Limpeza adequada de recursos no fechamento

## Fluxo de Envio de Arquivo

```
Usuário A -> Chat A
  |
  | !upload /home/user/file.pdf @userB
  |
  v
ChatController (valida e processa)
  |
  | (exibe: Enviando "/home/user/file.pdf" para @userB.)
  |
  v
FileTransferManager.sendFile() (não-bloqueante)
  |
  | uploadExecutor.submit() → Tarefa enfileirada
  |
  v
ExecutorService (fila FIFO - single thread)
  |
  | Tarefa 1: arquivo1.pdf
  | ├─ Cria channel específico
  | ├─ Detecta tipo MIME
  | ├─ Serializa payload
  | ├─ channel.basicPublish() → RabbitMQ
  | └─ Fecha channel
  |
  | (Tarefa 2 aguarda aqui se arquivo2.pdf foi enfileirado antes)
  |
  v
RabbitMQ (queue: userB_files)
  |
  | (transferência de dados)
  |
  v
Receptor.FileCallback
  |
  | (salva em ~/chat/downloads/file.pdf)
  | (notifica: Arquivo "file.pdf" foi enviado para @userB !)
  |
  v
ChatWindow (exibe em mensagens)
  |
  | (21/09/2024 às 20:55) Arquivo "file.pdf" recebido de @userA !
```

**Garantia FIFO:**
- Se arquivo1 é enfileirado em t=100ms
- Se arquivo2 é enfileirado em t=102ms
- Então arquivo1 sempre chega no receptor ANTES que arquivo2

## Fluxo de Recebimento de Arquivo

```
RabbitMQ (queue: {username}_files OU queue: {username}_{groupname}_files)
  |
  | (delivery callback)
  |
  v
Receiver.setupFileQueue() OU Receiver.subscribeToGroupFiles()
  |
  | (thread de consumo RabbitMQ)
  |
  v
FileCallback.onFileReceived()
  |
  | (GUI thread - invokeLater)
  |
  v
ChatController.receiveFile()
  |
  | (exibe no TextBox de mensagens)
  | (notifica na aba de notificações)
```

## Fluxo de Subscription Dinâmica em Grupo

```
Usuário executa: !addGroup ufs
  |
  v
ChatController.addGroup()
  |
  | messageReceiver.subscribeToGroupFiles("ufs")
  |
  v
Receiver.subscribeToGroupFiles("ufs")
  |
  | 1. channel.exchangeDeclare("ufs_files", "fanout", true)
  | 2. channel.queueDeclare("tarcisio_ufs_files", true, false, false, null)
  | 3. channel.queueBind("tarcisio_ufs_files", "ufs_files", "")
  | 4. channel.basicConsume("tarcisio_ufs_files", ...)
  |
  v
Pronto para receber arquivos do grupo ufs

---

Quando arquivo é enviado para grupo: #ufs<< !upload /home/tarcisio/aula1.pdf
  |
  v
FileTransferManager.sendFileToGroup("ufs", filePath)
  |
  | channel.basicPublish("ufs_files", "", payload)
  |
  v
RabbitMQ distribui via fanout para todas as filas ligadas
  |
  | (tarcisio_ufs_files recebe)
  | (marcio_ufs_files recebe)
  | (outra_pessoa_ufs_files recebe)
  |
  v
Cada Receiver processa independentemente
```

## Tipos MIME Suportados

O sistema detecta automaticamente o tipo MIME usando `Files.probeContentType()`:
- Documentos: `.pdf`, `.doc`, `.docx`, `.txt`, etc.
- Imagens: `.jpg`, `.png`, `.gif`, `.bmp`, etc.
- Arquivos: `.zip`, `.rar`, `.7z`, etc.
- Se não detectado: `application/octet-stream`

## Sincronização e Thread-Safety

- **Fila FIFO de uploads**: `ExecutorService` com single thread garante ordem de envio
- **Channel por upload**: Cada transferência cria seu próprio channel RabbitMQ
- **Sem contenção**: Uploads não competem pelo mesmo channel
- **Ordem garantida**: Arquivo enviado primeiro chega primeiro (FIFO)
- **GUI Thread**: Callbacks sempre executados via `gui.getGUIThread().invokeLater()`
- **Sincronização RabbitMQ**: Gerenciada internamente pela biblioteca
- **Subscriptions dinâmicas**: Cada chamada a `subscribeToGroupFiles()` cria um novo consumer na thread de IO do RabbitMQ

## Tratamento de Erros

- Arquivo não encontrado: mensagem de notificação
- Destinatário não definido: mensagem de erro
- Erro ao enviar: mensagem com exceção capturada
- Erro ao receber: logged no console (não bloqueia chat)

## Diretório de Downloads

- **Localização**: `$HOME/chat/downloads`
- **Criação automática**: Sim, no primeiro recebimento
- **Nomeação de arquivos**:
  - Preserva nome original do arquivo
  - Se arquivo já existe, adiciona sufixo: `documento_1.pdf`, `documento_2.pdf`, etc.
  - Incrementa até encontrar nome disponível
  - Nunca sobrescreve arquivos existentes

## Auto-Conexão Dinâmica a Grupos

O sistema detecta automaticamente quando um usuário recebe mensagens de grupo e se conecta aos exchanges de arquivo:

```
1. bruno envia mensagem para grupo:
   #ufs<< mensagem teste
   → Mensagem: "bruno#ufs diz: mensagem teste"

2. will recebe a mensagem
   receiveMessage("bruno", "bruno#ufs diz: mensagem teste", timestamp)
   → autoConnectToGroupFileExchanges() detecta padrão "#ufs"
   → will.subscribeToGroupFiles("ufs") é chamado automaticamente
   → will recebe própria queue: will_ufs_files

3. bruno envia arquivo para grupo:
   #ufs<< !upload arquivo.pdf
   → Arquivo vai para exchange ufs_files
   → AMBAS as queues recebem (bruno_ufs_files E will_ufs_files)
   → Ambos recebem o arquivo ✅
```

**Vantagens:**
- ✅ Usuários não precisam executar comando especial para receber arquivos de grupo
- ✅ Funciona automaticamente apenas recebendo mensagens
- ✅ Idempotente (não tenta conectar duas vezes)
- ✅ Rastreado em Map `connectedGroups`

## Compatibilidade

- ✅ Compatível com código existente
- ✅ Não quebra funcionalidade de mensagens de texto
- ✅ Suporta envio para usuários individuais
- ✅ Suporta envio para grupos
- ✅ Totalmente assíncrono e não-bloqueante
- ✅ Canais completamente separados (não compartilham RabbitMQ queues)
- ✅ Subscriptions dinâmicas em exchanges de arquivo de grupo
- ✅ Cada usuário tem fila exclusiva por grupo (fanout)
- ✅ Fila FIFO com ordem de envio garantida
- ✅ Sem contenção de channel (cada upload tem seu próprio)
- ✅ Auto-conexão dinâmica a grupos (detecta padrão #groupname)
- ✅ Nomes únicos de arquivo (sufixos automáticos)
- ✅ Nunca sobrescreve arquivos existentes

## Testes Recomendados

1. Envio de arquivo para usuário individual
2. Envio de arquivo para grupo (criar grupo primeiro)
3. Arquivo não encontrado (erro)
4. Destinatário não definido (erro)
5. Múltiplos envios simultâneos (fila FIFO - verifica ordem)
6. Recebimento de múltiplos arquivos
7. Verificação de tipos MIME
8. Verificação da pasta de downloads
9. Adicionar usuário a grupo e verificar se consegue receber arquivos do grupo
10. Criar grupo e verificar se exchange de arquivos é criado dinamicamente
11. Múltiplos usuários recebendo mesmo arquivo de grupo (fanout)
12. **Auto-conexão dinâmica:** Usuário B recebe mensagem de grupo de Usuário A, depois recebe arquivo enviado para o grupo (sem comandos extras)
13. **Nomes únicos:** Receber mesmo arquivo múltiplas vezes e verificar sufixos (_1, _2, etc.)
14. **FIFO:** Enviar arquivo1.pdf, depois arquivo2.pdf, e verificar que arquivo1 chega primeiro no receptor
5. Múltiplos envios simultâneos
6. Recebimento de múltiplos arquivos
7. Verificação de tipos MIME
8. Verificação da pasta de downloads
9. Adicionar usuário a grupo e verificar se consegue receber arquivos do grupo
10. Criar grupo e verificar se exchange de arquivos é criado dinamicamente
11. Múltiplos usuários recebendo mesmo arquivo de grupo (fanout)

## Build e Execução

```bash
# Compilar
mvn clean compile

# Construir JAR (se necessário)
mvn package

# Executar
./run.sh
```

## Dependências Adicionadas

- Nenhuma nova dependência externa (usa bibliotecas existentes)
- `Files` e `Paths` de `java.nio.file` (JDK 7+)
- `Files.probeContentType()` (JDK 7+)
