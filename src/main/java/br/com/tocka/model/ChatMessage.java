package br.com.tocka.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.protobuf.ByteString;

import br.com.tocka.payload.PayloadProto;
import br.com.tocka.payload.PayloadProto.Content;
import br.com.tocka.payload.PayloadProto.PayloadRequest;

public class ChatMessage {
    private String producer;
    private String consumer;
    private String content;
    private LocalDateTime timestamp;
    private PayloadRequest payload;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    // Construtor padrão
    public ChatMessage(String producer, String consumer, String content) {
        this(producer, consumer, content, LocalDateTime.now());
    }

    // Construtor com timestamp customizado
    public ChatMessage(String producer, String consumer, String content, LocalDateTime timestamp) {
        this.producer = producer;
        this.consumer = consumer;
        this.content = content;
        this.timestamp = timestamp;
        this.payload = PayloadRequest
                                    .newBuilder()
                                    .setEmmitter(producer)
                                    .setReceiver(consumer)
                                    .setDate(timestamp.format(formatter))
                                    .setContent(
                                        Content
                                            .newBuilder()
                                            .setName("null")
                                            .setBody(ByteString.copyFrom(content.getBytes()))
                                            .build()
                                    )
                                    .build();
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
        return this.timestamp.format(formatter);
    }

    public byte[] getReadyPayload(){
        return this.payload.toByteArray();
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