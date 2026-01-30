package br.com.tocka.controller;

import br.com.tocka.model.ChatMessage;
import br.com.tocka.rabbitmq.FileTransferManager;
import br.com.tocka.rabbitmq.Receiver;
import br.com.tocka.rabbitmq.Sender;
import com.googlecode.lanterna.gui2.TextBox;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatController {

    private String username;
    private String currentRecipient;
    private String currentTarget;
    private Map<String, List<ChatMessage>> conversations;
    private Map<String, Boolean> connectedGroups;

    private TextBox messagesBox;
    private TextBox notificationsBox;

    private Sender messageSender;
    private FileTransferManager fileTransferManager;
    private Receiver messageReceiver;

    public ChatController(String username, TextBox messagesBox, TextBox notificationsBox, Sender messageSender) {
        this.username = username;
        this.currentRecipient = "";
        this.conversations = new HashMap<>();
        this.connectedGroups = new HashMap<>();
        this.messagesBox = messagesBox;
        this.notificationsBox = notificationsBox;
        this.messageSender = messageSender;
        this.currentTarget = "";
        this.fileTransferManager = null;
        this.messageReceiver = null;
    }

    public void setFileTransferManager(FileTransferManager fileTransferManager) {
        this.fileTransferManager = fileTransferManager;
    }

    public void setMessageReceiver(Receiver messageReceiver) {
        this.messageReceiver = messageReceiver;
    }

    public void processInput(String input) {
        input = input.trim();

        if (input.isEmpty()) {
            return;
        }

        if (input.startsWith("@") || input.startsWith("#")) {
            String newRecipient = input.trim();

            if (!newRecipient.isEmpty()) {
                currentRecipient = newRecipient;
                currentTarget = currentRecipient.substring(1);
            }
            return;
        }
        else if (input.startsWith("!")){
            int index = 0;
            if ((index = input.indexOf("addGroup", 0)) != -1){
                String groupName = input.substring(index+("addgroup").length()).trim();
                if (groupName.isEmpty()){
                    addToNotifications("Comando incorreto!");
                    return;
                }
                try{
                    messageSender.getGroupMenager().createGroup(groupName);
                    addToNotifications("Novo grupo criado: " + groupName);
                    
                    // Se conectar ao exchange de arquivos do grupo
                    if (messageReceiver != null) {
                        messageReceiver.subscribeToGroupFiles(groupName);
                    }
                } catch (IOException e){
                    e.printStackTrace();
                }
            }

            else if ((index = input.indexOf("addUser", 0)) != -1){
                List<String> splitted = new ArrayList<>(Arrays.asList(input.split(" ")));

                if (splitted.size() != 3){
                    addToNotifications("Comando incorreto!");
                    return;
                }

                try {
                    messageSender.getGroupMenager().addNewUser(splitted.get(1), splitted.get(2));
                    addToNotifications("Usuário " + splitted.get(1) + " adicionado ao grupo " + splitted.get(2));
                    
                    // Se o usuário atual foi adicionado, se conectar ao exchange de arquivos do grupo
                    if (splitted.get(1).equals(username) && messageReceiver != null) {
                        messageReceiver.subscribeToGroupFiles(splitted.get(2));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            else if ((index = input.indexOf("removeUser", 0)) != -1){
                List<String> splitted = new ArrayList<>(Arrays.asList(input.split(" ")));

                if (splitted.size() != 3){
                    addToNotifications("Comando incorreto!");
                    return;
                }

                try {
                    messageSender.getGroupMenager().removeUserFromGroup(splitted.get(1), splitted.get(2));
                    addToNotifications("Usuário " + splitted.get(1) + " removido do grupo " + splitted.get(2));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            else if ((index = input.indexOf("upload", 0)) != -1){
                String filePath = input.substring(index + 6).trim();
                if (filePath.isEmpty()){
                    addToNotifications("Comando incorreto! Use: !upload /path/to/file");
                    return;
                }

                // Verificar se arquivo existe
                if (!Files.exists(Paths.get(filePath))){
                    addToNotifications("Arquivo não encontrado: " + filePath);
                    return;
                }

                // Verificar se há destinatário definido
                if (currentTarget.isEmpty()){
                    addToNotifications("Por favor, defina um destinatário com @usuario ou #grupo");
                    return;
                }

                // Enviar arquivo
                try {
                    String fileName = Paths.get(filePath).getFileName().toString();
                    addToNotifications("Enviando \"" + filePath + "\" para " + currentRecipient + ".");

                    if (fileTransferManager != null) {
                        if (currentRecipient.startsWith("@")){
                            fileTransferManager.sendFile(currentTarget, filePath);
                        }
                        else if (currentRecipient.startsWith("#")){
                            fileTransferManager.sendFileToGroup(currentTarget, filePath);
                        }
                    }
                } catch (IOException e) {
                    addToNotifications("Erro ao enviar arquivo: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            return;
        }

        if (currentTarget.isEmpty()) {
            addToNotifications("Por favor, defina um destinatário com @usuario primeiro");
            return;
        }

        sendMessage(input);
    }

    private void sendMessage(String content) {
        String formattedMessage = new String("");
        
        if (currentRecipient.startsWith("#")){
            formattedMessage = username + "#" + currentTarget;
        }
        else if (currentRecipient.startsWith("@")){
            formattedMessage = "@" + currentTarget;
        }

        formattedMessage += " diz: " + content;
        
        ChatMessage message = new ChatMessage(username, currentTarget, formattedMessage);

        conversations.computeIfAbsent(currentTarget, k -> new ArrayList<>()).add(message);

        addToMessages("Você enviou: " + content);

        addToNotifications("Você enviou a mensagem para: " + currentRecipient);

        if (messageSender != null) {
            try {
                if (currentRecipient.startsWith("@")){
                    messageSender.sendMessage(currentTarget, message);
                }
                else if (currentRecipient.startsWith("#")){
                    messageSender.sendMessageToGroup(currentTarget, message);
                }
            } catch (IOException e) {
                addToNotifications("Erro ao enviar mensagem: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void receiveMessage(String sender, String content, LocalDateTime timestamp) {
        ChatMessage message = new ChatMessage(sender, username, content, timestamp);

        conversations.computeIfAbsent(sender, k -> new ArrayList<>()).add(message);

        String formattedMsg = String.format("(%s) %s", message.getFormattedTime(), content);
        addToMessages(formattedMsg);

        addToNotifications("Mensagem recebida de " + sender);

        // Auto-conectar a exchanges de arquivo de grupos conhecidos
        autoConnectToGroupFileExchanges(sender, content);
    }

    private void autoConnectToGroupFileExchanges(String sender, String content) {
        // Detectar se a mensagem contém nome de grupo (padrão: #groupname)
        // Quando um usuário envia mensagem de grupo, conectar automaticamente
        if (content != null && content.contains("#")) {
            try {
                // Extrair nome do grupo da mensagem (padrão: "usuario#groupname diz: ...")
                int hashIndex = content.indexOf("#");
                int spaceIndex = content.indexOf(" ", hashIndex);
                if (hashIndex > 0 && spaceIndex > hashIndex) {
                    String groupName = content.substring(hashIndex + 1, spaceIndex);
                    
                    // Se ainda não está conectado a este grupo, conectar
                    if (!connectedGroups.containsKey(groupName) && messageReceiver != null) {
                        messageReceiver.subscribeToGroupFiles(groupName);
                        connectedGroups.put(groupName, true);
                    }
                }
            } catch (Exception e) {
                // Se houver erro ao fazer parsing, ignorar silenciosamente
            }
        }
    }

    public void receiveFile(String sender, String fileName, LocalDateTime timestamp) {
        String formattedMsg = String.format("(%s) Arquivo \"%s\" recebido de @%s !", timestamp.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")), fileName, sender);
        addToMessages(formattedMsg);
        addToNotifications("Arquivo recebido de " + sender);
    }

    public void fileSent(String receiver, String fileName) {
        addToNotifications("Arquivo \"" + fileName + "\" foi enviado para " + receiver + " !");
    }

    private void addToMessages(String text) {
        String currentText = messagesBox.getText();
        String newText = currentText + (currentText.isEmpty() ? "" : "\n") + text;
        messagesBox.setText(newText);
    }

    private void addToNotifications(String notification) {
        String currentText = notificationsBox.getText();
        String newText = currentText + (currentText.isEmpty() ? "" : "\n") + notification;
        notificationsBox.setText(newText);
    }

    public String getCurrentPrompt() {
        if (currentTarget.isEmpty()) {
            return "<< ";
        }
        return currentRecipient + "<< ";
    }

    public String getUsername() {
        return username;
    }

    public String getCurrentRecipient() {
        return currentRecipient;
    }

    public Map<String, List<ChatMessage>> getConversations() {
        return conversations;
    }
}