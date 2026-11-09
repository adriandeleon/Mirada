package com.mirada.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.mirada.AppInfo;
import org.jspecify.annotations.Nullable;

import static com.mirada.i18n.Messages.tr;

/**
 * Asks GitHub's {@code releases/latest} whether there is a newer Mirada. Blocking: callers run it off
 * the FX thread. Sends nothing about the user beyond what any HTTPS request carries, and never
 * downloads or installs anything (DIAG-05).
 */
public final class UpdateService {

    /** What came back: a newer release, nothing newer, or a failure with a short reason. */
    public record Outcome(
            @Nullable ReleaseInfo newer, @Nullable String error) {}

    /** Points the check elsewhere; tests use a closed local port. */
    public static final String URL_PROPERTY = "mirada.updateUrl";

    /** A latest-release answer is a few kilobytes; a body past this is refused unread. */
    static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String endpoint;

    public UpdateService() {
        this(System.getProperty(URL_PROPERTY, AppInfo.LATEST_RELEASE_API));
    }

    public UpdateService(String endpoint) {
        this.endpoint = endpoint;
    }

    /** One check against {@code currentVersion}. */
    public Outcome check(String currentVersion) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", AppInfo.NAME + "/" + AppInfo.VERSION)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (InputStream in = response.body()) {
                byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) return new Outcome(null, tr("update.error.tooLarge"));
                body = new String(bytes, StandardCharsets.UTF_8);
            }
            // 404: nothing published yet. Nothing newer, not an error.
            if (response.statusCode() == 404) return new Outcome(null, null);
            if (response.statusCode() != 200) return new Outcome(null, tr("update.error.http", response.statusCode()));
            ReleaseInfo latest = UpdateCheck.parseLatest(body);
            if (latest == null) return new Outcome(null, tr("update.error.unreadable"));
            return new Outcome(UpdateCheck.isNewer(currentVersion, latest.version()) ? latest : null, null);
        } catch (HttpTimeoutException e) {
            return new Outcome(null, tr("update.error.timedOut"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, tr("update.error.interrupted"));
        } catch (IOException | IllegalArgumentException e) {
            return new Outcome(null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * One check, printed: for {@code scripts/native-metadata.sh}, so the tracing agent sees what
     * HttpClient and TLS reach in the native image, which no tour does.
     */
    public static void main(String[] args) {
        Outcome outcome = new UpdateService().check(args.length > 0 ? args[0] : "0.0.1");
        System.out.println(outcome);
    }
}
