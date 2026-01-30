package br.com.tocka.rabbitmq;

import com.google.protobuf.ByteString;
import com.rabbitmq.client.*;

import br.com.tocka.model.ChatMessage;
import br.com.tocka.payload.PayloadProto;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;

public class Receiver {

    private Connection connection;
    private Channel channel;
    private String queueName;
    private MessageCallback callback;
    private FileCallback fileCallback;

    public interface MessageCallback {
        void onMessageReceived(String sender, String message, LocalDateTime timestamp);
    }

    public interface FileCallback {
        void onFileReceived(String sender, String fileName, LocalDateTime timestamp);
    }

    public Receiver(Connection connection, String username, MessageCallback callback) throws IOException {
        this.connection = connection;
        this.queueName = username;
        this.callback = callback;
        this.channel = connection.createChannel();

        setupQueue();
    }

    public void setFileCallback(FileCallback fileCallback) {
        this.fileCallback = fileCallback;
        try {
            setupFileQueue();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void subscribeToGroupFiles(String groupName) throws IOException {
        String groupFileExchangeName = groupName + "_files";
        String groupFileQueueName = queueName + "_" + groupName + "_files";

        // Declarar exchange de arquivos do grupo
        channel.exchangeDeclare(groupFileExchangeName, "fanout", true);

        // Declarar fila exclusiva para esse usuário nesse grupo
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("x-queue-type", "quorum");
        args.put("x-quorum-initial-group-size", 3);
        channel.queueDeclare(groupFileQueueName, true, false, false, args);

        // Ligar fila ao exchange de arquivos do grupo
        channel.queueBind(groupFileQueueName, groupFileExchangeName, "");

        // Configurar consumer para essa fila de grupo
        DeliverCallback groupFileDeliverCallback = (consumerTag, delivery) -> {
            try {
                PayloadProto.PayloadRequest payload = PayloadProto.PayloadRequest.parseFrom(delivery.getBody());
                String fileName = payload.getContent().getName();
                String sender = payload.getEmmitter();
                byte[] fileBytes = payload.getContent().getBody().toByteArray();
                LocalDateTime timestamp = parseTimestamp(payload.getDate());

                // Salvar arquivo no diretório de downloads
                String downloadDirectory = System.getProperty("user.home") + "/chat/downloads";
                Files.createDirectories(Paths.get(downloadDirectory));
                String filePath = downloadDirectory + File.separator + fileName;
                
                // Gerar nome único se arquivo já existe
                filePath = getUniqueFileName(filePath);
                
                Files.write(Paths.get(filePath), fileBytes);

                if (fileCallback != null) {
                    fileCallback.onFileReceived(sender, Paths.get(filePath).getFileName().toString(), timestamp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        channel.basicConsume(groupFileQueueName, true, groupFileDeliverCallback, consumerTag -> {});
    }

    private void setupQueue() throws IOException {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("x-queue-type", "quorum");
        args.put("x-quorum-initial-group-size", 3);
        channel.queueDeclare(queueName, true, false, false, args);

        //System.out.println("Aguardando mensagens na fila: " + queueName);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                PayloadProto.PayloadRequest payload = PayloadProto.PayloadRequest.parseFrom(delivery.getBody()); 
                String message = new String (payload.getContent().getBody().toByteArray());

                String sender = payload.getEmmitter();
                LocalDateTime timestamp = parseTimestamp(payload.getDate());

                if (callback != null) {
                    callback.onMessageReceived(sender, message, timestamp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }

    private void setupFileQueue() throws IOException {
        String fileQueueName = queueName + "_files";
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("x-queue-type", "quorum");
        args.put("x-quorum-initial-group-size", 3);
        channel.queueDeclare(fileQueueName, true, false, false, args);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                PayloadProto.PayloadRequest payload = PayloadProto.PayloadRequest.parseFrom(delivery.getBody());
                String fileName = payload.getContent().getName();
                String sender = payload.getEmmitter();
                byte[] fileBytes = payload.getContent().getBody().toByteArray();
                LocalDateTime timestamp = parseTimestamp(payload.getDate());

                // Salvar arquivo no diretório de downloads
                String downloadDirectory = System.getProperty("user.home") + "/chat/downloads";
                Files.createDirectories(Paths.get(downloadDirectory));
                String filePath = downloadDirectory + File.separator + fileName;
                
                // Gerar nome único se arquivo já existe
                filePath = getUniqueFileName(filePath);
                
                Files.write(Paths.get(filePath), fileBytes);

                if (fileCallback != null) {
                    fileCallback.onFileReceived(sender, Paths.get(filePath).getFileName().toString(), timestamp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        channel.basicConsume(fileQueueName, true, deliverCallback, consumerTag -> {});
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

    private String getUniqueFileName(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return filePath;
        }

        String fileName = path.getFileName().toString();
        String directory = path.getParent().toString();
        
        int lastDot = fileName.lastIndexOf('.');
        String nameWithoutExt = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        String extension = lastDot > 0 ? fileName.substring(lastDot) : "";

        int counter = 1;
        while (true) {
            String newFileName = nameWithoutExt + "_" + counter + extension;
            String newFilePath = directory + File.separator + newFileName;
            if (!Files.exists(Paths.get(newFilePath))) {
                return newFilePath;
            }
            counter++;
        }
    }

    private LocalDateTime parseTimestamp(String dateString) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
            return LocalDateTime.parse(dateString, formatter);
        } catch (Exception e) {
            // Se houver erro ao parsear, retorna a hora atual
            return LocalDateTime.now();
        }
    }

    public void close() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}
