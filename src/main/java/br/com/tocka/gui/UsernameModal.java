package br.com.tocka.gui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;


import java.util.Arrays;

public class UsernameModal {

    public static String showDialog(WindowBasedTextGUI gui) {

        BasicWindow modal = new BasicWindow("Bem-vindo ao MSN - 2025!");
        modal.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.MODAL));

        Panel mainPanel = new Panel(new BorderLayout());
        mainPanel.addComponent(new Label("Digite seu nome de usuário:"), BorderLayout.Location.TOP);

        TextBox usernameTextBox = new TextBox(new TerminalSize(30, 1), TextBox.Style.SINGLE_LINE);
        mainPanel.addComponent(usernameTextBox, BorderLayout.Location.CENTER);

        final String[] result = {null};

        Panel buttonPanel = new Panel(new BorderLayout());

        Button okButton = new Button("Entrar", () -> {
            String username = usernameTextBox.getText().trim();

            result[0] = username;
            modal.close();
        });

        buttonPanel.addComponent(okButton, BorderLayout.Location.CENTER);
        mainPanel.addComponent(buttonPanel, BorderLayout.Location.BOTTOM);

        modal.setComponent(mainPanel);

        // Mostra o dialog de forma modal (bloqueia até fechar)
        gui.addWindowAndWait(modal);

        return result[0];
    }
}