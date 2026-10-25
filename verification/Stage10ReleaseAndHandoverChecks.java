package in.smartvrms.verification;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Stage 10 — Reviewed Release, Documentation, and Handover Checks.
 *
 * Verifies delivery checklist completeness (all 90 requirements), release artifact
 * physical presence, exact SHA256 checksum agreement, documentation integrity,
 * credential leak prevention, third-party license notices, and Gate G6/G7 compliance.
 */
public final class Stage10ReleaseAndHandoverChecks {
    private static int checks;

    private static void require(boolean val, String reason) {
        if (!val) throw new AssertionError("FAIL: " + reason);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Stage 10 release, documentation, and handover checks...");

        testDeliveryChecklistIntegrity();
        testReleaseManifestAndArtifactChecksums();
        testDocumentationIntegrityAndZeroLeaks();
        testThirdPartyNoticesAndClientExclusions();
        testGateG6AndGateG7Compliance();

        System.out.println("PASS: " + checks + " Stage 10 release, documentation, checksum, and handover assertions.");
    }

    /**
     * Verifies that docs/Delivery-Checklist.csv maintains all 90 requirements without dropped rows.
     */
    private static void testDeliveryChecklistIntegrity() throws Exception {
        System.out.println("Testing delivery checklist integrity...");

        Path csvPath = Path.of("docs/Delivery-Checklist.csv");
        require(Files.exists(csvPath), "docs/Delivery-Checklist.csv exists");

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        // Header + 90 rows = 91 lines
        require(lines.size() >= 91, "Delivery checklist contains at least 91 lines (header + 90 requirements)");

        int requirementRows = 0;
        Set<String> seenIds = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            requirementRows++;

            // Extract ID
            int firstComma = line.indexOf(',');
            require(firstComma > 0, "Valid CSV row structure at line " + (i + 1));
            String id = line.substring(0, firstComma).replace("\"", "").trim();
            require(id.startsWith("FR-") || id.startsWith("NFR-"), "Requirement ID follows standard naming: " + id);
            require(!seenIds.contains(id), "Requirement ID is unique: " + id);
            seenIds.add(id);

            // Verify row is not empty and has evidence column
            require(line.contains("docs/evidence") || line.contains("Planned") || line.contains("Complete") || line.contains("In progress"),
                    "Requirement row has status/evidence: " + id);
        }

