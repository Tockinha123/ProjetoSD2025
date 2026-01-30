package br.com.tocka.controller;

import br.com.tocka.model.ChatMessage;
import br.com.tocka.rabbitmq.Sender;
import com.googlecode.lanterna.gui2.TextBox;

import java.io.IOException;
import java.lang.reflect.Array;
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

    private TextBox messagesBox;
    private TextBox notificationsBox;

    private Sender messageSender;

    public ChatController(String username, TextBox messagesBox, TextBox notificationsBox, Sender messageSender) {
        this.username = username;
        this.currentRecipient = "";
        this.conversations = new HashMap<>();
        this.messagesBox = messagesBox;
        this.notificationsBox = notificationsBox;
        this.messageSender = messageSender;
        this.currentTarget = "";
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (currentTarget.isEmpty()) {
            addToNotifications("Por favor, defina um destinatário com @usuario primeiro");
            return;
        }

        sendMessage(input);
    }

    private void sendMessage(String content) {
        String formattedMessage = ((currentRecipient.startsWith("#")) ? "" : "@") + username + ((currentRecipient.startsWith("#")) ? currentTarget + "#" : "")  + " diz: " + content;

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

    public void receiveMessage(String sender, String content) {
        ChatMessage message = new ChatMessage(sender, username, content);

        conversations.computeIfAbsent(sender, k -> new ArrayList<>()).add(message);

        String formattedMsg = String.format("(%s) %s", message.getFormattedTime(), content);
        addToMessages(formattedMsg);

        addToNotifications("Mensagem recebida de " + sender);
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