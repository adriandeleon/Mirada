package com.mirada.update;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * One check against a local server standing in for GitHub; never the real one. A socket answering
 * one request by hand: the JDK's own server is a module the tests cannot read.
 */
class UpdateServiceTest {

    private ServerSocket server;

    private String serve(int status, String body) throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        ServerSocket listening = server;
        Thread.ofVirtual().start(() -> {
            try (Socket socket = listening.accept()) {
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    // the request's headers, unread
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 " + status + " X\r\nContent-Length: " + bytes.length
                                + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                // the test fails on what the client saw
            }
        });
        return "http://127.0.0.1:" + server.getLocalPort() + "/latest";
    }

    @AfterEach
    void stop() throws IOException {
        if (server != null) server.close();
    }

    @Test
    void aNewerReleaseIsFound() throws IOException {
        String url = serve(200, "{\"tag_name\": \"v9.0.0\", \"html_url\": \"https://example.test/r\"}");
        UpdateService.Outcome outcome = new UpdateService(url).check("0.4.0");
        assertEquals(new ReleaseInfo("9.0.0", "https://example.test/r"), outcome.newer());
        assertNull(outcome.error());
    }

    @Test
    void theSameReleaseIsNothingNewer() throws IOException {
        String url = serve(200, "{\"tag_name\": \"v0.4.0\"}");
        assertEquals(new UpdateService.Outcome(null, null), new UpdateService(url).check("0.4.0"));
    }

    @Test
    void noReleaseYetIsNotAnError() throws IOException {
        String url = serve(404, "{\"message\": \"Not Found\"}");
        assertEquals(new UpdateService.Outcome(null, null), new UpdateService(url).check("0.4.0"));
    }

    @Test
    void aServerErrorIsAFailure() throws IOException {
        String url = serve(503, "");
        assertEquals(new UpdateService.Outcome(null, "HTTP 503"), new UpdateService(url).check("0.4.0"));
    }

    @Test
    void aClosedPortIsAFailure() {
        UpdateService.Outcome outcome = new UpdateService("http://127.0.0.1:9/latest").check("0.4.0");
        assertNull(outcome.newer());
        assertNotNull(outcome.error());
    }
}
