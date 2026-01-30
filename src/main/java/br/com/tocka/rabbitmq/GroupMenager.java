package br.com.tocka.rabbitmq;

import br.com.tocka.model.ChatMessage;
import br.com.tocka.rabbitmq.Receiver.MessageCallback;

import java.io.IOException;

import com.rabbitmq.client.*;

public class GroupMenager {

    private Channel channel;

    GroupMenager (Channel channel) {
        this.channel = channel;
    }

    public void createGroup (String groupName) throws IOException{
        this.channel.exchangeDeclare(groupName, "fanout", true);
    }

    public void addNewUser (String username, String groupName) throws IOException{
        this.channel.queueDeclare(username, true, false, false, null);
        this.channel.queueBind(username, groupName, "");
    }

    public void removeUserFromGroup (String username, String groupName) throws IOException{
        this.channel.queueUnbind(username, groupName, "");
    }

}
