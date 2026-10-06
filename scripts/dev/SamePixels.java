import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Compares PNGs pixel for pixel the way Roborazzi reads a golden (AwtRoboCanvas.load): ImageIO
 * decodes the file, which is drawn with the Src rule onto an ARGB canvas. Two files this calls
 * "same" compare equal in Roborazzi's verify, however each is encoded (colour type, bit depth,
 * filters, compression). recompress-goldens.sh uses it to prove a recompression lossless.
 *
 * Usage: java -Djava.awt.headless=true SamePixels.java PAIRS
 *   PAIRS holds one "left<TAB>right" line per pair of files. Prints "same<TAB>left<TAB>right" or
 *   "diff<TAB>left<TAB>right" for each pair and exits 0; exits 2 on a malformed line or a file
 *   ImageIO can't read.
 */
public class SamePixels {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: java SamePixels.java PAIRS");
            System.exit(2);
        }
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            if (line.isBlank()) continue;
            String[] pair = line.split("\t", -1);
            if (pair.length != 2) {
                System.err.println("SamePixels: not a left<TAB>right line: " + line);
                System.exit(2);
            }
            String verdict = same(pair[0], pair[1]) ? "same" : "diff";
            System.out.println(verdict + "\t" + pair[0] + "\t" + pair[1]);
        }
    }

    private static boolean same(String left, String right) {
        BufferedImage a = argb(left);
        BufferedImage b = argb(right);
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        int width = a.getWidth();
        int height = a.getHeight();
        return Arrays.equals(a.getRGB(0, 0, width, height, null, 0, width), b.getRGB(0, 0, width, height, null, 0, width));
    }

    /** The file as Roborazzi sees it: decoded by ImageIO, drawn with the Src rule onto an ARGB canvas. */
    private static BufferedImage argb(String path) {
        BufferedImage image;
        try {
            image = ImageIO.read(new File(path));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            System.err.println("SamePixels: ImageIO can't read " + path);
            System.exit(2);
        }
        BufferedImage canvas = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
