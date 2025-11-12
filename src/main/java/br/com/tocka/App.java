package br.com.tocka;

import br.com.tocka.gui.ChatWindow;
import br.com.tocka.gui.UsernameModal;
import br.com.tocka.rabbitmq.ConnectionManager;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class App {
    public static void main(String[] args) {
        Terminal terminal = null;
        Screen screen = null;
        ConnectionManager connectionManager = null;

        try {
            terminal = new DefaultTerminalFactory().createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();

            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

            String username = UsernameModal.showDialog(gui);

            connectionManager = new ConnectionManager(
                    "localhost",  // Host
                    5672,         // Porta
                    "guest",      // Username RabbitMQ
                    "guest"       // Password RabbitMQ
            );

            connectionManager.connect();

            TerminalSize terminalSize = screen.getTerminalSize();
            ChatWindow.showChatWindow(gui, terminalSize, username, connectionManager.getConnection());

            connectionManager.close();
            screen.stopScreen();

        } catch (IOException | TimeoutException e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connectionManager != null) connectionManager.close();
                if (screen != null) screen.stopScreen();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}