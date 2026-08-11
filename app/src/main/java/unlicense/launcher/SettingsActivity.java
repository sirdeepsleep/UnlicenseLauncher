package unlicense.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_CONFIRM_CREDENTIAL_PIN = 2002;
    private static final int REQUEST_CODE_WALLPAPER = 1001;

    private static final String APP_PIN_HASH = "search_pin_hash";
    private static final String APP_PIN_SALT = "search_pin_salt";

    private SharedPreferences prefs;
    private int finalPadding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager == null || keyguardManager.isKeyguardLocked()) {
            setShowWhenLocked(false);
            finish();
            return;
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);        

        float density = getResources().getDisplayMetrics().density;
        finalPadding = (int) (44 * density);

        showMainMenu();
    }

    private void showMainMenu() {
        boolean disableScreenshots = prefs.getBoolean("disable_screenshots", false);        
        if (disableScreenshots) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(0, finalPadding, 0, finalPadding);
        root.setClipToPadding(false);
        
        LinearLayout screenshotsRow = new LinearLayout(this);
        screenshotsRow.setOrientation(LinearLayout.HORIZONTAL);
        screenshotsRow.setGravity(Gravity.CENTER);        

        TextView screenshotsText = new TextView(this);
        screenshotsText.setText("Disable screenshots in launcher");
        screenshotsText.setTextSize(15f);

        Switch screenshotsSwitch = new Switch(this);
        screenshotsSwitch.setChecked(prefs.getBoolean("disable_screenshots", false));
        screenshotsSwitch.setThumbTintList(ColorStateList.valueOf(Color.RED));

        screenshotsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("disable_screenshots", isChecked).commit();
            if (isChecked) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });

        screenshotsRow.addView(screenshotsText);
        screenshotsRow.addView(screenshotsSwitch);

        root.addView(screenshotsRow);

        String storedHash = CryptoManager.getString(prefs, CryptoManager.CE_ALIAS, APP_PIN_HASH, "");
        if (storedHash.isEmpty()) {
            Button btnSetSearchPin = new Button(this);
            btnSetSearchPin.setText("Set search PIN");
            btnSetSearchPin.setOnClickListener(v -> authenticateWithKeyguard(REQUEST_CODE_CONFIRM_CREDENTIAL_PIN));
            root.addView(btnSetSearchPin);
        }

        Button btnWallpaper = new Button(this);
        btnWallpaper.setText("Select wallpaper");
        btnWallpaper.setOnClickListener(v -> showWallpaperMenu());

        Button btnBack = new Button(this);
        btnBack.setText("Home");
        btnBack.setOnClickListener(v -> finish());

        root.addView(btnWallpaper);
        root.addView(btnBack);

        setContentView(root);
    }

    private void authenticateWithKeyguard(int requestCode) {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardSecure()) {
            Intent intent = km.createConfirmDeviceCredentialIntent("Authentication Required", "Confirm screen lock credential to set PIN");
            if (intent != null) {
                startActivityForResult(intent, requestCode);
                return;
            }
        }
        if (requestCode == REQUEST_CODE_CONFIRM_CREDENTIAL_PIN) {
            showSetSearchPinMenu();
        }
    }

    private void showSetSearchPinMenu() {        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);        
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(0, finalPadding, 0, finalPadding);

        TextView title = new TextView(this);
        title.setText("Set Search PIN Code");
        title.setTextSize(18f);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter PIN to display excluded apps when searched:");
        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);        

        EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setGravity(Gravity.CENTER);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(Gravity.CENTER);        

        Button backBtn = new Button(this);
        backBtn.setText("Back");
        backBtn.setOnClickListener(v -> showMainMenu());

        Button saveBtn = new Button(this);
        saveBtn.setText("Save PIN");
        saveBtn.setOnClickListener(v -> {
            String pin = pinInput.getText().toString().trim();
            if (pin.length() >= 4) {
                String salt = generateSalt();
                String hash = hashPin(pin, salt);
                CryptoManager.putString(prefs, CryptoManager.CE_ALIAS, APP_PIN_SALT, salt);
                CryptoManager.putString(prefs, CryptoManager.CE_ALIAS, APP_PIN_HASH, hash);
                showMainMenu();
            } else {
                Toast.makeText(this, "You must enter 4 or more digits", Toast.LENGTH_SHORT).show();
            }
        });

        btnLayout.addView(backBtn);
        btnLayout.addView(saveBtn);

        root.addView(title);
        root.addView(subtitle);
        root.addView(pinInput);
        root.addView(btnLayout);

        setContentView(root);
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.decode(salt, Base64.NO_WRAP));
            byte[] hash = md.digest(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void showWallpaperMenu() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(0, finalPadding, 0, finalPadding);
        root.setClipToPadding(false);

        Button btnChoose = new Button(this);
        btnChoose.setText("Choose from gallery");
        btnChoose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_CODE_WALLPAPER);
        });

        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setOnClickListener(v -> showMainMenu());

        root.addView(btnChoose);
        root.addView(backButton);

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_CONFIRM_CREDENTIAL_PIN) {
                showSetSearchPinMenu();
            } else if (requestCode == REQUEST_CODE_WALLPAPER && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    try {
                        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        if (inputStream != null) {
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            inputStream.close();
                            if (bitmap != null) {
                                wallpaperManager.setBitmap(bitmap, null, true,
                                        WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                                finish();
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager == null || keyguardManager.isKeyguardLocked()) {
            setShowWhenLocked(false);
            finish();
            return;
        }

        boolean disableScreenshots = prefs.getBoolean("disable_screenshots", false);
        if (disableScreenshots) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
}
