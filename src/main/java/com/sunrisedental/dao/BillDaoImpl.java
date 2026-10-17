package com.sunrisedental.dao;

import com.sunrisedental.model.Bill;
import com.sunrisedental.model.BillingType;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.PaymentStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** JDBC implementation of {@link BillDao}. */
public final class BillDaoImpl implements BillDao {

    private static final String SELECT = "SELECT bill_id, appointment_id, treatment_price, consultation_fee, "
            + "billing_type, subtotal, discount_percent, discount_amount, tax_percent, tax_amount, total_amount, "
            + "payment_status, payment_method, paid_at, issued_by, created_at, updated_at FROM bills";

    private static final String INSERT = "INSERT INTO bills (appointment_id, treatment_price, consultation_fee, "
            + "billing_type, subtotal, discount_percent, discount_amount, tax_percent, tax_amount, total_amount, "
            + "issued_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @Override
    public Bill insert(Connection connection, Bill bill) {
        long id = JdbcHelper.insert(connection, INSERT, ps -> {
            ps.setLong(1, bill.getAppointmentId());
            ps.setBigDecimal(2, bill.getTreatmentPrice());
            ps.setBigDecimal(3, bill.getConsultationFee());
            ps.setString(4, bill.getBillingType().name());
            ps.setBigDecimal(5, bill.getSubtotal());
            ps.setBigDecimal(6, bill.getDiscountPercent());
            ps.setBigDecimal(7, bill.getDiscountAmount());
            ps.setBigDecimal(8, bill.getTaxPercent());
            ps.setBigDecimal(9, bill.getTaxAmount());
            ps.setBigDecimal(10, bill.getTotalAmount());
            JdbcHelper.setNullableLong(ps, 11, bill.getIssuedBy());
        });
        return findById(connection, id).orElseThrow(() -> new DataAccessException("Inserted bill not found: " + id));
    }

    @Override
    public Optional<Bill> findById(Connection connection, long id) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE bill_id = ?",
                ps -> ps.setLong(1, id), BillDaoImpl::map);
    }

    @Override
    public Optional<Bill> findByAppointmentId(Connection connection, long appointmentId) {
        return JdbcHelper.queryOne(connection, SELECT + " WHERE appointment_id = ?",
                ps -> ps.setLong(1, appointmentId), BillDaoImpl::map);
    }

    @Override
    public boolean markPaid(Connection connection, long id, PaymentMethod method) {
        String sql = "UPDATE bills SET payment_status = 'PAID', payment_method = ?, paid_at = NOW() "
                + "WHERE bill_id = ? AND payment_status = 'UNPAID'";
        return JdbcHelper.update(connection, sql, ps -> {
            ps.setString(1, method.name());
            ps.setLong(2, id);
        }) > 0;
    }

    @Override
    public boolean voidBill(Connection connection, long id) {
        return JdbcHelper.update(connection,
                "UPDATE bills SET payment_status = 'VOID' WHERE bill_id = ? AND payment_status = 'UNPAID'",
                ps -> ps.setLong(1, id)) > 0;
    }

    private static Bill map(ResultSet rs) throws SQLException {
        Bill b = new Bill();
        b.setId(rs.getLong("bill_id"));
        b.setAppointmentId(rs.getLong("appointment_id"));
        b.setTreatmentPrice(rs.getBigDecimal("treatment_price"));
        b.setConsultationFee(rs.getBigDecimal("consultation_fee"));
        b.setBillingType(BillingType.valueOf(rs.getString("billing_type")));
        b.setSubtotal(rs.getBigDecimal("subtotal"));
        b.setDiscountPercent(rs.getBigDecimal("discount_percent"));
        b.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        b.setTaxPercent(rs.getBigDecimal("tax_percent"));
        b.setTaxAmount(rs.getBigDecimal("tax_amount"));
        b.setTotalAmount(rs.getBigDecimal("total_amount"));
        b.setPaymentStatus(PaymentStatus.valueOf(rs.getString("payment_status")));
        String method = rs.getString("payment_method");
        b.setPaymentMethod(method == null ? null : PaymentMethod.valueOf(method));
        b.setPaidAt(rs.getObject("paid_at", LocalDateTime.class));
        long issuedBy = rs.getLong("issued_by");
        b.setIssuedBy(rs.wasNull() ? null : issuedBy);
        b.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        b.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return b;
    }
}
