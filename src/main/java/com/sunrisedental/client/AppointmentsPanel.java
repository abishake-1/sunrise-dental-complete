package com.sunrisedental.client;

import com.sunrisedental.api.Json;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Day view of appointments with booking, status changes, cancelling and billing. */
final class AppointmentsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"ID", "Number", "Date", "Time", "Patient ID", "Dentist", "Treatment", "Status"};

    private final transient ApiClient api;
    private final JTextField date = new JTextField(LocalDate.now().toString(), 10);
    private final JComboBox<Option> dentistFilter = new JComboBox<>();
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private transient List<Map<String, Object>> dentists = new ArrayList<>();
    private transient List<Map<String, Object>> treatments = new ArrayList<>();

    /** A combo-box entry showing a label for an id. */
    record Option(Long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    AppointmentsPanel(ApiClient api) {
        super(new BorderLayout(8, 8));
        this.api = api;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.add(new JLabel("Date (yyyy-mm-dd)"));
        filters.add(date);
        filters.add(new JLabel("Dentist"));
        filters.add(dentistFilter);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        filters.add(refresh);

        JPanel actions = new JPanel(new GridLayout(1, 0, 6, 6));
        actions.add(button("Book appointment...", this::book));
        actions.add(button("Confirm", () -> setStatus("CONFIRMED")));
        actions.add(button("Mark completed", () -> setStatus("COMPLETED")));
        actions.add(button("Mark no-show", () -> setStatus("NO_SHOW")));
        actions.add(button("Cancel appointment", this::cancel));
        actions.add(button("Generate bill...", this::bill));

        add(filters, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        loadCatalogs();
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void loadCatalogs() {
        Async.run(this, () -> {
            List<Map<String, Object>> d = ApiClient.items(api.getObject("/api/dentists", null));
            List<Map<String, Object>> t = ApiClient.items(api.getObject("/api/treatments", null));
            return List.of(d, t);
        }, result -> {
            dentists = result.get(0);
            treatments = result.get(1);
            DefaultComboBoxModel<Option> m = new DefaultComboBoxModel<>();
            m.addElement(new Option(null, "All dentists"));
            for (Map<String, Object> d : dentists) {
                m.addElement(new Option(asLong(d.get("id")), String.valueOf(d.get("fullName"))));
            }
            dentistFilter.setModel(m);
            refresh();
        });
    }

    private void refresh() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("date", date.getText());
        Option selected = (Option) dentistFilter.getSelectedItem();
        if (selected != null && selected.id() != null) {
            query.put("dentistId", String.valueOf(selected.id()));
        }
        Async.run(this, () -> ApiClient.items(api.getObject("/api/appointments", query)), rows -> {
            model.setRowCount(0);
            for (Map<String, Object> a : rows) {
                model.addRow(new Object[] {a.get("id"), a.get("appointmentNumber"), a.get("date"), a.get("time"),
                    a.get("patientId"), nameOf(dentists, a.get("dentistId"), "fullName"),
                    nameOf(treatments, a.get("treatmentId"), "name"), a.get("status")});
            }
        });
    }

    private static String nameOf(List<Map<String, Object>> list, Object id, String field) {
        for (Map<String, Object> m : list) {
            if (String.valueOf(m.get("id")).equals(String.valueOf(id))) {
                return String.valueOf(m.get(field));
            }
        }
        return String.valueOf(id);
    }

    static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value));
    }

    private Long selectedId() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an appointment first.");
            return null;
        }
        return asLong(model.getValueAt(table.convertRowIndexToModel(row), 0));
    }

    private void setStatus(String status) {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        Async.run(this, () -> api.put("/api/appointments/" + id, Json.object("status", status)), r -> refresh());
    }

    private void cancel() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Cancel this appointment?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        Async.run(this, () -> api.delete("/api/appointments/" + id), r -> refresh());
    }

    private void bill() {
        Long id = selectedId();
        if (id == null) {
            return;
        }
        JComboBox<String> type = new JComboBox<>(new String[] {"STANDARD", "SENIOR", "INSURANCE"});
        JTextField coverage = new JTextField("", 6);
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Billing type"));
        form.add(type);
        form.add(new JLabel("Insurance cover % (optional)"));
        form.add(coverage);
        if (JOptionPane.showConfirmDialog(this, form, "Generate bill (appointment must be COMPLETED)",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        Map<String, Object> body = Json.object("billingType", type.getSelectedItem());
        if (!coverage.getText().isBlank()) {
            body.put("insuranceCoveragePercent", new java.math.BigDecimal(coverage.getText().trim()));
        }
        Async.run(this, () -> api.post("/api/appointments/" + id + "/bill", body),
                r -> ReceiptDialog.show(this, api, asLong(r.get("billId"))));
    }

    private void book() {
        JTextField patientSearch = new JTextField(12);
        JComboBox<Option> patient = new JComboBox<>();
        JButton find = new JButton("Find");
        find.addActionListener(e -> Async.run(this, () -> ApiClient.items(api.getObject("/api/patients",
                Map.of("search", patientSearch.getText()))), rows -> {
            DefaultComboBoxModel<Option> m = new DefaultComboBoxModel<>();
            for (Map<String, Object> p : rows) {
                m.addElement(new Option(asLong(p.get("id")),
                        p.get("firstName") + " " + p.get("lastName") + " (" + p.get("phone") + ")"));
            }
            patient.setModel(m);
        }));
        JComboBox<Option> dentist = new JComboBox<>();
        for (Map<String, Object> d : dentists) {
            dentist.addItem(new Option(asLong(d.get("id")), String.valueOf(d.get("fullName"))));
        }
        JComboBox<Option> treatment = new JComboBox<>();
        for (Map<String, Object> t : treatments) {
            treatment.addItem(new Option(asLong(t.get("id")),
                    t.get("name") + " - " + t.get("durationMinutes") + " min"));
        }
        JTextField day = new JTextField(date.getText(), 10);
        JTextField time = new JTextField("09:00", 6);
        JTextField notes = new JTextField(20);

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Search patient (name/phone)"));
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.add(patientSearch, BorderLayout.CENTER);
        searchRow.add(find, BorderLayout.EAST);
        form.add(searchRow);
        form.add(new JLabel("Patient"));
        form.add(patient);
        form.add(new JLabel("Dentist"));
        form.add(dentist);
        form.add(new JLabel("Treatment"));
        form.add(treatment);
        form.add(new JLabel("Date (yyyy-mm-dd)"));
        form.add(day);
        form.add(new JLabel("Time (HH:mm, 15-min steps)"));
        form.add(time);
        form.add(new JLabel("Notes"));
        form.add(notes);

        if (JOptionPane.showConfirmDialog(this, form, "Book appointment",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        Option p = (Option) patient.getSelectedItem();
        Option d = (Option) dentist.getSelectedItem();
        Option t = (Option) treatment.getSelectedItem();
        if (p == null || d == null || t == null) {
            JOptionPane.showMessageDialog(this, "Find and select a patient, dentist and treatment first.");
            return;
        }
        Map<String, Object> body = Json.object("patientId", p.id(), "dentistId", d.id(), "treatmentId", t.id(),
                "date", day.getText().trim(), "time", time.getText().trim());
        if (!notes.getText().isBlank()) {
            body.put("notes", notes.getText().trim());
        }
        Async.run(this, () -> api.post("/api/appointments", body), r -> {
            JOptionPane.showMessageDialog(this, "Booked: " + r.get("appointmentNumber"));
            date.setText(day.getText().trim());
            refresh();
        });
    }
}
