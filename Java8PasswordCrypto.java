import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Java8PasswordCrypto {

    private static final Logger logger = Logger.getLogger(Java8PasswordCrypto.class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 128;      // 128-bit works on ALL Java 1.8 updates without policy files
    private static final int IV_LENGTH_BYTES = 12;     // Standard 96-bit IV for GCM
    private static final int TAG_LENGTH_BITS = 128;    // 128-bit authentication tag

    // Generates a random 128-bit AES key
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_SIZE_BITS, new SecureRandom());
        return keyGen.generateKey();
    }

    // Helper: Convert SecretKey to Base64 String (for saving in config/env)
    public static String keyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // Helper: Restore SecretKey from a Base64 String
    public static SecretKey stringToKey(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public static String encrypt(String plainPassword, SecretKey key) throws Exception {
        // 1. Generate a fresh random IV for each encryption
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        // 2. Initialize Cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        // 3. Encrypt
        byte[] cipherText = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));

        // 4. Combine [IV + Ciphertext] so IV is available for decryption
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);

        // 5. Return Base64 string
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    public static String decrypt(String encryptedBase64, SecretKey key) throws Exception {
        // 1. Decode Base64
        byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

        // 2. Split [IV] and [Ciphertext]
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byteBuffer.get(iv);
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        // 3. Initialize Cipher for decryption
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        // 4. Decrypt
        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        // Generate and serialize a key (save base64Key in an environment variable or vault)
        SecretKey key = generateKey();
        String base64Key = "n8n3tvukHFJW15UiVzUpvQ==";  // keyToString(key);

        if ( args.length > 0 )
        { 
           String password = args[0];

           // Encrypt
           String encrypted = encrypt(password, stringToKey(base64Key));

           // Decrypt
           String decrypted = decrypt(encrypted, stringToKey(base64Key));

           System.out.println("Base64 Key: " + base64Key);
           System.out.println("Encrypted:  " + encrypted);
           System.out.println("Decrypted:  " + decrypted);
        }

        try {

            LocalDate today = LocalDate.now();
            System.out.println("Date: " + today);

            // 'true' enables append mode so previous logs aren't overwritten
            FileHandler fileHandler = new FileHandler("application_" + today + ".log", true);
            
            // Format logs as readable plain text instead of the default XML
            fileHandler.setFormatter(new SimpleFormatter());
            
            logger.addHandler(fileHandler);

            // Write log entries
            logger.info("User logged in successfully.");
            logger.warning("Disk space is running low.");
            logger.severe("Failed to connect to the database.");



        // Current date and time (e.g., 2026-11-18T10:32:00.123)
        LocalDateTime now = LocalDateTime.now();
        System.out.println("Raw DateTime: " + now);

        // Format into a readable string (great for log entries or filenames)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = now.format(formatter);
        System.out.println("Formatted: " + formattedDate);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}