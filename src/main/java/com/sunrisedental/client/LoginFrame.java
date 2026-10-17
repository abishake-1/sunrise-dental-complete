package com.sunrisedental.client;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Login window. On success it opens the {@link MainFrame}. */
final class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final transient ApiClient api;
    private final JTextField username = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final JLabel status = new JLabel(" ");
    private final JButton loginButton = new JButton("Log in");

    LoginFrame(ApiClient api) {
        super("Sunrise Dental - Log in");
        this.api = api;
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Sunrise Dental Clinic");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 20f));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        form.add(title, c);

        c.gridwidth = 1;
        c.gridy = 1;
        form.add(new JLabel("Username"), c);
        c.gridx = 1;
        form.add(username, c);
        c.gridx = 0;
        c.gridy = 2;
        form.add(new JLabel("Password"), c);
        c.gridx = 1;
        form.add(password, c);
        c.gridx = 1;
        c.gridy = 3;
        form.add(loginButton, c);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        form.add(status, c);
        c.gridy = 5;
        form.add(new JLabel("Server: " + api.baseUrl()), c);

        loginButton.addActionListener(e -> login());
        password.addActionListener(e -> login());
        getRootPane().setDefaultButton(loginButton);
        setContentPane(form);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void login() {
        String user = username.getText().trim();
        char[] pw = password.getPassword();
        if (user.isEmpty() || pw.length == 0) {
            status.setText("Enter username and password");
            return;
        }
        loginButton.setEnabled(false);
        status.setText("Signing in...");
        Async.run(this, () -> {
            try {
                api.login(user, pw);
                return Boolean.TRUE;
            } finally {
                Arrays.fill(pw, '\0');
            }
        }, ok -> {
            password.setText("");
            new MainFrame(api).setVisible(true);
            dispose();
        }, () -> {
            loginButton.setEnabled(true);
            status.setText(" ");
        });
    }
}
