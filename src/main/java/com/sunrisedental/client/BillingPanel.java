package com.sunrisedental.client;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Opens the receipt of an existing bill. Bills are created from the Appointments tab. */
final class BillingPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    BillingPanel(ApiClient api) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextField billId = new JTextField(8);
        JButton open = new JButton("Open receipt");
        open.addActionListener(e -> {
            try {
                long id = Long.parseLong(billId.getText().trim());
                ReceiptDialog.show(this, api, id);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter the numeric bill id (for example 1).");
            }
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel("Bill id"));
        row.add(billId);
        row.add(open);
        add(row, BorderLayout.NORTH);
        add(new JLabel("<html>To create a bill, mark the appointment as completed and use "
                + "<b>Generate bill...</b> on the Appointments tab.<br>"
                + "Printable .txt/.html copies are also saved in the server's receipts folder.</html>"),
                BorderLayout.CENTER);
    }
}
