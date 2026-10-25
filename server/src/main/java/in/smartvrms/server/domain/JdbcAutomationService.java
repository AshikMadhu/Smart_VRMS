package in.smartvrms.server.domain;

import in.smartvrms.application.port.AuditPort;
import in.smartvrms.application.port.AutomationPort;
import in.smartvrms.contracts.DeliveryAttemptRecord;
import in.smartvrms.contracts.OutboxMessageRecord;
import in.smartvrms.contracts.StaffSession;
import in.smartvrms.contracts.WorkerHealthStatus;
import in.smartvrms.server.automation.DocumentReminderScanner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class JdbcAutomationService implements AutomationPort {

    private final DataSource dataSource;
    private final AuditPort auditPort;

    public JdbcAutomationService(DataSource dataSource, AuditPort auditPort) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditPort = auditPort;
    }

    private void setContext(Connection c, StaffSession session) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT vrms_private.set_app_context(?, ?)")) {
            ps.setObject(1, session.userId());
            ps.setObject(2, session.companyId());
            ps.execute();
        }
    }

    private void checkStaff(StaffSession session) {
        Objects.requireNonNull(session, "session");
        if (!"ADMINISTRATOR".equals(session.role()) && !"EMPLOYEE".equals(session.role())) {
            throw new SecurityException("Forbidden: Staff session required");
        }
    }

    @Override
    public OutboxMessageRecord enqueueMessage(
            StaffSession session,
            String eventType,
            String idempotencyKey,
            String recipientEmail,
            String recipientRole,
            String subject,
            String templateName,
            String payloadJson,
            UUID attachmentDocId
    ) {
        checkStaff(session);
        try (Connection c = dataSource.getConnection()) {
            setContext(c, session);

            // Check if already enqueued
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, company_id, event_type, idempotency_key, recipient_email, recipient_role, subject, " +
                    "template_name, payload_json, attachment_doc_id, status, lease_until, claimed_by, attempts_count, " +
                    "max_attempts, next_attempt_at, last_attempt_at, last_error, created_at " +
                    "FROM vrms_private.outbox_messages WHERE company_id = ? AND idempotency_key = ?"
            )) {
                ps.setObject(1, session.companyId());
                ps.setString(2, idempotencyKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapOutboxRecord(rs);
                    }
                }
            }

            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO vrms_private.outbox_messages " +
                    "(id, company_id, event_type, idempotency_key, recipient_email, recipient_role, subject, " +
                    "template_name, payload_json, attachment_doc_id, status, attempts_count, max_attempts, next_attempt_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'QUEUED', 0, 5, ?, ?)"
            )) {
                ps.setObject(1, id);
                ps.setObject(2, session.companyId());
                ps.setString(3, eventType);
                ps.setString(4, idempotencyKey);
                ps.setString(5, recipientEmail);
                ps.setString(6, recipientRole);
                ps.setString(7, subject);
                ps.setString(8, templateName);
                ps.setString(9, payloadJson != null ? payloadJson : "{}");
                ps.setObject(10, attachmentDocId);
                ps.setTimestamp(11, Timestamp.from(now));
                ps.setTimestamp(12, Timestamp.from(now));
                ps.executeUpdate();
            }

            return new OutboxMessageRecord(
                    id, session.companyId(), eventType, idempotencyKey, recipientEmail, recipientRole, subject,
                    templateName, payloadJson != null ? payloadJson : "{}", attachmentDocId, "QUEUED",
                    null, null, 0, 5, now, null, null, now
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to enqueue outbox message: " + e.getMessage(), e);
        }
    }

    @Override
    public List<OutboxMessageRecord> claimBatch(String workerId, int limit, int leaseDurationSeconds) {
        List<OutboxMessageRecord> claimed = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Instant now = Instant.now();
                Instant leaseUntil = now.plusSeconds(leaseDurationSeconds);

                List<UUID> idsToClaim = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM vrms_private.outbox_messages " +
                        "WHERE (status = 'QUEUED' AND next_attempt_at <= ?) " +
                        "   OR (status = 'CLAIMED' AND lease_until < ?) " +
                        "ORDER BY next_attempt_at ASC LIMIT ? FOR UPDATE SKIP LOCKED"
                )) {
                    ps.setTimestamp(1, Timestamp.from(now));
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setInt(3, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            idsToClaim.add((UUID) rs.getObject("id"));
                        }
                    }
                }

                if (!idsToClaim.isEmpty()) {
                    for (UUID id : idsToClaim) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE vrms_private.outbox_messages " +
                                "SET status = 'CLAIMED', lease_until = ?, claimed_by = ? " +
                                "WHERE id = ? " +
                                "RETURNING id, company_id, event_type, idempotency_key, recipient_email, recipient_role, " +
                                "subject, template_name, payload_json, attachment_doc_id, status, lease_until, claimed_by, " +
                                "attempts_count, max_attempts, next_attempt_at, last_attempt_at, last_error, created_at"
                        )) {
                            ps.setTimestamp(1, Timestamp.from(leaseUntil));
                            ps.setString(2, workerId);
                            ps.setObject(3, id);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    claimed.add(mapOutboxRecord(rs));
                                }
                            }
                        }
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to claim outbox batch: " + e.getMessage(), e);
        }
        return claimed;
    }

    @Override
    public void markAccepted(UUID messageId, String providerName, int responseCode, String messageIdRef) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID companyId = null;
                int currentAttempts = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT company_id, attempts_count FROM vrms_private.outbox_messages WHERE id = ? FOR UPDATE"
                )) {
                    ps.setObject(1, messageId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            companyId = (UUID) rs.getObject("company_id");
                            currentAttempts = rs.getInt("attempts_count");
                        }
                    }
                }

                if (companyId == null) {
                    c.rollback();
                    return;
                }

                int attemptNum = currentAttempts + 1;
                Instant now = Instant.now();

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE vrms_private.outbox_messages " +
                        "SET status = 'ACCEPTED', attempts_count = ?, last_attempt_at = ?, lease_until = NULL " +
                        "WHERE id = ?"
                )) {
                    ps.setInt(1, attemptNum);
                    ps.setTimestamp(2, Timestamp.from(now));
                    ps.setObject(3, messageId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO vrms_private.delivery_attempts " +
                        "(company_id, outbox_id, attempt_number, started_at, finished_at, provider_name, " +
                        "provider_response_code, provider_message_id, outcome) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACCEPTED')"
                )) {
                    ps.setObject(1, companyId);
                    ps.setObject(2, messageId);
                    ps.setInt(3, attemptNum);
                    ps.setTimestamp(4, Timestamp.from(now.minusMillis(50)));
                    ps.setTimestamp(5, Timestamp.from(now));
                    ps.setString(6, providerName);
                    ps.setInt(7, responseCode);
                    ps.setString(8, messageIdRef);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark message accepted: " + e.getMessage(), e);
        }
    }

    @Override
    public void markFailed(UUID messageId, String providerName, String errorMessage, boolean permanent) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                UUID companyId = null;
                int currentAttempts = 0;
                int maxAttempts = 5;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT company_id, attempts_count, max_attempts FROM vrms_private.outbox_messages WHERE id = ? FOR UPDATE"
                )) {
                    ps.setObject(1, messageId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            companyId = (UUID) rs.getObject("company_id");
                            currentAttempts = rs.getInt("attempts_count");
                            maxAttempts = rs.getInt("max_attempts");
                        }
                    }
                }

                if (companyId == null) {
                    c.rollback();
                    return;
                }

                int attemptNum = currentAttempts + 1;
                Instant now = Instant.now();
                boolean isFinalFail = permanent || attemptNum >= maxAttempts;
                String nextStatus = isFinalFail ? "FAILED" : "QUEUED";

                // Bounded exponential backoff: 30s * 2^attempts
                long delaySeconds = Math.min(3600L, 30L * (1L << Math.min(6, attemptNum)));
                Instant nextAttempt = now.plusSeconds(delaySeconds);

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE vrms_private.outbox_messages " +
                        "SET status = ?, attempts_count = ?, last_attempt_at = ?, next_attempt_at = ?, " +
                        "last_error = ?, lease_until = NULL " +
                        "WHERE id = ?"
                )) {
                    ps.setString(1, nextStatus);
                    ps.setInt(2, attemptNum);
                    ps.setTimestamp(3, Timestamp.from(now));
                    ps.setTimestamp(4, Timestamp.from(nextAttempt));
                    ps.setString(5, errorMessage);
                    ps.setObject(6, messageId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO vrms_private.delivery_attempts " +
                        "(company_id, outbox_id, attempt_number, started_at, finished_at, provider_name, error_message, outcome) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    ps.setObject(1, companyId);
                    ps.setObject(2, messageId);
                    ps.setInt(3, attemptNum);
                    ps.setTimestamp(4, Timestamp.from(now.minusMillis(50)));
                    ps.setTimestamp(5, Timestamp.from(now));
                    ps.setString(6, providerName);
                    ps.setString(7, errorMessage);
                    ps.setString(8, isFinalFail ? "PERMANENT_FAILURE" : "RETRYABLE_ERROR");
                    ps.executeUpdate();
                }

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark message failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int scanDocumentReminders(StaffSession session, Instant clockTime) {
        checkStaff(session);
        int queuedCount = 0;
        try (Connection c = dataSource.getConnection()) {
            setContext(c, session);

            // 1. Fetch active company administrator email
            String adminEmail = "admin@smartvrms.in";
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT u.email FROM vrms_private.memberships m " +
                    "JOIN auth.users u ON m.user_id = u.id " +
                    "WHERE m.company_id = ? AND m.role = 'ADMINISTRATOR' AND m.membership_status = 'ACTIVE' LIMIT 1"
            )) {
                ps.setObject(1, session.companyId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        adminEmail = rs.getString("email");
                    }
                }
            }

            // 2. Fetch all vehicle documents
            List<DocToScan> docs = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT d.id, d.vehicle_id, d.document_type, d.expiry_date, d.document_version, v.license_plate " +
                    "FROM vrms_private.vehicle_documents d " +
                    "JOIN vrms_private.vehicles v ON d.vehicle_id = v.id " +
                    "WHERE d.company_id = ?"
            )) {
                ps.setObject(1, session.companyId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp expTs = rs.getTimestamp("expiry_date");
                        docs.add(new DocToScan(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("vehicle_id"),
                                rs.getString("document_type"),
                                expTs != null ? expTs.toInstant() : null,
                                rs.getInt("document_version"),
                                rs.getString("license_plate")
                        ));
                    }
                }
            }

            for (DocToScan doc : docs) {
                if (doc.expiry == null) {
                    continue; // Non-expiring documents emit no reminder
                }

                DocumentReminderScanner.ReminderStage stage = DocumentReminderScanner.evaluateStage(doc.expiry, clockTime);
                if (stage == DocumentReminderScanner.ReminderStage.NONE) {
                    continue;
                }

                String stageName = (stage == DocumentReminderScanner.ReminderStage.LESS_THAN_72H) ? "LESS_THAN_72H" : "EXPIRED";

                // Check if already scanned/sent for this version and stage
                boolean alreadyDispatched = false;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM vrms_private.document_reminder_scans " +
                        "WHERE company_id = ? AND document_id = ? AND document_version = ? AND reminder_stage = ? AND recipient_email = ?"
                )) {
                    ps.setObject(1, session.companyId());
                    ps.setObject(2, doc.id);
                    ps.setInt(3, doc.version);
                    ps.setString(4, stageName);
                    ps.setString(5, adminEmail);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            alreadyDispatched = true;
                        }
                    }
                }

                if (alreadyDispatched) {
                    continue;
                }

                // Invalidate older version pending reminders (supersede)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE vrms_private.document_reminder_scans " +
                        "SET superseded = true " +
                        "WHERE company_id = ? AND document_id = ? AND document_version < ?"
                )) {
                    ps.setObject(1, session.companyId());
                    ps.setObject(2, doc.id);
                    ps.setInt(3, doc.version);
                    ps.executeUpdate();
                }

                // Enqueue reminder outbox message
                String eventType = (stage == DocumentReminderScanner.ReminderStage.LESS_THAN_72H) ? "EXPIRY_REMINDER_72H" : "EXPIRY_REMINDER_EXPIRED";
                String key = DocumentReminderScanner.buildIdempotencyKey(session.companyId(), doc.id, doc.version, stage, adminEmail);
                String subject = (stage == DocumentReminderScanner.ReminderStage.LESS_THAN_72H)
                        ? "Urgent: Vehicle Document " + doc.docType + " (" + doc.plate + ") Expiring Within 72 Hours"
                        : "Action Required: Vehicle Document " + doc.docType + " (" + doc.plate + ") Has Expired";

                String payload = "{\"vehicleId\":\"" + doc.vehicleId + "\",\"documentType\":\"" + doc.docType + "\",\"plate\":\"" + doc.plate + "\",\"version\":" + doc.version + "}";

                OutboxMessageRecord outbox = enqueueMessage(
                        session, eventType, key, adminEmail, "ADMINISTRATOR", subject, "document_reminder", payload, null
                );

                // Record scan event
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO vrms_private.document_reminder_scans " +
                        "(company_id, document_id, document_version, reminder_stage, recipient_email, outbox_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    ps.setObject(1, session.companyId());
                    ps.setObject(2, doc.id);
                    ps.setInt(3, doc.version);
                    ps.setString(4, stageName);
                    ps.setString(5, adminEmail);
                    ps.setObject(6, outbox.id());
                    ps.executeUpdate();
                }

                queuedCount++;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to scan document reminders: " + e.getMessage(), e);
        }
        return queuedCount;
    }

    @Override
    public WorkerHealthStatus getWorkerHealth(StaffSession session) {
        checkStaff(session);
        try (Connection c = dataSource.getConnection()) {
            setContext(c, session);

            int pending = 0;
            int claimed = 0;
            int failed = 0;
            int accepted = 0;
            Instant oldest = null;

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT status, count(*), min(created_at) FROM vrms_private.outbox_messages " +
                    "WHERE company_id = ? GROUP BY status"
            )) {
                ps.setObject(1, session.companyId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String st = rs.getString(1);
                        int count = rs.getInt(2);
                        Timestamp minCreated = rs.getTimestamp(3);

                        if ("QUEUED".equals(st)) {
                            pending = count;
                            if (minCreated != null && (oldest == null || minCreated.toInstant().isBefore(oldest))) {
                                oldest = minCreated.toInstant();
                            }
                        } else if ("CLAIMED".equals(st)) {
                            claimed = count;
                        } else if ("FAILED".equals(st)) {
                            failed = count;
                        } else if ("ACCEPTED".equals(st)) {
                            accepted = count;
                        }
                    }
                }
            }

            long oldestSec = (oldest != null) ? Duration.between(oldest, Instant.now()).getSeconds() : 0L;
            boolean isLagging = (pending > 10) || (oldestSec > 3600L);

            return new WorkerHealthStatus(Instant.now(), pending, claimed, failed, accepted, oldestSec, isLagging);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get worker health: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DeliveryAttemptRecord> listAttempts(StaffSession session, UUID messageId) {
        checkStaff(session);
        List<DeliveryAttemptRecord> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            setContext(c, session);
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, company_id, outbox_id, attempt_number, started_at, finished_at, provider_name, " +
                    "provider_response_code, provider_message_id, error_message, outcome " +
                    "FROM vrms_private.delivery_attempts WHERE company_id = ? AND outbox_id = ? " +
                    "ORDER BY attempt_number ASC"
            )) {
                ps.setObject(1, session.companyId());
                ps.setObject(2, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp fin = rs.getTimestamp("finished_at");
                        list.add(new DeliveryAttemptRecord(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("company_id"),
                                (UUID) rs.getObject("outbox_id"),
                                rs.getInt("attempt_number"),
                                rs.getTimestamp("started_at").toInstant(),
                                fin != null ? fin.toInstant() : null,
                                rs.getString("provider_name"),
                                (Integer) rs.getObject("provider_response_code"),
                                rs.getString("provider_message_id"),
                                rs.getString("error_message"),
                                rs.getString("outcome")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list delivery attempts: " + e.getMessage(), e);
        }
        return list;
    }

    private static OutboxMessageRecord mapOutboxRecord(ResultSet rs) throws SQLException {
        Timestamp lease = rs.getTimestamp("lease_until");
        Timestamp last = rs.getTimestamp("last_attempt_at");
        return new OutboxMessageRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                rs.getString("event_type"),
                rs.getString("idempotency_key"),
                rs.getString("recipient_email"),
                rs.getString("recipient_role"),
                rs.getString("subject"),
                rs.getString("template_name"),
                rs.getString("payload_json"),
                (UUID) rs.getObject("attachment_doc_id"),
                rs.getString("status"),
                lease != null ? lease.toInstant() : null,
                rs.getString("claimed_by"),
                rs.getInt("attempts_count"),
                rs.getInt("max_attempts"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                last != null ? last.toInstant() : null,
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static class DocToScan {
        final UUID id;
        final UUID vehicleId;
        final String docType;
        final Instant expiry;
        final int version;
        final String plate;

        DocToScan(UUID id, UUID vehicleId, String docType, Instant expiry, int version, String plate) {
            this.id = id;
            this.vehicleId = vehicleId;
            this.docType = docType;
            this.expiry = expiry;
            this.version = version;
            this.plate = plate;
        }
    }
}
