package in.smartvrms.server.domain;

import in.smartvrms.application.port.AuditPort;
import in.smartvrms.application.port.PersonnelPort;
import in.smartvrms.contracts.AttendanceRecord;
import in.smartvrms.contracts.CompensationRecord;
import in.smartvrms.contracts.EmployeeRecord;
import in.smartvrms.contracts.StaffSession;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class JdbcPersonnelService implements PersonnelPort {
    private final DataSource dataSource;
    private final AuditPort auditPort;

    public JdbcPersonnelService(DataSource dataSource, AuditPort auditPort) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditPort = auditPort;
    }

    @Override
    public EmployeeRecord registerEmployee(StaffSession session, String fullName, String contact, String email, String address, LocalDate joiningDate, boolean isDriver, UUID photoFileId) {
        Objects.requireNonNull(session, "session");
        if (!session.isAdministrator()) {
            throw new SecurityException("Forbidden: Only administrators can register employees");
        }
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name is required");
        if (contact == null || contact.isBlank() || contact.length() < 7) throw new IllegalArgumentException("Valid contact number is required");
        if (address == null || address.isBlank()) throw new IllegalArgumentException("Address is required");
        if (joiningDate == null) throw new IllegalArgumentException("Joining date is required");

        String sql = """
            INSERT INTO vrms_private.employees
                (company_id, full_name, contact_number, email, address, joining_date, employment_status, is_driver, photo_file_id)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            RETURNING id, created_at, version
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_PERSONNEL);
                EmployeeRecord record;
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setString(2, fullName.strip());
                    s.setString(3, contact.strip());
                    s.setString(4, email != null && !email.isBlank() ? email.strip() : null);
                    s.setString(5, address.strip());
                    s.setDate(6, java.sql.Date.valueOf(joiningDate));
                    s.setBoolean(7, isDriver);
                    s.setObject(8, photoFileId);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to register employee");
                        record = new EmployeeRecord(
                            r.getObject(1, UUID.class),
                            session.companyId(),
                            null,
                            fullName.strip(),
                            contact.strip(),
                            email != null ? email.strip() : null,
                            address.strip(),
                            joiningDate,
                            "ACTIVE",
                            isDriver,
                            photoFileId,
                            r.getLong(3)
                        );
                    }
                }
                in.smartvrms.server.security.JdbcBusinessAudit.append(c, session, "EMPLOYEE_REGISTERED", "EMPLOYEE", record.id().toString(), in.smartvrms.server.security.JdbcBusinessAudit.detail("name", fullName));
                c.commit();
                return record;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to register employee", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error registering employee", e);
        }
    }

    @Override
    public EmployeeRecord updateEmployee(StaffSession session, UUID employeeId, String fullName, String contact, String email, String address, String employmentStatus) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(employeeId, "employeeId");
        if (!session.isAdministrator()) {
            throw new SecurityException("Forbidden: Only administrators can update employee records");
        }

        String sql = """
            UPDATE vrms_private.employees
            SET full_name = ?, contact_number = ?, email = ?, address = ?, employment_status = ?, version = version + 1
            WHERE id = ? AND company_id = ?
            RETURNING id, company_id, auth_user_id, full_name, contact_number, email, address, joining_date, employment_status, is_driver, photo_file_id, version
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_PERSONNEL);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setString(1, fullName.strip());
                    s.setString(2, contact.strip());
                    s.setString(3, email != null ? email.strip() : null);
                    s.setString(4, address.strip());
                    s.setString(5, employmentStatus);
                    s.setObject(6, employeeId);
                    s.setObject(7, session.companyId());
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Employee not found: " + employeeId);
                        EmployeeRecord record = new EmployeeRecord(
                            r.getObject("id", UUID.class),
                            r.getObject("company_id", UUID.class),
                            r.getObject("auth_user_id", UUID.class),
                            r.getString("full_name"),
                            r.getString("contact_number"),
                            r.getString("email"),
                            r.getString("address"),
                            r.getDate("joining_date").toLocalDate(),
                            r.getString("employment_status"),
                            r.getBoolean("is_driver"),
                            r.getObject("photo_file_id", UUID.class),
                            r.getLong("version")
                        );
                        in.smartvrms.server.security.JdbcBusinessAudit.append(c,session,"EMPLOYEE_UPDATED","EMPLOYEE",record.id().toString(),"{}");
                        c.commit();
                        return record;
                    }
                }
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to update employee", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error updating employee", e);
        }
    }

    @Override
    public EmployeeRecord deactivateEmployee(StaffSession session, UUID employeeId, String reason) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(employeeId, "employeeId");
        if (!session.isAdministrator()) {
            throw new SecurityException("Forbidden: Only administrators can deactivate employees");
        }

        String sql = """
            UPDATE vrms_private.employees
            SET employment_status = 'DEACTIVATED', version = version + 1
            WHERE id = ? AND company_id = ?
            RETURNING id, company_id, auth_user_id, full_name, contact_number, email, address, joining_date, employment_status, is_driver, photo_file_id, version
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_PERSONNEL);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, employeeId);
                    s.setObject(2, session.companyId());
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Employee not found: " + employeeId);
                        EmployeeRecord record = new EmployeeRecord(
                            r.getObject("id", UUID.class),
                            r.getObject("company_id", UUID.class),
                            r.getObject("auth_user_id", UUID.class),
                            r.getString("full_name"),
                            r.getString("contact_number"),
                            r.getString("email"),
                            r.getString("address"),
                            r.getDate("joining_date").toLocalDate(),
                            r.getString("employment_status"),
                            r.getBoolean("is_driver"),
                            r.getObject("photo_file_id", UUID.class),
                            r.getLong("version")
                        );
                        in.smartvrms.server.security.JdbcBusinessAudit.append(c, session, "EMPLOYEE_DEACTIVATED", "EMPLOYEE", employeeId.toString(), in.smartvrms.server.security.JdbcBusinessAudit.detail("reason", reason));
                        c.commit();
                        return record;
                    }
                }
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to deactivate employee", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error deactivating employee", e);
        }
    }

    @Override
    public List<EmployeeRecord> listEmployees(StaffSession session, boolean activeOnly) {
        Objects.requireNonNull(session, "session");
        String sql = """
            SELECT id, company_id, auth_user_id, full_name, contact_number, email, address, joining_date, employment_status, is_driver, photo_file_id, version
            FROM vrms_private.employees
            WHERE company_id = ? AND (? = false OR employment_status = 'ACTIVE')
            ORDER BY full_name ASC
            """;

        List<EmployeeRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_PERSONNEL);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setBoolean(2, activeOnly);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            list.add(new EmployeeRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("auth_user_id", UUID.class),
                                r.getString("full_name"),
                                r.getString("contact_number"),
                                r.getString("email"),
                                r.getString("address"),
                                r.getDate("joining_date").toLocalDate(),
                                r.getString("employment_status"),
                                r.getBoolean("is_driver"),
                                r.getObject("photo_file_id", UUID.class),
                                r.getLong("version")
                            ));
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to list employees", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error listing employees", e);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public Optional<EmployeeRecord> findDuplicateEmployee(StaffSession session, String contactNumber, String email) {
        Objects.requireNonNull(session, "session");
        String sql = """
            SELECT id, company_id, auth_user_id, full_name, contact_number, email, address, joining_date, employment_status, is_driver, photo_file_id, version
            FROM vrms_private.employees
            WHERE company_id = ? AND (contact_number = ? OR (email IS NOT NULL AND email = ?))
            LIMIT 1
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_PERSONNEL);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setString(2, contactNumber != null ? contactNumber.strip() : "");
                    s.setString(3, email != null ? email.strip() : "");
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) {
                            EmployeeRecord record = new EmployeeRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("auth_user_id", UUID.class),
                                r.getString("full_name"),
                                r.getString("contact_number"),
                                r.getString("email"),
                                r.getString("address"),
                                r.getDate("joining_date").toLocalDate(),
                                r.getString("employment_status"),
                                r.getBoolean("is_driver"),
                                r.getObject("photo_file_id", UUID.class),
                                r.getLong("version")
                            );
                            c.commit();
                            return Optional.of(record);
                        }
                    }
                }
                c.commit();
                return Optional.empty();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to find duplicate employee", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error finding duplicate employee", e);
        }
    }

    @Override
    public AttendanceRecord recordAttendance(StaffSession session, UUID employeeId, LocalDate date, String status, String notes) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(status, "status");

        String sql = """
            INSERT INTO vrms_private.attendance_records
                (company_id, employee_id, record_date, status, notes, recorded_by)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (company_id, employee_id, record_date)
            DO UPDATE SET status = EXCLUDED.status, notes = EXCLUDED.notes
            RETURNING id
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_ATTENDANCE);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, employeeId);
                    s.setDate(3, java.sql.Date.valueOf(date));
                    s.setString(4, status);
                    s.setString(5, notes);
                    s.setObject(6, session.userId());
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to record attendance");
                        UUID id = r.getObject(1, UUID.class);
                        in.smartvrms.server.security.JdbcBusinessAudit.append(c,session,"ATTENDANCE_RECORDED","ATTENDANCE",id.toString(),"{}");
                        c.commit();
                        return new AttendanceRecord(id, session.companyId(), employeeId, date, status, notes, session.userId());
                    }
                }
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to record attendance", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error recording attendance", e);
        }
    }

    @Override
    public List<AttendanceRecord> getAttendanceHistory(StaffSession session, UUID employeeId, LocalDate from, LocalDate to) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(employeeId, "employeeId");

        String sql = """
            SELECT id, company_id, employee_id, record_date, status, notes, recorded_by
            FROM vrms_private.attendance_records
            WHERE company_id = ? AND employee_id = ? AND record_date BETWEEN ? AND ?
            ORDER BY record_date ASC
            """;

        List<AttendanceRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_ATTENDANCE);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, employeeId);
                    s.setDate(3, java.sql.Date.valueOf(from != null ? from : LocalDate.of(2000, 1, 1)));
                    s.setDate(4, java.sql.Date.valueOf(to != null ? to : LocalDate.of(2099, 12, 31)));
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            list.add(new AttendanceRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("employee_id", UUID.class),
                                r.getDate("record_date").toLocalDate(),
                                r.getString("status"),
                                r.getString("notes"),
                                r.getObject("recorded_by", UUID.class)
                            ));
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to get attendance history", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error getting attendance", e);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public CompensationRecord recordCompensation(StaffSession session, UUID employeeId, String arrangementType, long baseRatePaise, long tripAllowancePaise, LocalDate effectiveFrom) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(employeeId, "employeeId");
        if (!session.isAdministrator()) {
            throw new SecurityException("Forbidden: Only administrators can view or modify compensation records");
        }

        String sql = """
            INSERT INTO vrms_private.compensation_records
                (company_id, employee_id, arrangement_type, base_rate_paise, trip_allowance_paise, effective_from)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.VIEW_COMPENSATION);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, employeeId);
                    s.setString(3, arrangementType);
                    s.setLong(4, baseRatePaise);
                    s.setLong(5, tripAllowancePaise);
                    s.setDate(6, java.sql.Date.valueOf(effectiveFrom));
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to record compensation");
                        UUID id = r.getObject(1, UUID.class);
                        in.smartvrms.server.security.JdbcBusinessAudit.append(c,session,"COMPENSATION_RECORDED","COMPENSATION",id.toString(),"{}");
                        c.commit();
                        return new CompensationRecord(id, session.companyId(), employeeId, arrangementType, baseRatePaise, tripAllowancePaise, effectiveFrom);
                    }
                }
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to record compensation", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error recording compensation", e);
        }
    }

    @Override
    public Optional<CompensationRecord> getCompensation(StaffSession session, UUID employeeId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(employeeId, "employeeId");
        if (!session.isAdministrator()) {
            return Optional.empty(); // RLS / Security isolation for Employees (FR-ACC-003)
        }

        String sql = """
            SELECT id, company_id, employee_id, arrangement_type, base_rate_paise, trip_allowance_paise, effective_from
            FROM vrms_private.compensation_records
            WHERE company_id = ? AND employee_id = ?
            ORDER BY effective_from DESC
            LIMIT 1
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.VIEW_COMPENSATION);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, employeeId);
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) {
                            CompensationRecord record = new CompensationRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("employee_id", UUID.class),
                                r.getString("arrangement_type"),
                                r.getLong("base_rate_paise"),
                                r.getLong("trip_allowance_paise"),
                                r.getDate("effective_from").toLocalDate()
                            );
                            c.commit();
                            return Optional.of(record);
                        }
                    }
                }
                c.commit();
                return Optional.empty();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to get compensation", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error getting compensation", e);
        }
    }

}
