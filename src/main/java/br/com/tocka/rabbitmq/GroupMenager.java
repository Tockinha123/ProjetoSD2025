package br.com.tocka.rabbitmq;

import br.com.tocka.model.ChatMessage;
import br.com.tocka.rabbitmq.Receiver.MessageCallback;

import java.io.IOException;
import java.util.List;

import com.rabbitmq.client.*;

public class GroupMenager {

    private Channel channel;
    private RabbitMQAPIClient apiClient;

    GroupMenager (Channel channel) {
        this.channel = channel;
        this.apiClient = null;
    }

    public void setAPIClient(RabbitMQAPIClient apiClient) {
        this.apiClient = apiClient;
    }

    public void createGroup (String groupName) throws IOException{
        this.channel.exchangeDeclare(groupName, "fanout", true);
    }

    public void addNewUser (String username, String groupName) throws IOException{
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("x-queue-type", "quorum");
        args.put("x-quorum-initial-group-size", 3);
        this.channel.queueDeclare(username, true, false, false, args);
        this.channel.queueBind(username, groupName, "");
    }

    public void removeUserFromGroup (String username, String groupName) throws IOException{
        this.channel.queueUnbind(username, groupName, "");
    }

    public List<String> listUsersInGroup(String groupName) throws IOException {
        if (apiClient == null) {
            throw new IOException("API Client não foi inicializado");
        }
        return apiClient.listUsersInGroup(groupName);
    }

    public List<String> listGroupsForUser(String username) throws IOException {
        if (apiClient == null) {
            throw new IOException("API Client não foi inicializado");
        }
        return apiClient.listGroupsForUser(username);
    }

}
