package br.com.tocka.rabbitmq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class ConnectionManager {

    private Connection connection;
    private String host;
    private int port;
    private String username;
    private String password;

    public ConnectionManager(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void connect() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);

        connection = factory.newConnection();
        System.out.println("Conectado em " + host + ":" + port);
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() throws IOException {

        if (connection != null && connection.isOpen()) {
            connection.close();
            //System.out.println("Conexão fechada");
        }
    }
}

