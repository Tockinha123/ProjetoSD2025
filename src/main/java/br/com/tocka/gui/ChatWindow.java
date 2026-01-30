package br.com.tocka.gui;

import br.com.tocka.controller.ChatController;
import br.com.tocka.rabbitmq.FileTransferManager;
import br.com.tocka.rabbitmq.Sender;
import br.com.tocka.rabbitmq.Receiver;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.Arrays;

public class ChatWindow {

    public static void showChatWindow(MultiWindowTextGUI gui, TerminalSize terminalSize, String username, Connection rabbitConnection, String rabbitHost, int managementPort, String rabbitUser, String rabbitPass) {
        int totalWidth = terminalSize.getColumns();
        int notificationsWidth = totalWidth * 30 / 100;

        BasicWindow window = new BasicWindow("MSN - 2025");
        window.setHints(Arrays.asList(Window.Hint.EXPANDED));

        Panel mainPanel = new Panel(new BorderLayout());

        // TOP PANEL
        Panel topPanel = new Panel(new BorderLayout());
        topPanel.addComponent(
                new Label("Que bom ver você de novo " + username + "!"),
                BorderLayout.Location.CENTER
        );
        mainPanel.addComponent(
                topPanel.withBorder(Borders.singleLine()),
                BorderLayout.Location.TOP
        );

        // CENTER PANEL
        Panel centerPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));

        TextBox messagesBox = new TextBox(
                new TerminalSize(1, 1),
                "",
                TextBox.Style.MULTI_LINE
        );
        messagesBox.setReadOnly(true);
        messagesBox.setLayoutData(LinearLayout.createLayoutData(
                LinearLayout.Alignment.Fill,
                LinearLayout.GrowPolicy.CanGrow
        ));
        centerPanel.addComponent(
                messagesBox.withBorder(Borders.singleLine("MENSAGENS"))
        );

        TextBox notificationsBox = new TextBox(
                new TerminalSize(1, 1),
                "",
                TextBox.Style.MULTI_LINE
        );
        notificationsBox.setReadOnly(true);
        notificationsBox.setPreferredSize(new TerminalSize(notificationsWidth, 0));
        notificationsBox.setLayoutData(LinearLayout.createLayoutData(
                LinearLayout.Alignment.Fill
        ));
        centerPanel.addComponent(
                notificationsBox.withBorder(Borders.singleLine("NOTIFICAÇÕES"))
        );

        mainPanel.addComponent(centerPanel, BorderLayout.Location.CENTER);

        Sender messageSender = null;
        Receiver messageReceiver = null;
        FileTransferManager fileTransferManager = null;

        try {
            messageSender = new Sender(rabbitConnection, rabbitHost, managementPort, rabbitUser, rabbitPass);

            ChatController controller = new ChatController(username, messagesBox, notificationsBox, messageSender);

            messageReceiver = new Receiver(rabbitConnection, username, (senderName, content, timestamp) -> {
                gui.getGUIThread().invokeLater(() -> {
                    controller.receiveMessage(senderName, content, timestamp);
                });
            });

            fileTransferManager = new FileTransferManager(rabbitConnection, username, new FileTransferManager.FileCallback() {
                @Override
                public void onFileReceived(String sender, String fileName, java.time.LocalDateTime timestamp) {
                    gui.getGUIThread().invokeLater(() -> {
                        controller.receiveFile(sender, fileName, timestamp);
                    });
                }

                @Override
                public void onFileSent(String receiver, String fileName) {
                    gui.getGUIThread().invokeLater(() -> {
                        controller.fileSent(receiver, fileName);
                    });
                }
            });

            controller.setFileTransferManager(fileTransferManager);
            controller.setMessageReceiver(messageReceiver);
            messageReceiver.setFileCallback((sender, fileName, timestamp) -> {
                gui.getGUIThread().invokeLater(() -> {
                    controller.receiveFile(sender, fileName, timestamp);
                });
            });

            Panel bottomPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));

            Label promptLabel = new Label(controller.getCurrentPrompt());
            bottomPanel.addComponent(promptLabel);

            TextBox inputBox = new TextBox(new TerminalSize(50, 1));
            inputBox.setLayoutData(LinearLayout.createLayoutData(
                    LinearLayout.Alignment.Fill,
                    LinearLayout.GrowPolicy.CanGrow
            ));
            bottomPanel.addComponent(inputBox);

            Button sendButton = new Button("ENVIAR", () -> {
                String message = inputBox.getText();

                controller.processInput(message);
                promptLabel.setText(controller.getCurrentPrompt());
                inputBox.setText("");
                inputBox.takeFocus();
            });
            bottomPanel.addComponent(sendButton);

            mainPanel.addComponent(
                    bottomPanel.withBorder(Borders.singleLine("Digite Aqui")),
                    BorderLayout.Location.BOTTOM
            );

            window.setComponent(mainPanel);

            gui.addWindowAndWait(window);

            try {
                if (messageSender != null) {
                    messageSender.close();
                }
                if (messageReceiver != null) {
                    messageReceiver.close();
                }
                if (fileTransferManager != null) {
                    fileTransferManager.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("Erro ao inicializar RabbitMQ: " + e.getMessage());
            e.printStackTrace();
        }
    }
}