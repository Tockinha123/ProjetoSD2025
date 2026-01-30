package br.com.tocka.rabbitmq;

import com.google.protobuf.ByteString;
import com.rabbitmq.client.*;

import br.com.tocka.payload.PayloadProto;

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
            PayloadProto.PayloadRequest payload = PayloadProto.PayloadRequest.parseFrom(delivery.getBody()); 
            //String message = new String (payload.getContent().getBody().toByteArray());
            String message = new String (payload.getContent().getBody().toByteArray());
            //System.out.println(" [x] Recebido: '" + message + "'");

            String sender = payload.getEmmitter();

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