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

        Config config = Config.fromArgs(args);

        if (config.hasFlag("help")) {
            System.out.println("ProjetoSD2025 — Chat TUI via RabbitMQ\n");
            System.out.println("Uso:");
            System.out.println("  java -jar target/ProjetoSD2025-1.0-SNAPSHOT-jar-with-dependencies.jar [opções]\n");
            System.out.println("Opções (args têm prioridade sobre env vars):");
            System.out.println("  --rabbit-host <host>   (env: RABBIT_HOST, default: localhost)");
            System.out.println("  --rabbit-port <port>   (env: RABBIT_PORT, default: 5672)");
            System.out.println("  --rabbit-user <user>   (env: RABBIT_USER, default: guest)");
            System.out.println("  --rabbit-pass <pass>   (env: RABBIT_PASS, default: guest)");
            System.out.println("  --help, -h             Mostra esta ajuda e sai\n");
            return;
        }

        try {
            terminal = new DefaultTerminalFactory().createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();

            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);

            String username = UsernameModal.showDialog(gui);

                String rabbitHost = config.get("rabbit-host", "RABBIT_HOST", "localhost");
                int rabbitPort = config.getInt("rabbit-port", "RABBIT_PORT", 5672);
                String rabbitUser = config.get("rabbit-user", "RABBIT_USER", "guest");
                String rabbitPass = config.get("rabbit-pass", "RABBIT_PASS", "guest");

            connectionManager = new ConnectionManager(
                    rabbitHost,
                    rabbitPort,
                    rabbitUser,
                    rabbitPass
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