import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.regions.Region;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private static final String TABLE_NAME = "PasswordVault";
    private static final String VERIFY_ID = "__master_check__";

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 65536;

    private static DynamoDbClient client;

    private static DynamoDbClient getClient() {
        if (client == null) {
            client = DynamoDbClient.builder().region(Region.US_EAST_2).build();
        }
        return client;
    }

    public static void init() {
        // Table already created in AWS console, nothing needed here
    }

    public static class PasswordEntry {
        public final String site;
        public final String username;
        public final String encryptedPassword;

        public PasswordEntry(String site, String username, String encryptedPassword) {
            this.site = site;
            this.username = username;
            this.encryptedPassword = encryptedPassword;
        }
    }

    // ---------- Master password setup / check ----------

    public static boolean masterPasswordIsSet() {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(VERIFY_ID).build());
        GetItemRequest request = GetItemRequest.builder().tableName(TABLE_NAME).key(key).build();
        return getClient().getItem(request).hasItem();
    }

    public static void setupMasterPassword(String masterPassword) {
        String token = encrypt("verified", masterPassword);
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(VERIFY_ID).build());
        item.put("encryptedPassword", AttributeValue.builder().s(token).build());
        getClient().putItem(PutItemRequest.builder().tableName(TABLE_NAME).item(item).build());
    }

    public static boolean checkMasterPassword(String masterPassword) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(VERIFY_ID).build());
        GetItemResponse response = getClient().getItem(GetItemRequest.builder().tableName(TABLE_NAME).key(key).build());
        if (!response.hasItem()) return false;
        try {
            decrypt(response.item().get("encryptedPassword").s(), masterPassword);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- CRUD ----------

    public static void savePassword(String site, String username, String plainPassword, String masterPassword) {
        if (site == null || site.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("Site and username cannot be empty");
        }
        String encryptedPassword = encrypt(plainPassword, masterPassword);
        String id = site + "|" + username;

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("site", AttributeValue.builder().s(site).build());
        item.put("username", AttributeValue.builder().s(username).build());
        item.put("encryptedPassword", AttributeValue.builder().s(encryptedPassword).build());

        getClient().putItem(PutItemRequest.builder().tableName(TABLE_NAME).item(item).build());
    }

    public static void deletePassword(String site, String username) {
        String id = site + "|" + username;
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", AttributeValue.builder().s(id).build());
        getClient().deleteItem(DeleteItemRequest.builder().tableName(TABLE_NAME).key(key).build());
    }

    public static List<PasswordEntry> getAllPasswords() {
        ScanResponse response = getClient().scan(ScanRequest.builder().tableName(TABLE_NAME).build());
        List<PasswordEntry> entries = new ArrayList<>();

        for (Map<String, AttributeValue> item : response.items()) {
            if (item.get("id").s().equals(VERIFY_ID)) continue;
            entries.add(new PasswordEntry(
                    item.get("site").s(),
                    item.get("username").s(),
                    item.get("encryptedPassword").s()
            ));
        }
        return entries;
    }

    public static String decryptEntry(PasswordEntry entry, String masterPassword) {
        return decrypt(entry.encryptedPassword, masterPassword);
    }

    // ---------- Encryption (AES-256-GCM, key derived via PBKDF2) ----------

    private static String encrypt(String plainText, String masterPassword) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            SecretKeySpec key = deriveKey(masterPassword, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] combined = new byte[salt.length + iv.length + cipherText.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(cipherText, 0, combined, salt.length + iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private static String decrypt(String encryptedText, String masterPassword) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - SALT_LENGTH - IV_LENGTH];

            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH);
            System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKeySpec key = deriveKey(masterPassword, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, "UTF-8");
        } catch (AEADBadTagException e) {
            throw new RuntimeException("Incorrect master password", e);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private static SecretKeySpec deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}