package br.com.tocka.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class Sender {

    private Connection connection;
    private Channel channel;

    public Sender(Connection connection) throws IOException {
        this.connection = connection;
        this.channel = connection.createChannel();
    }

    public void sendMessage(String recipientQueue, String message) throws IOException {
            channel.queueDeclare(recipientQueue, true, false, false, null);

            channel.basicPublish(
                    "",
                    recipientQueue,
                    null,
                    message.getBytes(StandardCharsets.UTF_8)
            );
    }

    public void close() throws IOException, TimeoutException {

            if (channel != null && channel.isOpen()) {
                channel.close();
            }
    }
}
