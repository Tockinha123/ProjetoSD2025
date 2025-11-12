package br.com.tocka.model;

public class User {
    private String username;
    private String queueName;

    public User(String username) {
        this.username = username;
        this.queueName = username;

}

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

}
