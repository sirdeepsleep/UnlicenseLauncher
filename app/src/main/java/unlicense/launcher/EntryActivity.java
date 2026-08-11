package unlicense.launcher;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.Gravity;
import android.app.KeyguardManager;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.provider.Settings;

public class EntryActivity extends Activity {
    
    private AlertDialog dialog;

    @Override
    protected void onResume() {
        super.onResume();
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager == null || keyguardManager.isKeyguardLocked()) {
            setShowWhenLocked(false);
            finish();
            return;
        }        
        if (isDefault()) {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
            if (dialog == null || !dialog.isShowing()) {
                dialog = new AlertDialog.Builder(this)
                        .setMessage("Please set launcher as default")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));                            
                        })
                        .setCancelable(false)
                        .create();

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setGravity(Gravity.CENTER);
                    WindowManager.LayoutParams params = window.getAttributes();
                    params.x = 0;
                    params.y = 0;
                    window.setAttributes(params);
                }

                dialog.show();              
            }
        }
    }

    private boolean isDefault() {
        RoleManager rm = getSystemService(RoleManager.class);
        return rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME);
    }
}
