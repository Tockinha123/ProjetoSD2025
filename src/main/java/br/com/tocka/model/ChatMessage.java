package br.com.tocka.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatMessage {
    private String producer;
    private String consumer;
    private String content;
    private LocalDateTime timestamp;

    // Construtor
    public ChatMessage(String producer, String consumer, String content) {
        this.producer = producer;
        this.consumer = consumer;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public String getProducer() {
        return producer;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        return timestamp.format(formatter);
    }

    // Setters
    public void setProducer(String producer) {
        this.producer = producer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}