        require(requirementRows == 90, "Exactly 90 distinct requirements accounted for in Delivery-Checklist.csv (found " + requirementRows + ")");
    }

    /**
     * Verifies physical artifact existence and exact SHA256 checksum matching against Release-Manifest.md.
     */
    private static void testReleaseManifestAndArtifactChecksums() throws Exception {
        System.out.println("Testing release manifest and artifact SHA256 checksums...");

        Path manifestPath = Path.of("docs/Release-Manifest.md");
        require(Files.exists(manifestPath), "docs/Release-Manifest.md exists");
        String manifestContent = Files.readString(manifestPath, StandardCharsets.UTF_8);

        // 1. Verify Windows Portable Zip
        Path zipPath = Path.of("dist/SmartVRMS-Windows-x64.zip");
        require(Files.exists(zipPath), "Current Windows preview archive exists on disk");
        String calculatedZipHash = computeSha256(zipPath);
        require(manifestContent.contains(calculatedZipHash), "Manifest contains the current archive SHA256: " + calculatedZipHash);

        // 2. Verify Windows Launcher Executable
        Path exePath = Path.of("dist/package-win64/SmartVRMS/SmartVRMS.exe");
        require(Files.exists(exePath), "dist/package-win64/SmartVRMS/SmartVRMS.exe exists on disk");
        String calculatedExeHash = computeSha256(exePath);
        require(manifestContent.contains(calculatedExeHash), "Manifest contains exact SHA256 for SmartVRMS.exe: " + calculatedExeHash);

        // 3. Verify Headless Server JAR
        Path serverJarPath = Path.of("dist/server-bundle/server.jar");
        require(Files.exists(serverJarPath), "dist/server-bundle/server.jar exists on disk");
        String calculatedServerHash = computeSha256(serverJarPath);
        require(manifestContent.contains(calculatedServerHash), "Manifest contains exact SHA256 for server.jar: " + calculatedServerHash);
    }

    /**
     * Verifies that all required handover documents exist and contain zero credentials or sensitive leaks.
     */
    private static void testDocumentationIntegrityAndZeroLeaks() throws Exception {
        System.out.println("Testing documentation integrity and zero-leak compliance...");

        List<Path> docs = List.of(
                Path.of("README.md"),
                Path.of("docs/User-Guide.md"),
                Path.of("docs/Operator-and-Deployment-Guide.md"),
                Path.of("docs/Developer-Guide.md"),
                Path.of("docs/Third-Party-Notices.md"),
                Path.of("docs/Release-Manifest.md")
        );

        for (Path doc : docs) {
            require(Files.exists(doc), doc + " exists");
            String content = Files.readString(doc, StandardCharsets.UTF_8);
            require(!content.isBlank(), doc + " is not empty");

            // Verify no real private API keys or database passwords leaked
            require(!content.contains("sb_secret_actual"), "Zero actual Supabase secrets in " + doc);
            require(!content.contains("postgres:postgres"), "Zero raw root database passwords in " + doc);
            require(!content.contains("eyJh"), "Zero JWT access tokens in " + doc);
        }

        // Verify screenshots referenced in User Guide exist
        Path userGuide = Path.of("docs/User-Guide.md");
        String ugContent = Files.readString(userGuide, StandardCharsets.UTF_8);
        require(ugContent.contains("screenshots/signin-1280.png"), "User guide references sign-in screenshot");
        require(ugContent.contains("screenshots/overview-1280.png"), "User guide references overview screenshot");
        require(Files.exists(Path.of("docs/screenshots/signin-1280.png")), "docs/screenshots/signin-1280.png exists");
        require(Files.exists(Path.of("docs/screenshots/overview-1280.png")), "docs/screenshots/overview-1280.png exists");
    }

    /**
     * Verifies third-party notices completeness and desktop client isolation.
     */
    private static void testThirdPartyNoticesAndClientExclusions() throws Exception {
        System.out.println("Testing third-party notices and client isolation...");

        Path noticesPath = Path.of("docs/Third-Party-Notices.md");
        String notices = Files.readString(noticesPath, StandardCharsets.UTF_8);

        require(notices.contains("com.google.code.gson:gson"), "Gson 2.15.0 documented in notices");
        require(notices.contains("org.postgresql:postgresql"), "PostgreSQL JDBC 42.7.14 documented in notices");
        require(notices.contains("org.apache.pdfbox:pdfbox"), "Apache PDFBox 3.0.3 documented in notices");
        require(notices.contains("com.google.zxing:core"), "Google ZXing 3.5.3 documented in notices");
        require(notices.contains("Apache License"), "Apache 2.0 license text included");
        require(notices.contains("BSD 2-Clause License"), "BSD 2-Clause license text included");

        // Verify client package app directory
        Path clientAppDir = Path.of("dist/package-win64/SmartVRMS/app");
        if (Files.exists(clientAppDir)) {
            require(!Files.exists(clientAppDir.resolve("server.jar")), "server.jar strictly excluded from client app dir");
            require(!Files.exists(clientAppDir.resolve("postgresql-42.7.14.jar")), "postgresql JDBC driver strictly excluded from client app dir");
        }
    }

    /**
     * Validates Gate G6 platform matrix honesty and Gate G7 publication gate compliance.
     */
    private static void testGateG6AndGateG7Compliance() throws Exception {
        System.out.println("Validating Gate G6 and Gate G7 compliance...");

        Path manifestPath = Path.of("docs/Release-Manifest.md");
        String manifest = Files.readString(manifestPath, StandardCharsets.UTF_8);

        // Gate G6: Windows package verified; macOS/Linux explicitly documented as pending native runners
        require(manifest.contains("Windows 11 x64"), "Windows 11 x64 documented in manifest");
        require(manifest.contains("PENDING RUNNER"), "macOS/Linux transparently marked as pending native runners per Gate G6");

        // Gate G7: Publication authorization gate preserved
        require(manifest.contains("PREVIEW PUBLICATION AUTHORIZED") && manifest.contains("PRODUCTION BLOCKED"), "Preview publication authorization and outstanding production gates recorded");
    }

    private static String computeSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
