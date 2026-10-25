package in.smartvrms.server.domain;

import in.smartvrms.application.port.AuditPort;
import in.smartvrms.application.port.FleetPort;
import in.smartvrms.contracts.AssetRestrictionRecord;
import in.smartvrms.contracts.StaffSession;
import in.smartvrms.contracts.VehicleDocumentRecord;
import in.smartvrms.contracts.VehicleRecord;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class JdbcFleetService implements FleetPort {
    private final DataSource dataSource;
    private final AuditPort auditPort;

    public JdbcFleetService(DataSource dataSource, AuditPort auditPort) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditPort = auditPort;
    }

    @Override
    public VehicleRecord registerVehicle(StaffSession session, UUID categoryId, String plate, String manufacturer, String model, int capacity, long dailyRatePaise, long hourlyRatePaise, UUID photoFileId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(manufacturer, "manufacturer");
        Objects.requireNonNull(model, "model");

        if (plate.isBlank() || plate.length() < 4) throw new IllegalArgumentException("Invalid registration plate");
        if (capacity <= 0) throw new IllegalArgumentException("Seating capacity must be positive");
        if (dailyRatePaise <= 0) throw new IllegalArgumentException("Daily rate must be positive");

        String sql = """
            INSERT INTO vrms_private.vehicles
                (company_id, category_id, registration_plate, manufacturer, model, seating_capacity, daily_rate_paise, hourly_rate_paise, operational_status, photo_file_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?)
            RETURNING id, created_at, version
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                VehicleRecord record;
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, categoryId);
                    s.setString(3, plate.strip().toUpperCase(Locale.ROOT));
                    s.setString(4, manufacturer.strip());
                    s.setString(5, model.strip());
                    s.setInt(6, capacity);
                    s.setLong(7, dailyRatePaise);
                    s.setLong(8, hourlyRatePaise);
                    s.setObject(9, photoFileId);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to register vehicle");
                        record = new VehicleRecord(
                            r.getObject(1, UUID.class),
                            session.companyId(),
                            categoryId,
                            "Category",
                            plate.strip().toUpperCase(Locale.ROOT),
                            manufacturer.strip(),
                            model.strip(),
                            capacity,
                            dailyRatePaise,
                            hourlyRatePaise,
                            "AVAILABLE",
                            photoFileId,
                            r.getLong(3)
                        );
                    }
                }
                c.commit();
                if (auditPort != null) {
                    auditPort.recordEvent(session, "VEHICLE_REGISTERED", "VEHICLE", record.id().toString(), "{\"plate\":\"" + plate + "\"}");
                }
                return record;
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to register vehicle", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error registering vehicle", e);
        }
    }

    @Override
    public List<VehicleRecord> listVehicles(StaffSession session, String categoryCode, String status) {
        Objects.requireNonNull(session, "session");
        String sql = """
            SELECT v.id, v.company_id, v.category_id, c.name as category_name, v.registration_plate, v.manufacturer, v.model,
                   v.seating_capacity, v.daily_rate_paise, v.hourly_rate_paise, v.operational_status, v.photo_file_id, v.version
            FROM vrms_private.vehicles v
            JOIN vrms_private.vehicle_categories c ON c.id = v.category_id
            WHERE v.company_id = ?
              AND (? IS NULL OR c.code = ?)
              AND (? IS NULL OR v.operational_status = ?)
            ORDER BY v.manufacturer ASC, v.model ASC
            """;

        List<VehicleRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setString(2, categoryCode);
                    s.setString(3, categoryCode);
                    s.setString(4, status);
                    s.setString(5, status);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            list.add(new VehicleRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("category_id", UUID.class),
                                r.getString("category_name"),
                                r.getString("registration_plate"),
                                r.getString("manufacturer"),
                                r.getString("model"),
                                r.getInt("seating_capacity"),
                                r.getLong("daily_rate_paise"),
                                r.getLong("hourly_rate_paise"),
                                r.getString("operational_status"),
                                r.getObject("photo_file_id", UUID.class),
                                r.getLong("version")
                            ));
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to list vehicles", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error listing vehicles", e);
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public Optional<VehicleRecord> getVehicle(StaffSession session, UUID vehicleId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(vehicleId, "vehicleId");

        String sql = """
            SELECT v.id, v.company_id, v.category_id, c.name as category_name, v.registration_plate, v.manufacturer, v.model,
                   v.seating_capacity, v.daily_rate_paise, v.hourly_rate_paise, v.operational_status, v.photo_file_id, v.version
            FROM vrms_private.vehicles v
            JOIN vrms_private.vehicle_categories c ON c.id = v.category_id
            WHERE v.company_id = ? AND v.id = ?
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, vehicleId);
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) {
                            VehicleRecord record = new VehicleRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("category_id", UUID.class),
                                r.getString("category_name"),
                                r.getString("registration_plate"),
                                r.getString("manufacturer"),
                                r.getString("model"),
                                r.getInt("seating_capacity"),
                                r.getLong("daily_rate_paise"),
                                r.getLong("hourly_rate_paise"),
                                r.getString("operational_status"),
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
                throw new RuntimeException("Failed to get vehicle", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error getting vehicle", e);
        }
    }

    @Override
    public AssetRestrictionRecord freezeAsset(StaffSession session, String assetType, UUID assetId, String reason, Instant startTime, Instant endTime) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(assetType, "assetType");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(startTime, "startTime");

        String sql = """
            INSERT INTO vrms_private.asset_restrictions
                (company_id, asset_type, asset_id, restriction_type, start_time, end_time, reason, active)
            VALUES (?, ?, ?, 'FREEZE', ?, ?, ?, true)
            RETURNING id
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setString(2, assetType);
                    s.setObject(3, assetId);
                    s.setTimestamp(4, java.sql.Timestamp.from(startTime));
                    s.setTimestamp(5, endTime != null ? java.sql.Timestamp.from(endTime) : null);
                    s.setString(6, reason);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to freeze asset");
                        UUID id = r.getObject(1, UUID.class);
                        c.commit();
                        if (auditPort != null) {
                            auditPort.recordEvent(session, "ASSET_FROZEN", assetType, assetId.toString(), "{\"reason\":\"" + reason + "\"}");
                        }
                        return new AssetRestrictionRecord(id, session.companyId(), assetType, assetId, "FREEZE", startTime, endTime, reason, true);
                    }
                }
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to freeze asset", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error freezing asset", e);
        }
    }

    @Override
    public void resolveAssetRestriction(StaffSession session, UUID restrictionId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(restrictionId, "restrictionId");

        String sql = """
            UPDATE vrms_private.asset_restrictions
            SET active = false
            WHERE id = ? AND company_id = ?
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, restrictionId);
                    s.setObject(2, session.companyId());
                    s.executeUpdate();
                }
                c.commit();
                if (auditPort != null) {
                    auditPort.recordEvent(session, "RESTRICTION_RESOLVED", "RESTRICTION", restrictionId.toString(), "{}");
                }
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to resolve restriction", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error resolving restriction", e);
        }
    }

    @Override
    public VehicleDocumentRecord addVehicleDocument(StaffSession session, UUID vehicleId, String docType, UUID fileId, LocalDate issueDate, LocalDate expiryDate) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(docType, "docType");
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(issueDate, "issueDate");

        String sql = """
            INSERT INTO vrms_private.vehicle_documents
                (company_id, vehicle_id, doc_type, file_id, issue_date, expiry_date)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id, version
            """;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, vehicleId);
                    s.setString(3, docType);
                    s.setObject(4, fileId);
                    s.setDate(5, java.sql.Date.valueOf(issueDate));
                    s.setDate(6, expiryDate != null ? java.sql.Date.valueOf(expiryDate) : null);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Failed to add vehicle document");
                        UUID id = r.getObject(1, UUID.class);
                        int ver = r.getInt(2);
                        c.commit();
                        if (auditPort != null) {
                            auditPort.recordEvent(session, "DOCUMENT_ADDED", "VEHICLE_DOC", id.toString(), "{\"type\":\"" + docType + "\"}");
                        }
                        return new VehicleDocumentRecord(id, session.companyId(), vehicleId, docType, fileId, issueDate, expiryDate, ver);
                    }
                }
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to add vehicle document", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error adding vehicle document", e);
        }
    }

    @Override
    public List<VehicleDocumentRecord> getVehicleDocuments(StaffSession session, UUID vehicleId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(vehicleId, "vehicleId");

        String sql = """
            SELECT id, company_id, vehicle_id, doc_type, file_id, issue_date, expiry_date, version
            FROM vrms_private.vehicle_documents
            WHERE company_id = ? AND vehicle_id = ?
            ORDER BY doc_type ASC, version DESC
            """;

        List<VehicleDocumentRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                setContext(c, session);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, session.companyId());
                    s.setObject(2, vehicleId);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            list.add(new VehicleDocumentRecord(
                                r.getObject("id", UUID.class),
                                r.getObject("company_id", UUID.class),
                                r.getObject("vehicle_id", UUID.class),
                                r.getString("doc_type"),
                                r.getObject("file_id", UUID.class),
                                r.getDate("issue_date").toLocalDate(),
                                r.getDate("expiry_date") != null ? r.getDate("expiry_date").toLocalDate() : null,
                                r.getInt("version")
                            ));
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Failed to get vehicle documents", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error getting vehicle documents", e);
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
