package unlicense.launcher;

import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoManager {
    public static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    public static final String BFU_ALIAS = "bfu_key";
    public static final String CE_ALIAS = "ce_key";

    public static void initKeys() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(BFU_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                keyGenerator.init(new KeyGenParameterSpec.Builder(BFU_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build());
                keyGenerator.generateKey();
            }

            if (!keyStore.containsAlias(CE_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(CE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setUnlockedDeviceRequired(true);
                }
                keyGenerator.init(builder.build());
                keyGenerator.generateKey();
            }
        } catch (Exception ignored) {}
    }

    private static String encrypt(String alias, String data) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(alias, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }

    private static String decrypt(String alias, String base64Data) {
        if (base64Data == null) return null;
        try {
            byte[] combined = Base64.decode(base64Data, Base64.NO_WRAP);
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(alias, null);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, combined, 0, 12);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] decrypted = cipher.doFinal(combined, 12, combined.length - 12);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) { return null; }
    }

    public static void putInt(SharedPreferences prefs, String alias, String key, int value) {
        String encrypted = encrypt(alias, String.valueOf(value));
        if (encrypted != null) prefs.edit().putString(key, encrypted).commit();
    }

    public static int getInt(SharedPreferences prefs, String alias, String key, int defValue) {
        String encrypted = prefs.getString(key, null);
        if (encrypted == null) return defValue;
        String decrypted = decrypt(alias, encrypted);
        try { return decrypted != null ? Integer.parseInt(decrypted) : defValue; }
        catch (Exception e) { return defValue; }
    }

    public static void putBoolean(SharedPreferences prefs, String alias, String key, boolean value) {
        String encrypted = encrypt(alias, String.valueOf(value));
        if (encrypted != null) prefs.edit().putString(key, encrypted).commit();
    }

    public static boolean getBoolean(SharedPreferences prefs, String alias, String key, boolean defValue) {
        String encrypted = prefs.getString(key, null);
        if (encrypted == null) return defValue;
        String decrypted = decrypt(alias, encrypted);
        try { return decrypted != null ? Boolean.parseBoolean(decrypted) : defValue; }
        catch (Exception e) { return defValue; }
    }

    public static void putString(SharedPreferences prefs, String alias, String key, String value) {
        String encrypted = encrypt(alias, value);
        if (encrypted != null) prefs.edit().putString(key, encrypted).commit();
    }

    public static String getString(SharedPreferences prefs, String alias, String key, String defValue) {
        String encrypted = prefs.getString(key, null);
        if (encrypted == null) return defValue;
        String decrypted = decrypt(alias, encrypted);
        return decrypted != null ? decrypted : defValue;
    }
}
