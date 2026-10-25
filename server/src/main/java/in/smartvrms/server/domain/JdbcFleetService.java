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
    public List<in.smartvrms.contracts.VehicleCategoryRecord> listCategories(StaffSession session) {
        List<in.smartvrms.contracts.VehicleCategoryRecord> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c,session,in.smartvrms.server.security.Capability.VIEW_FLEET);
                try (PreparedStatement query = c.prepareStatement("SELECT id,code,name FROM vrms_private.vehicle_categories WHERE company_id=? ORDER BY name")) {
                    query.setQueryTimeout(10); query.setObject(1,session.companyId());
                    try (ResultSet rows=query.executeQuery()) {
                        while(rows.next()) result.add(new in.smartvrms.contracts.VehicleCategoryRecord(rows.getObject(1,UUID.class),rows.getString(2),rows.getString(3)));
                    }
                }
                c.commit(); return List.copyOf(result);
            } catch (Exception ex) { c.rollback(); throw ex; }
        } catch (SQLException ex) { throw new IllegalStateException("Fleet categories are unavailable."); }
    }

    @Override
    public in.smartvrms.contracts.VehicleCategoryRecord createCategory(StaffSession session,String code,String name) {
        if(code==null || !code.matches("[A-Z][A-Z0-9_]{1,19}") || name==null || name.isBlank() || name.strip().length()>50)
            throw new IllegalArgumentException("Use a category code of 2–20 uppercase letters/digits and a name up to 50 characters.");
        try(Connection c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                in.smartvrms.server.security.JdbcStaffContext.require(c,session,in.smartvrms.server.security.Capability.MANAGE_VEHICLE_MASTER);
                in.smartvrms.contracts.VehicleCategoryRecord record;
                try(PreparedStatement query=c.prepareStatement("INSERT INTO vrms_private.vehicle_categories(company_id,code,name) VALUES (?,?,?) RETURNING id")) {
                    query.setQueryTimeout(10);query.setObject(1,session.companyId());query.setString(2,code);query.setString(3,name.strip());
                    try(ResultSet rows=query.executeQuery()) {
                        if(!rows.next()) throw new SQLException("Category insertion failed.");
                        record=new in.smartvrms.contracts.VehicleCategoryRecord(rows.getObject(1,UUID.class),code,name.strip());
                    }
                }
                in.smartvrms.server.security.JdbcBusinessAudit.append(c,session,"VEHICLE_CATEGORY_CREATED","VEHICLE_CATEGORY",record.id().toString(),"{}");
                c.commit();return record;
            } catch(Exception ex){c.rollback();throw ex;}
        } catch(SQLException ex){throw new IllegalStateException("Category could not be saved. Check whether its code already exists.");}
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.MANAGE_VEHICLE_MASTER);
                String categoryName;
                try(PreparedStatement categoryQuery=c.prepareStatement("SELECT name FROM vrms_private.vehicle_categories WHERE id=? AND company_id=?")) {
                    categoryQuery.setQueryTimeout(10);categoryQuery.setObject(1,categoryId);categoryQuery.setObject(2,session.companyId());
                    try(ResultSet categoryRows=categoryQuery.executeQuery()) {
                        if(!categoryRows.next()) throw new IllegalArgumentException("Choose a category belonging to this company.");
                        categoryName=categoryRows.getString(1);
                    }
                }
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
                            categoryName,
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
                in.smartvrms.server.security.JdbcBusinessAudit.append(c, session, "VEHICLE_REGISTERED", "VEHICLE", record.id().toString(), in.smartvrms.server.security.JdbcBusinessAudit.detail("plate", plate));
                c.commit();
                return record;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.VIEW_FLEET);
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
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.VIEW_FLEET);
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
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.RECORD_FLEET_FREEZE);
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
                        in.smartvrms.server.security.JdbcBusinessAudit.append(c, session, "ASSET_FROZEN", assetType, assetId.toString(), in.smartvrms.server.security.JdbcBusinessAudit.detail("reason", reason));
                        c.commit();
                        return new AssetRestrictionRecord(id, session.companyId(), assetType, assetId, "FREEZE", startTime, endTime, reason, true);
                    }
                }
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.APPROVE_MAINTENANCE_RESOLUTION);
                try (PreparedStatement s = c.prepareStatement(sql)) {
                    s.setObject(1, restrictionId);
                    s.setObject(2, session.companyId());
                    s.executeUpdate();
                }
                in.smartvrms.server.security.JdbcBusinessAudit.append(c, session, "RESTRICTION_RESOLVED", "RESTRICTION", restrictionId.toString(), "{}");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.RENEW_ASSIGNED_DOCUMENT);
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
                        in.smartvrms.server.security.JdbcBusinessAudit.append(c, session, "DOCUMENT_ADDED", "VEHICLE_DOC", id.toString(), in.smartvrms.server.security.JdbcBusinessAudit.detail("type", docType));
                        c.commit();
                        return new VehicleDocumentRecord(id, session.companyId(), vehicleId, docType, fileId, issueDate, expiryDate, ver);
                    }
                }
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
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
                in.smartvrms.server.security.JdbcStaffContext.require(c, session, in.smartvrms.server.security.Capability.VIEW_FLEET);
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
                if (e instanceof SecurityException denied) throw denied;
                if (e instanceof IllegalArgumentException invalid) throw invalid;
                throw new RuntimeException("Failed to get vehicle documents", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error getting vehicle documents", e);
        }
        return Collections.unmodifiableList(list);
    }

}
