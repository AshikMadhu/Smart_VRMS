package in.smartvrms.desktop.integration;

import in.smartvrms.desktop.infrastructure.AppVersion;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Optional;

/** Uses GitHub's official redirect, requiring neither tokens nor a JSON release feed. */
public final class UpdateChecker {
    public static Optional<URI> newerRelease(String current, String location) {
        if (location == null) return Optional.empty();
        try {
            URI uri = URI.create(location);
            String prefix = "/AshikMadhu/Smart_VRMS/releases/tag/v";
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !uri.getRawPath().startsWith(prefix)) return Optional.empty();
            String tag = uri.getRawPath().substring(prefix.length());
            if (!tag.matches("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})")) return Optional.empty();
            String[] latest = tag.split("\\."), installed = current.split("-", 2)[0].split("\\.");
            if (installed.length != 3) return Optional.empty();
            for (int i = 0; i < 3; i++) {
                int comparison = Long.compare(Long.parseLong(latest[i]), Long.parseLong(installed[i]));
                if (comparison > 0) return Optional.of(uri);
                if (comparison < 0) return Optional.empty();
            }
            return current.contains("-") ? Optional.of(uri) : Optional.empty();
        } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
    public Optional<URI> check() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(AppVersion.RELEASES + "/latest"))
                .timeout(Duration.ofSeconds(8)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 404) return Optional.empty();
        if (response.statusCode() != 302 && response.statusCode() != 301) throw new IllegalStateException("Update check unavailable.");
        return newerRelease(AppVersion.CURRENT, response.headers().firstValue("Location").orElse(null));
    }
}
