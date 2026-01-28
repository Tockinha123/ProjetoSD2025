package br.com.tocka.rabbitmq;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class Receiver {

    private Connection connection;
    private Channel channel;
    private String queueName;
    private MessageCallback callback;

    public interface MessageCallback {
        void onMessageReceived(String sender, String message);
    }

    public Receiver(Connection connection, String username, MessageCallback callback) throws IOException {
        this.connection = connection;
        this.queueName = username;
        this.callback = callback;
        this.channel = connection.createChannel();

        setupQueue();
    }

    private void setupQueue() throws IOException {
        channel.queueDeclare(queueName, true, false, false, null);

        //System.out.println("Aguardando mensagens na fila: " + queueName);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            //System.out.println(" [x] Recebido: '" + message + "'");

            String sender = extractSender(message);

            if (callback != null) {
                callback.onMessageReceived(sender, message);
            }

            //System.out.println(" [x] Processado");
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    private String extractSender(String message) {
        if (message.startsWith("@")) {
            int endIndex = message.indexOf(" diz:");
            if (endIndex > 0) {
                return message.substring(1, endIndex);
            }
        }
        return "";
    }

    public void close() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}