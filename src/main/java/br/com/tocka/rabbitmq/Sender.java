package br.com.tocka.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import br.com.tocka.model.ChatMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;


public class Sender {

    private Connection connection;
    private Channel channel;
    private GroupMenager groupMenager;
    private String host;
    private int managementPort;
    private String username;
    private String password;

    public Sender(Connection connection, String host, int managementPort, String username, String password) throws IOException {
        this.connection = connection;
        this.host = host;
        this.managementPort = managementPort;
        this.username = username;
        this.password = password;
        this.channel = connection.createChannel();

        this.groupMenager = new GroupMenager(this.channel);
        
        // Inicializar o API Client para o GroupManager
        RabbitMQAPIClient apiClient = new RabbitMQAPIClient(host, managementPort, username, password);
        this.groupMenager.setAPIClient(apiClient);
    }

    public void sendMessageToGroup(String groupName, ChatMessage message) throws IOException {

            channel.basicPublish(
                    groupName,
                    "",
                    null,
                    message.getReadyPayload()
            );
    }

    public void sendMessage(String recipientQueue, ChatMessage message) throws IOException {
            channel.queueDeclare(recipientQueue, true, false, false, null);

            channel.basicPublish(
                    "",
                    recipientQueue,
                    null,
                    message.getReadyPayload()
            );
    }

    public Channel getChannel() {
        return channel;
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() throws IOException, TimeoutException {

            if (channel != null && channel.isOpen()) {
                channel.close();
            }
    }

    public GroupMenager getGroupMenager() {
        return groupMenager;
    }
}