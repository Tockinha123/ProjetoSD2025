package br.com.tocka.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HistoryEvent {
    public enum Type {
        MESSAGE_SENT,      // Mensagem enviada
        MESSAGE_RECEIVED   // Mensagem recebida
    }

    private Type type;
    private String username;      // Com quem foi a interação
    private LocalDateTime timestamp;

    public HistoryEvent(Type type, String username) {
        this.type = type;
        this.username = username;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public Type getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        return timestamp.format(formatter);
    }

    public String getDisplayText() {
        String action = (type == Type.MESSAGE_SENT) ? "enviada para" : "recebida de";
        return String.format("Mensagem %s %s (%s)",
                action, username, getFormattedTime());
    }

    // Setters
    public void setType(Type type) {
        this.type = type;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}