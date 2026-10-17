package com.sunrisedental.client;

import com.sunrisedental.api.Json;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Patient search and registration. */
final class PatientsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"ID", "First name", "Last name", "Date of birth", "Gender", "Phone", "Email"};

    private final transient ApiClient api;
    private final JTextField search = new JTextField(16);
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    PatientsPanel(ApiClient api) {
        super(new BorderLayout(8, 8));
        this.api = api;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Name or phone"));
        top.add(search);
        JButton find = new JButton("Search");
        find.addActionListener(e -> search());
        search.addActionListener(e -> search());
        JButton register = new JButton("Register patient...");
        register.addActionListener(e -> register());
        top.add(find);
        top.add(register);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
    }

    private void search() {
        String term = search.getText();
        Async.run(this, () -> ApiClient.items(api.getObject("/api/patients", Map.of("search", term))), rows -> {
            model.setRowCount(0);
            for (Map<String, Object> p : rows) {
                model.addRow(new Object[] {p.get("id"), p.get("firstName"), p.get("lastName"),
                    p.get("dateOfBirth"), p.get("gender"), p.get("phone"), p.get("email")});
            }
        });
    }

    private void register() {
        JTextField first = new JTextField(16);
        JTextField last = new JTextField(16);
        JTextField dob = new JTextField("1990-01-01", 10);
        JComboBox<String> gender = new JComboBox<>(new String[] {"FEMALE", "MALE", "OTHER"});
        JTextField phone = new JTextField("07", 12);
        JTextField email = new JTextField(20);
        JTextField address = new JTextField(24);
        JTextField notes = new JTextField(24);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("First name"));
        form.add(first);
        form.add(new JLabel("Last name"));
        form.add(last);
        form.add(new JLabel("Date of birth (yyyy-mm-dd)"));
        form.add(dob);
        form.add(new JLabel("Gender"));
        form.add(gender);
        form.add(new JLabel("Phone (07XXXXXXXX)"));
        form.add(phone);
        form.add(new JLabel("Email (optional)"));
        form.add(email);
        form.add(new JLabel("Address (optional)"));
        form.add(address);
        form.add(new JLabel("Medical notes (optional)"));
        form.add(notes);
        if (JOptionPane.showConfirmDialog(this, form, "Register patient",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        Map<String, Object> body = Json.object("firstName", first.getText().trim(), "lastName", last.getText().trim(),
                "dateOfBirth", dob.getText().trim(), "gender", gender.getSelectedItem(),
                "phone", phone.getText().trim());
        putIfPresent(body, "email", email);
        putIfPresent(body, "address", address);
        putIfPresent(body, "medicalNotes", notes);
        Async.run(this, () -> api.post("/api/patients", body), r -> {
            JOptionPane.showMessageDialog(this, "Patient registered with id " + r.get("id"));
            search.setText(last.getText().trim());
            search();
        });
    }

    private static void putIfPresent(Map<String, Object> body, String key, JTextField field) {
        if (!field.getText().isBlank()) {
            body.put(key, field.getText().trim());
        }
    }
}
