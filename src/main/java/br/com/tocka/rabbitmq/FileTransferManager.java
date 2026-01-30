package br.com.tocka.rabbitmq;

import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;

import br.com.tocka.payload.PayloadProto;
import br.com.tocka.payload.PayloadProto.Content;
import br.com.tocka.payload.PayloadProto.PayloadRequest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class FileTransferManager {

    private Connection connection;
    private Channel channel;
    private String username;
    private FileCallback callback;
    private String downloadDirectory;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private ExecutorService uploadExecutor;

    public interface FileCallback {
        void onFileReceived(String sender, String fileName, LocalDateTime timestamp);
        void onFileSent(String receiver, String fileName);
    }

    public FileTransferManager(Connection connection, String username, FileCallback callback) throws IOException {
        this.connection = connection;
        this.username = username;
        this.callback = callback;
        this.channel = connection.createChannel();
        this.uploadExecutor = Executors.newSingleThreadExecutor((r) -> {
            Thread t = new Thread(r, "FileUploadWorker-" + username);
            t.setDaemon(true);
            return t;
        });
        
        // Criar diretório de downloads se não existir
        this.downloadDirectory = System.getProperty("user.home") + "/chat/downloads";
        Files.createDirectories(Paths.get(downloadDirectory));
        
        setupFileQueue();
    }

    private void setupFileQueue() throws IOException {
        String fileQueueName = username + "_files";
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
                String filePath = downloadDirectory + File.separator + fileName;
                Files.write(Paths.get(filePath), fileBytes);

                if (callback != null) {
                    callback.onFileReceived(sender, fileName, timestamp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        channel.basicConsume(fileQueueName, true, deliverCallback, consumerTag -> {});
    }

    public void sendFile(String recipientUsername, String filePath) throws IOException {
        uploadExecutor.submit(() -> {
            Channel fileChannel = null;
            try {
                // Criar channel específico para esse upload
                fileChannel = connection.createChannel();
                
                Path source = Paths.get(filePath);
                String fileName = source.getFileName().toString();
                String mimeType = Files.probeContentType(source);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                // Ler arquivo
                byte[] fileBytes = Files.readAllBytes(source);

                // Criar payload com arquivo
                PayloadRequest payload = PayloadRequest
                        .newBuilder()
                        .setEmmitter(username)
                        .setReceiver(recipientUsername)
                        .setDate(LocalDateTime.now().format(formatter))
                        .setContent(
                            Content
                                .newBuilder()
                                .setName(fileName)
                                .setType("file")
                                .setMimeType(mimeType)
                                .setBody(ByteString.copyFrom(fileBytes))
                                .build()
                        )
                        .build();

                // Enviar para fila de arquivos do receptor
                String fileQueueName = recipientUsername + "_files";
                java.util.Map<String, Object> args = new java.util.HashMap<>();
                args.put("x-queue-type", "quorum");
                args.put("x-quorum-initial-group-size", 3);
                fileChannel.queueDeclare(fileQueueName, true, false, false, args);

                fileChannel.basicPublish(
                        "",
                        fileQueueName,
                        null,
                        payload.toByteArray()
                );

                if (callback != null) {
                    callback.onFileSent(recipientUsername, fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileChannel != null && fileChannel.isOpen()) {
                    try {
                        fileChannel.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void sendFileToGroup(String groupName, String filePath) throws IOException {
        uploadExecutor.submit(() -> {
            Channel fileChannel = null;
            try {
                // Criar channel específico para esse upload
                fileChannel = connection.createChannel();
                
                Path source = Paths.get(filePath);
                String fileName = source.getFileName().toString();
                String mimeType = Files.probeContentType(source);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                // Ler arquivo
                byte[] fileBytes = Files.readAllBytes(source);

                // Criar payload com arquivo
                PayloadRequest payload = PayloadRequest
                        .newBuilder()
                        .setEmmitter(username)
                        .setGroup(groupName)
                        .setIsGroup(true)
                        .setDate(LocalDateTime.now().format(formatter))
                        .setContent(
                            Content
                                .newBuilder()
                                .setName(fileName)
                                .setType("file")
                                .setMimeType(mimeType)
                                .setBody(ByteString.copyFrom(fileBytes))
                                .build()
                        )
                        .build();

                // Enviar para o group
                String fileExchangeName = groupName + "_files";
                fileChannel.exchangeDeclare(fileExchangeName, "fanout", true);

                fileChannel.basicPublish(
                        fileExchangeName,
                        "",
                        null,
                        payload.toByteArray()
                );

                if (callback != null) {
                    callback.onFileSent(groupName, fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fileChannel != null && fileChannel.isOpen()) {
                    try {
                        fileChannel.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private LocalDateTime parseTimestamp(String dateString) {
        try {
            return LocalDateTime.parse(dateString, formatter);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public void close() throws IOException, TimeoutException {
        uploadExecutor.shutdown();
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }
}
