package br.com.erudio.testsupport;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class NetworkAssumptions {

    private static final String REPORT_IMAGES_HOST = "raw.githubusercontent.com";

    private NetworkAssumptions() {}

    /**
     * The PDF templates download their images (logo, person photo) from GitHub while the report is generated,
     * so the PDF tests are skipped, not failed, when that host cannot be reached.
     */
    public static void assumeReportImagesAreReachable() {
        Assumptions.assumeTrue(isReachable(REPORT_IMAGES_HOST, 443),
            "PDF reports download images from " + REPORT_IMAGES_HOST + ", which is not reachable");
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
