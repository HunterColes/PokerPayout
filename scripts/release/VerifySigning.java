import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * Checks keystore.properties against the release keystore, parsing it exactly as Gradle does
 * (java.util.Properties). Prints pass/fail per step and never prints a secret.
 *
 * Usage: java VerifySigning.java <repoRoot> <expectedCertSha256>
 */
public class VerifySigning {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String expected = args[1].toLowerCase(Locale.ROOT);

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(root.resolve("keystore.properties"))) {
            props.load(in);
        }
        String storeFile = required(props, "storeFile");
        String storePassword = required(props, "storePassword");
        String keyAlias = required(props, "keyAlias");
        String keyPassword = required(props, "keyPassword");

        Path keystore = root.resolve(storeFile);
        if (!Files.isRegularFile(keystore)) fail("Keystore not found: " + keystore);

        KeyStore store = null;
        for (String type : new String[] {"PKCS12", "JKS"}) {
            try (InputStream in = Files.newInputStream(keystore)) {
                KeyStore candidate = KeyStore.getInstance(type);
                candidate.load(in, storePassword.toCharArray());
                store = candidate;
                break;
            } catch (Exception ignored) {
                // Wrong type or wrong password; try the next type.
            }
        }
        if (store == null) fail("storePassword does not open " + keystore.getFileName());
        ok("storePassword opens the keystore (" + store.getType() + ")");

        if (!store.containsAlias(keyAlias)) {
            fail("keyAlias '" + keyAlias + "' is not in the keystore. Aliases present: " + Collections.list(store.aliases()));
        }
        ok("keyAlias '" + keyAlias + "' found");

        try {
            Key key = store.getKey(keyAlias, keyPassword.toCharArray());
            if (!(key instanceof PrivateKey)) fail("'" + keyAlias + "' holds no private key");
        } catch (UnrecoverableKeyException e) {
            fail("keyPassword does not unlock the key");
        }
        ok("keyPassword unlocks the private key");

        byte[] cert = store.getCertificate(keyAlias).getEncoded();
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert));
        if (!sha256.equals(expected)) {
            fail("certificate SHA-256 " + sha256 + " does not match F-Droid's AllowedAPKSigningKeys " + expected);
        }
        ok("certificate matches F-Droid's AllowedAPKSigningKeys");
        System.out.println("Ready to sign releases.");
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) fail(key + " is empty in keystore.properties");
        return value.strip();
    }

    private static void ok(String message) {
        System.out.println("  ✔ " + message);
    }

    private static void fail(String message) {
        System.out.println("  ✘ " + message);
        System.exit(1);
    }
}
