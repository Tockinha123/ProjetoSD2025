package br.com.tocka.controller;

import br.com.tocka.model.ChatMessage;
import br.com.tocka.rabbitmq.Sender;
import com.googlecode.lanterna.gui2.TextBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatController {

    private String username;
    private String currentRecipient;
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
    }

    public void processInput(String input) {
        input = input.trim();

        if (input.isEmpty()) {
            return;
        }

        if (input.startsWith("@")) {
            String newRecipient = input.substring(1).trim();

            if (!newRecipient.isEmpty()) {
                currentRecipient = newRecipient;
            }
            return;
        }

        if (currentRecipient.isEmpty()) {
            addToNotifications("Por favor, defina um destinatário com @usuario primeiro");
            return;
        }

        sendMessage(input);
    }

    private void sendMessage(String content) {
        String formattedMessage = "@" + username + " diz: " + content;

        ChatMessage message = new ChatMessage(username, currentRecipient, formattedMessage);

        conversations.computeIfAbsent(currentRecipient, k -> new ArrayList<>()).add(message);

        addToMessages("Você enviou: " + content);

        addToNotifications("Você enviou a mensagem para: @" + currentRecipient);

        if (messageSender != null) {
            try {
                messageSender.sendMessage(currentRecipient, formattedMessage);
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
        if (currentRecipient.isEmpty()) {
            return "<< ";
        }
        return "@" + currentRecipient + "<< ";
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