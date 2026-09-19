package br.com.erudio.unittests.services;

import br.com.erudio.services.QRCodeService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QRCodeServiceTest {

    private final QRCodeService service = new QRCodeService();

    private static String decode(BufferedImage image) throws Exception {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    @Test
    void generatesAPngWithTheRequestedSize() throws Exception {
        try (InputStream png = service.generateQRCode("https://pub.erudio.com.br", 200, 200)) {
            BufferedImage image = ImageIO.read(png);

            assertEquals(200, image.getWidth());
            assertEquals(200, image.getHeight());
        }
    }

    @Test
    void generatesAQrCodeThatDecodesBackToTheUrl() throws Exception {
        String url = "https://en.wikipedia.org/wiki/Ayrton_Senna";

        try (InputStream png = service.generateQRCode(url, 300, 300)) {
            assertEquals(url, decode(ImageIO.read(png)));
        }
    }

    @Test
    void encodesTextWithAccents() throws Exception {
        String text = "Formação Spring Boot 2026";

        try (InputStream png = service.generateQRCode(text, 300, 300)) {
            assertEquals(text, decode(ImageIO.read(png)));
        }
    }

    @Test
    void rejectsEmptyContent() {
        assertThrows(IllegalArgumentException.class, () -> service.generateQRCode("", 200, 200));
    }
}
