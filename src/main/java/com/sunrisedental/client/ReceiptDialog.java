package com.sunrisedental.client;

import com.sunrisedental.api.Json;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.print.PrinterException;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Shows the plain-text receipt of a bill, lets the cashier print it and record the payment. */
final class ReceiptDialog {

    private ReceiptDialog() {
    }

    /**
     * Loads the receipt and opens the dialog.
     *
     * @param parent parent component
     * @param api    the server client
     * @param billId bill to show
     */
    static void show(Component parent, ApiClient api, long billId) {
        Async.run(parent, () -> api.getRaw("/api/bills/" + billId, Map.of("format", "text")).text(),
                text -> open(parent, api, billId, text));
    }

    private static void open(Component parent, ApiClient api, long billId, String receiptText) {
        JTextArea area = new JTextArea(receiptText);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(520, 460));

        JComboBox<String> method = new JComboBox<>(new String[] {"CASH", "CARD", "ONLINE", "INSURANCE"});
        JButton pay = new JButton("Mark as paid");
        JButton print = new JButton("Print...");
        JButton close = new JButton("Close");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(method);
        buttons.add(pay);
        buttons.add(print);
        buttons.add(close);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Receipt - bill " + billId,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        close.addActionListener(e -> dialog.dispose());
        print.addActionListener(e -> {
            try {
                area.print();
            } catch (PrinterException ex) {
                JOptionPane.showMessageDialog(dialog, "Printing failed: " + ex.getMessage());
            }
        });
        pay.addActionListener(e -> Async.run(dialog, () -> {
            api.post("/api/bills/" + billId + "/pay", Json.object("paymentMethod", method.getSelectedItem()));
            return api.getRaw("/api/bills/" + billId, Map.of("format", "text")).text();
        }, text -> {
            area.setText(text);
            area.setCaretPosition(0);
            pay.setEnabled(false);
        }));
        dialog.setVisible(true);
    }
}
