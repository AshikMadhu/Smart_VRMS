package in.smartvrms.server.domain;

import in.smartvrms.application.port.AuditPort;
import in.smartvrms.application.port.CustomerPort;
import in.smartvrms.contracts.CustomerRecord;
import in.smartvrms.contracts.CustomerVisitRecord;
import in.smartvrms.contracts.StaffSession;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JdbcCustomerService implements CustomerPort {
    private final DataSource dataSource;
    private final AuditPort auditPort;

    public JdbcCustomerService(DataSource dataSource, AuditPort auditPort) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditPort = auditPort;
    }

    @Override
    public CustomerRecord createCustomerWithVisit(StaffSession session, String fullName, String mobile, String email, String address, int ageAtVisit, String bookingContext) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(mobile, "mobile");
        Objects.requireNonNull(address, "address");

        // Enforce FR-CUST-002: Customer creation only inside booking workflow
        if (bookingContext == null || bookingContext.isBlank()) {
            throw new IllegalArgumentException("Customer creation is strictly forbidden outside an active booking workflow (FR-CUST-002)");
        }
        if (ageAtVisit < 18 || ageAtVisit > 120) {
            throw new IllegalArgumentException("Age at visit must be between 18 and 120 (got " + ageAtVisit + ")");
        }

        String custSql = """
            INSERT INTO vrms_private.customers (company_id, full_name, mobile_number, email, address)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, created_at, version
            """;

        String visitSql = """
            INSERT INTO vrms_private.customer_visits (company_id, customer_id, age_at_visit, purpose)
            VALUES (?, ?, ?, ?)
            RETURNING id, visit_date
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                CustomerRecord customer;
                try (PreparedStatement s = c.prepareStatement(custSql)) {
                    s.setObject(1, session.companyId());
                    s.setString(2, fullName.strip());
                    s.setString(3, mobile.strip());
                    s.setString(4, email != null && !email.isBlank() ? email.strip() : null);
                    s.setString(5, address.strip());
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to insert customer");
                        UUID custId = r.getObject(1, UUID.class);
                        Instant createdAt = r.getTimestamp(2).toInstant();
                        long ver = r.getLong(3);
                        customer = new CustomerRecord(custId, session.companyId(), fullName.strip(), mobile.strip(), email, address.strip(), createdAt, ver);
                    }
                }
                try (PreparedStatement vs = c.prepareStatement(visitSql)) {
                    vs.setObject(1, session.companyId());
                    vs.setObject(2, customer.id());
                    vs.setInt(3, ageAtVisit);
                    vs.setString(4, "Booking context: " + bookingContext.strip());
                    vs.executeUpdate();
                }
                c.commit();
                if (auditPort != null) {
                    auditPort.recordEvent(session, "CUSTOMER_REGISTERED", "CUSTOMER", customer.id().toString(), "{\"booking\":\"" + bookingContext + "\"}");
                }
                return customer;
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to create customer and visit", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error creating customer", e);
        }
    }

    @Override
    public List<CustomerRecord> searchCustomers(StaffSession session, String query) {
        Objects.requireNonNull(session, "session");
        String sql = """
            SELECT id, company_id, full_name, mobile_number, email, address, created_at, version
            FROM vrms_private.customers
            WHERE company_id = ?
              AND (? IS NULL OR full_name ILIKE '%' || ? || '%' OR mobile_number LIKE '%' || ? || '%')
            ORDER BY full_name ASC
            LIMIT 50
            """;

        List<CustomerRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setString(2, query != null ? query.strip() : null);
                    s.setString(3, query != null ? query.strip() : null);
                    s.setString(4, query != null ? query.strip() : null);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            list.add(new CustomerRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getString("full_name"),
                                r.getString("mobile_number"),
                                r.getString("email"),
                                r.getString("address"),
                                r.getTimestamp("created_at").toInstant(),
                                r.getLong("version")
                            ));
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to search customers", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error searching customers", e);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public CustomerVisitRecord recordRepeatVisit(StaffSession session, UUID customerId, int ageAtVisit, String bookingContext) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(customerId, "customerId");
        if (ageAtVisit < 18 || ageAtVisit > 120) {
            throw new IllegalArgumentException("Age at visit must be between 18 and 120 (got " + ageAtVisit + ")");
        }

        String sql = """
            INSERT INTO vrms_private.customer_visits (company_id, customer_id, age_at_visit, purpose)
            VALUES (?, ?, ?, ?)
            RETURNING id, visit_date
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, customerId);
                    s.setInt(3, ageAtVisit);
                    s.setString(4, bookingContext);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to record visit");
                        UUID visitId = r.getObject(1, UUID.class);
                        Instant visitDate = r.getTimestamp(2).toInstant();
                        c.commit();
                        return new CustomerVisitRecord(visitId, session.companyId(), customerId, ageAtVisit, visitDate, bookingContext);
                    }
                }
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to record repeat visit", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error recording repeat visit", e);
        }
    }

    @Override
    public List<CustomerVisitRecord> getVisitHistory(StaffSession session, UUID customerId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(customerId, "customerId");

        String sql = """
            SELECT id, company_id, customer_id, age_at_visit, visit_date, purpose
            FROM vrms_private.customer_visits
            WHERE company_id = ? AND customer_id = ?
            ORDER BY visit_date DESC
            """;

        List<CustomerVisitRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, customerId);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            list.add(new CustomerVisitRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("customer_id", UUID.class),
                                r.getInt("age_at_visit"),
                                r.getTimestamp("visit_date").toInstant(),
                                r.getString("purpose")
                            ));
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to get visit history", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error getting visit history", e);
        }
        return Collections.unmodifiableList(list);
    }

    private static void setContext(Connection c, StaffSession session) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT set_config('vrms.user_id', ?, true), set_config('vrms.company_id', ?, true), set_config('vrms.role', ?, true)")) {
            s.setString(1, session.userId().toString());
            s.setString(2, session.companyId().toString());
            s.setString(3, session.role());
            s.execute();
        }
    }
}
