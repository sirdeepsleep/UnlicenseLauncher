package unlicense.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.app.KeyguardManager;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LauncherActivity extends Activity {

    public static class ExpandableGridView extends GridView {
        public ExpandableGridView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, expandSpec);
        }
    }

    private boolean ModeInsecure = false;

    private ExpandableGridView gridView;
    private ExpandableGridView workGridView;
    private TextView workAppsHeader;
    private EditText searchEditText;
    private LauncherApps launcherApps;
    private List<LauncherActivityInfo> allApps = new ArrayList<>();
    private List<LauncherActivityInfo> workApps = new ArrayList<>();
    private List<LauncherActivityInfo> displayedApps = new ArrayList<>();
    private LauncherApps.Callback callback;
    private SharedPreferences prefs;
    private FrameLayout rootLayout;

    private static final String APP_PIN_HASH = "search_pin_hash";
    private static final String APP_PIN_SALT = "search_pin_salt";
    private static final String EXCLUDED_APPS_SALT = "excluded_apps_salt";
    private static final String EXCLUDED_APPS_HASHES = "excluded_apps_hashes";

    private boolean isPinMatchMode = false;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager == null || keyguardManager.isKeyguardLocked()) {
            setShowWhenLocked(false);            
        }

        launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        CryptoManager.initKeys();

        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setFocusable(true);
        rootLayout.setFocusableInTouchMode(true);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setFillViewport(true);

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        float density = getResources().getDisplayMetrics().density;
        int topPadding = (int) (44 * density);
        int bottomPadding = (int) (44 * density);
        mainContainer.setPadding((int)(12 * density), topPadding, (int)(12 * density), bottomPadding);
        
        searchEditText = new EditText(this);
        searchEditText.setHint("Search apps...");
        searchEditText.setHintTextColor(Color.LTGRAY);
        searchEditText.setTextColor(Color.WHITE);
        searchEditText.setTextSize(15f);
        searchEditText.setSingleLine(true);
        searchEditText.setPadding((int)(15 * density), (int)(10 * density), (int)(15 * density), (int)(10 * density));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.parseColor("#40000000"));
        searchBg.setCornerRadius(24 * density);
        searchBg.setStroke((int)(1 * density), Color.parseColor("#60FFFFFF"));
        searchEditText.setBackground(searchBg);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, 0, 0, (int)(12 * density));
        searchEditText.setLayoutParams(searchParams);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        gridView = new ExpandableGridView(this);
        gridView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        gridView.setNumColumns(4);
        gridView.setBackgroundColor(Color.TRANSPARENT);
        gridView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        gridView.setClipToPadding(false);
        gridView.setHorizontalSpacing((int) (4 * density));
        gridView.setVerticalSpacing((int) (7 * density));

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (!isPinMatchMode) {
                if (position >= 0 && position < displayedApps.size()) {
                    LauncherActivityInfo info = displayedApps.get(position);
                    launcherApps.startMainActivity(info.getComponentName(), info.getUser(), null, null);
                }
            } else {
                int hCount = displayedApps.size();
                if (position >= 0 && position < hCount) {
                    LauncherActivityInfo info = displayedApps.get(position);
                    launcherApps.startMainActivity(info.getComponentName(), info.getUser(), null, null);
                } else if (position == hCount) {
                    showHideAppsMenu();
                } else if (position == hCount + 1) {
                    showSetSearchPinMenu();
                }
            }
        });

        gridView.setOnItemLongClickListener((parent, view, position, id) -> {
            LauncherActivityInfo info = null;

            if (!isPinMatchMode) {
                if (position < 0 || position >= displayedApps.size()) return true;
                info = displayedApps.get(position);
            } else {
                int hCount = displayedApps.size();
                if (position >= 0 && position < hCount) {
                    info = displayedApps.get(position);
                } else {
                    return true;
                }
            }

            if (info == null) return true;

            final LauncherActivityInfo targetInfo = info;

            PopupMenu popup = new PopupMenu(this, view);
            popup.getMenu().add(0, 1, 0, "App info");

            ApplicationInfo appInfo = targetInfo.getApplicationInfo();
            boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystem = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            if (!isSystem || isUpdatedSystem) {
                popup.getMenu().add(0, 2, 1, "Uninstall");
            }

            String salt = getOrGenerateHiddenAppsSalt();
            String pkgHash = hashPin(appInfo.packageName, salt);
            boolean isExcluded = getHiddenPackageHashes().contains(pkgHash);

            if (isExcluded) {
                popup.getMenu().add(0, 3, 2, "Include app");
            } else {
                popup.getMenu().add(0, 3, 2, "Exclude app");
            }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    launcherApps.startAppDetailsActivity(targetInfo.getComponentName(), targetInfo.getUser(), null, null);
                    return true;
                } else if (item.getItemId() == 2) {
                    String pkg = appInfo.packageName;
                    startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                            android.net.Uri.parse("package:" + pkg)));
                    return true;
                } else if (item.getItemId() == 3) {
                    toggleAppExclusion(appInfo.packageName);
                    return true;
                }
                return false;
            });
            popup.show();
            return true;
        });

        workAppsHeader = new TextView(this);
        workAppsHeader.setText("Work Profile Apps:\n");
        workAppsHeader.setTextColor(Color.LTGRAY);
        workAppsHeader.setTextSize(14f);
        workAppsHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        workAppsHeader.setPadding(0, (int) (16 * density), 0, (int) (8 * density));
        workAppsHeader.setVisibility(View.GONE);

        workGridView = new ExpandableGridView(this);
        workGridView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        workGridView.setNumColumns(4);
        workGridView.setBackgroundColor(Color.TRANSPARENT);
        workGridView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        workGridView.setClipToPadding(false);
        workGridView.setHorizontalSpacing((int) (4 * density));
        workGridView.setVerticalSpacing((int) (7 * density));
        workGridView.setVisibility(View.GONE);

        workGridView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < workApps.size()) {
                LauncherActivityInfo info = workApps.get(position);
                launcherApps.startMainActivity(info.getComponentName(), info.getUser(), null, null);
            }
        });

        workGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= workApps.size()) return true;
            LauncherActivityInfo targetInfo = workApps.get(position);

            PopupMenu popup = new PopupMenu(this, view);
            popup.getMenu().add(0, 1, 0, "App info");

            ApplicationInfo appInfo = targetInfo.getApplicationInfo();
            boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystem = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            if (!isSystem || isUpdatedSystem) {
                popup.getMenu().add(0, 2, 1, "Uninstall");
            }

            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    launcherApps.startAppDetailsActivity(targetInfo.getComponentName(), targetInfo.getUser(), null, null);
                    return true;
                } else if (item.getItemId() == 2) {
                    String pkg = appInfo.packageName;
                    startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                            android.net.Uri.parse("package:" + pkg)));
                    return true;
                }
                return false;
            });
            popup.show();
            return true;
        });

        mainContainer.addView(searchEditText);
        mainContainer.addView(gridView);
        mainContainer.addView(workAppsHeader);
        mainContainer.addView(workGridView);

        scrollView.addView(mainContainer);

        Button bgButton = new Button(this) {
            private final Handler longPressHandler = new Handler();
            private boolean isLongPressTriggered = false;
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    isLongPressTriggered = true;
                    startActivity(new Intent(LauncherActivity.this, SettingsActivity.class));
                }
            };

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                int[] searchLoc = new int[2];
                searchEditText.getLocationOnScreen(searchLoc);
                int searchX = (int) event.getRawX() - searchLoc[0];
                int searchY = (int) event.getRawY() - searchLoc[1];
                if (searchX >= 0 && searchY >= 0 && searchX < searchEditText.getWidth() && searchY < searchEditText.getHeight()) {
                    longPressHandler.removeCallbacks(longPressRunnable);
                    return false;
                }

                int[] gridLoc = new int[2];
                gridView.getLocationOnScreen(gridLoc);
                int gridX = (int) event.getRawX() - gridLoc[0];
                int gridY = (int) event.getRawY() - gridLoc[1];
                if (gridX >= 0 && gridY >= 0 && gridX < gridView.getWidth() && gridY < gridView.getHeight()) {
                    int position = gridView.pointToPosition(gridX, gridY);
                    if (position != AdapterView.INVALID_POSITION) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                        return false;
                    }
                }

                if (workGridView.getVisibility() == View.VISIBLE) {
                    int[] workLoc = new int[2];
                    workGridView.getLocationOnScreen(workLoc);
                    int workX = (int) event.getRawX() - workLoc[0];
                    int workY = (int) event.getRawY() - workLoc[1];
                    if (workX >= 0 && workY >= 0 && workX < workGridView.getWidth() && workY < workGridView.getHeight()) {
                        int position = workGridView.pointToPosition(workX, workY);
                        if (position != AdapterView.INVALID_POSITION) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                            return false;
                        }
                    }
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isLongPressTriggered = false;
                        longPressHandler.postDelayed(longPressRunnable, 700);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacks(longPressRunnable);
                        if (isLongPressTriggered) {
                            return true;
                        }
                        break;
                }
                return super.onTouchEvent(event);
            }
        };

        bgButton.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        bgButton.setBackgroundColor(Color.TRANSPARENT);
        bgButton.setElevation(0f);
        bgButton.setTranslationZ(0f);
        
        rootLayout.addView(scrollView);
        rootLayout.addView(bgButton);

        showMainLauncherLayout();

        callback = new LauncherApps.Callback() {
            @Override public void onPackageAdded(String packageName, android.os.UserHandle user) { loadApps(); }
            @Override public void onPackageRemoved(String packageName, android.os.UserHandle user) { loadApps(); }
            @Override public void onPackageChanged(String packageName, android.os.UserHandle user) { loadApps(); }
            @Override public void onPackagesAvailable(String[] packageNames, android.os.UserHandle user, boolean replacing) { loadApps(); }
            @Override public void onPackagesUnavailable(String[] packageNames, android.os.UserHandle user, boolean replacing) { loadApps(); }
        };

        launcherApps.registerCallback(callback);

        checkAndShowIntroDialog();
    }

    private void showMainLauncherLayout() {        
        setContentView(rootLayout);
        loadApps();
    }

    private void checkAndShowIntroDialog() {
        boolean isIntroRead = prefs.getBoolean("intro_read", false);
        if (!isIntroRead) {
            new AlertDialog.Builder(this)
                    .setTitle("Launcher Instructions")
                    .setMessage("• Long press on free space to open launcher settings\n\n• Long press on apps to open \"App info / Uninstall / Exclude app\" menu\n\n• Excluding means hide app from launcher and search panel\n\n• You can set Search PIN in launcher settings to search hidden apps using it in search panel\n\n• Also, you can disable screenshots and set wallpaper in launcher settings\n\n• Apps from work profile (if you have it) you can find in excluded apps screen")
                    .setPositiveButton("Confirm", (dialog, which) -> {
                        prefs.edit().putBoolean("intro_read", true).commit();
                        dialog.dismiss();
                    })
                    .setCancelable(false)
                    .create();

            Window window = introDialog.getWindow();
            if (window != null) {
                window.setGravity(Gravity.CENTER);
                WindowManager.LayoutParams params = window.getAttributes();
                params.x = 0;
                params.y = 0;
                window.setAttributes(params);
            }

            introDialog.show();
        }
    }

    private String getOrGenerateHiddenAppsSalt() {
        String salt = CryptoManager.getString(prefs, CryptoManager.CE_ALIAS, EXCLUDED_APPS_SALT, "");
        if (salt.isEmpty()) {
            salt = generateSalt();
            CryptoManager.putString(prefs, CryptoManager.CE_ALIAS, EXCLUDED_APPS_SALT, salt);
        }
        return salt;
    }

    private Set<String> getHiddenPackageHashes() {
        String raw = CryptoManager.getString(prefs, CryptoManager.CE_ALIAS, EXCLUDED_APPS_HASHES, "");
        Set<String> set = new HashSet<>();
        if (!raw.isEmpty()) {
            for (String h : raw.split(",")) {
                if (!h.isEmpty()) set.add(h);
            }
        }
        return set;
    }

    private void saveHiddenPackageHashes(Set<String> hashes) {
        StringBuilder sb = new StringBuilder();
        for (String h : hashes) {
            if (sb.length() > 0) sb.append(",");
            sb.append(h);
        }
        CryptoManager.putString(prefs, CryptoManager.CE_ALIAS, EXCLUDED_APPS_HASHES, sb.toString());
    }

    private void toggleAppExclusion(String packageName) {
        String salt = getOrGenerateHiddenAppsSalt();
        String hash = hashPin(packageName, salt);
        Set<String> hiddenHashes = getHiddenPackageHashes();
        if (hiddenHashes.contains(hash)) {
            hiddenHashes.remove(hash);
        } else {
            hiddenHashes.add(hash);
        }
        saveHiddenPackageHashes(hiddenHashes);
        loadApps();
    }

    private Button createStyledButton(String text, float density) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.BLACK);
        btnBg.setCornerRadius(8 * density);
        btnBg.setStroke((int)(1 * density), Color.parseColor("#333333"));
        button.setBackground(btnBg);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins((int)(8 * density), 0, (int)(8 * density), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void showHideAppsMenu() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        
        float density = getResources().getDisplayMetrics().density;
        int finalPadding = (int) (44 * density);
        root.setPadding((int)(15 * density), finalPadding, (int)(15 * density), finalPadding);
        root.setClipToPadding(false);

        ListView listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        listView.setBackgroundColor(Color.BLACK);
        listView.setDivider(null);

        final String salt = getOrGenerateHiddenAppsSalt();
        final Set<String> hiddenHashes = getHiddenPackageHashes();

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return allApps.size();
            }

            @Override
            public Object getItem(int position) {
                return allApps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout rowLayout = new LinearLayout(LauncherActivity.this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setGravity(Gravity.CENTER_VERTICAL);
                int verticalPadding = (int) (8 * density);
                rowLayout.setPadding(0, verticalPadding, 0, verticalPadding);

                LauncherActivityInfo info = allApps.get(position);
                String pkg = info.getApplicationInfo().packageName;
                String pkgHash = hashPin(pkg, salt);

                ImageView icon = new ImageView(LauncherActivity.this);
                int iconSize = (int) (36 * density);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconParams.setMargins(0, 0, (int) (12 * density), 0);
                icon.setLayoutParams(iconParams);
                icon.setImageDrawable(info.getIcon(0));

                TextView textView = new TextView(LauncherActivity.this);
                textView.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                textView.setText(info.getLabel());
                textView.setTextColor(Color.WHITE);
                textView.setTextSize(15f);
                textView.setSingleLine(true);

                CheckBox checkBox = new CheckBox(LauncherActivity.this);
                checkBox.setChecked(hiddenHashes.contains(pkgHash));
                checkBox.setClickable(false);
                checkBox.setFocusable(false);

                rowLayout.addView(icon);
                rowLayout.addView(textView);
                rowLayout.addView(checkBox);

                rowLayout.setOnClickListener(v -> {
                    boolean newState = !checkBox.isChecked();
                    checkBox.setChecked(newState);
                    if (newState) {
                        hiddenHashes.add(pkgHash);
                    } else {
                        hiddenHashes.remove(pkgHash);
                    }
                });

                return rowLayout;
            }
        });

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLayoutParams.setMargins(0, (int)(12 * density), 0, 0);
        btnLayout.setLayoutParams(btnLayoutParams);

        Button backButton = createStyledButton("Back", density);
        backButton.setOnClickListener(v -> showMainLauncherLayout());

        Button saveButton = createStyledButton("Exclude selected", density);
        saveButton.setOnClickListener(v -> {
            saveHiddenPackageHashes(hiddenHashes);
            if (searchEditText != null) {
                searchEditText.setText("");
            }
            showMainLauncherLayout();
        });

        btnLayout.addView(backButton);
        btnLayout.addView(saveButton);

        root.addView(listView);
        root.addView(btnLayout);

        setContentView(root);
    }

    private void showSetSearchPinMenu() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        
        float density = getResources().getDisplayMetrics().density;
        int finalPadding = (int) (44 * density);
        root.setPadding((int)(24 * density), finalPadding, (int)(24 * density), finalPadding);

        TextView title = new TextView(this);
        title.setText("Set Search PIN Code");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter PIN to display excluded apps when searched:");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, (int)(8 * density), 0, (int)(15 * density));
        subtitle.setLayoutParams(subParams);

        final EditText pinInput = new EditText(this);
        pinInput.setHint("Enter PIN");
        pinInput.setHintTextColor(Color.WHITE);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setGravity(Gravity.CENTER);
        
        GradientDrawable pinBg = new GradientDrawable();
        pinBg.setColor(Color.parseColor("#222222"));
        pinBg.setCornerRadius(8 * density);
        pinInput.setBackground(pinBg);
        pinInput.setPadding((int)(12 * density), (int)(12 * density), (int)(12 * density), (int)(12 * density));

        LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        pinParams.setMargins(0, 0, 0, (int)(20 * density));
        pinInput.setLayoutParams(pinParams);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        btnLayout.setGravity(Gravity.CENTER);        

        Button backBtn = createStyledButton("Back", density);
        backBtn.setOnClickListener(v -> showMainLauncherLayout());

        Button saveBtn = createStyledButton("Save PIN", density);
        saveBtn.setOnClickListener(v -> {
            String pin = pinInput.getText().toString().trim();
            if (pin.length() >= 4) {
                String salt = generateSalt();
                String hash = hashPin(pin, salt);
                CryptoManager.putString(prefs, CryptoManager.CE_ALIAS, APP_PIN_SALT, salt);
                CryptoManager.putString(prefs, CryptoManager.CE_ALIAS, APP_PIN_HASH, hash);
                if (searchEditText != null) {
                    searchEditText.setText("");
                }
                showMainLauncherLayout();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (launcherApps != null && callback != null) {
            launcherApps.unregisterCallback(callback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguardManager == null || keyguardManager.isKeyguardLocked()) {
            setShowWhenLocked(false);            
        }

        boolean disableScreenshots = prefs.getBoolean("disable_screenshots", false);
        if (disableScreenshots) {
            ModeInsecure = false;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            ModeInsecure = true;
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
        
        loadApps();
    }

    private void loadApps() {
        if (launcherApps == null) return;
        String myPackage = getPackageName();
        allApps = new ArrayList<>();
        workApps = new ArrayList<>();

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        UserHandle myUser = android.os.Process.myUserHandle();

        if (userManager != null) {
            List<UserHandle> profiles = userManager.getUserProfiles();
            for (UserHandle user : profiles) {
                List<LauncherActivityInfo> list = launcherApps.getActivityList(null, user);
                if (list != null) {
                    for (LauncherActivityInfo info : list) {
                        String pkg = info.getApplicationInfo().packageName;
                        if (!pkg.equals(myPackage)) {
                            if (user.equals(myUser)) {
                                allApps.add(info);
                            } else {
                                workApps.add(info);
                            }
                        }
                    }
                }
            }
        }
        filterApps(searchEditText != null ? searchEditText.getText().toString() : "");
    }

    private void filterApps(String query) {
        String hiddenSalt = getOrGenerateHiddenAppsSalt();
        Set<String> hiddenHashes = getHiddenPackageHashes();
        String storedSalt = CryptoManager.getString(prefs, CryptoManager.CE_ALIAS, APP_PIN_SALT, "");
        String storedHash = CryptoManager.getString(prefs, CryptoManager.CE_ALIAS, APP_PIN_HASH, "");

        displayedApps.clear();

        boolean isPinMatch = false;
        if (!query.isEmpty() && !storedHash.isEmpty()) {
            String inputHash = hashPin(query, storedSalt);
            if (inputHash.equals(storedHash)) {
                isPinMatch = true;
            }
        }

        isPinMatchMode = isPinMatch;
        rootLayout.setBackgroundColor(isPinMatch ? Color.BLACK : Color.TRANSPARENT);
        if (ModeInsecure) {
            if (isPinMatch) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        if (isPinMatch) {
            for (LauncherActivityInfo info : allApps) {
                String pkg = info.getApplicationInfo().packageName;
                String pkgHash = hashPin(pkg, hiddenSalt);
                if (hiddenHashes.contains(pkgHash)) {
                    displayedApps.add(info);
                }
            }
        } else {
            String lowercaseQuery = query.toLowerCase().trim();
            for (LauncherActivityInfo info : allApps) {
                String pkg = info.getApplicationInfo().packageName;
                String pkgHash = hashPin(pkg, hiddenSalt);
                if (!hiddenHashes.contains(pkgHash)) {
                    if (lowercaseQuery.isEmpty() || info.getLabel().toString().toLowerCase().contains(lowercaseQuery)) {
                        displayedApps.add(info);
                    }
                }
            }
        }

        runOnUiThread(() -> {
            gridView.setAdapter(new AppAdapter());

            if (isPinMatchMode && !workApps.isEmpty()) {
                workAppsHeader.setVisibility(View.VISIBLE);
                workGridView.setVisibility(View.VISIBLE);
                workGridView.setAdapter(new WorkAppAdapter());
            } else {
                workAppsHeader.setVisibility(View.GONE);
                workGridView.setVisibility(View.GONE);
            }
        });
    }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.decode(salt, Base64.NO_WRAP));
            byte[] hash = md.digest(pin.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ModeInsecure = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);        
        if (searchEditText != null) {
            searchEditText.setText("");
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override 
        public int getCount() { 
            if (isPinMatchMode) {
                return displayedApps.size() + 2;
            }
            return displayedApps.size(); 
        }

        @Override 
        public Object getItem(int position) { 
            if (!isPinMatchMode) {
                if (position < displayedApps.size()) {
                    return displayedApps.get(position);
                }
                return null;
            }
            int hCount = displayedApps.size();
            if (position < hCount) {
                return displayedApps.get(position);
            }
            return null;
        }
        
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout = new LinearLayout(LauncherActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);            

            ImageView icon = new ImageView(LauncherActivity.this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(140, 140));

            TextView text = new TextView(LauncherActivity.this);
            text.setTextColor(Color.WHITE);
            text.setGravity(Gravity.CENTER_HORIZONTAL);
            text.setSingleLine(true);
            text.setHorizontallyScrolling(false);

            text.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.setMargins(0, (int)(4 * densityMultiplier()), 0, 0);
            text.setLayoutParams(textParams);

            if (!isPinMatchMode) {
                if (position < displayedApps.size()) {
                    LauncherActivityInfo info = displayedApps.get(position);
                    icon.setImageDrawable(info.getIcon(0));
                    text.setText(info.getLabel());
                }
            } else {
                int hCount = displayedApps.size();
                if (position < hCount) {
                    LauncherActivityInfo info = displayedApps.get(position);
                    icon.setImageDrawable(info.getIcon(0));
                    text.setText(info.getLabel());
                } else if (position == hCount) {                
                    icon.setImageResource(android.R.drawable.ic_menu_preferences);
                    text.setText("Manage excluded apps");
                } else if (position == hCount + 1) {                
                    icon.setImageResource(android.R.drawable.ic_lock_lock);
                    text.setText("Change search PIN");
                }
            }

            layout.addView(icon);
            layout.addView(text);
            return layout;
        }
    }

    private class WorkAppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return workApps.size();
        }

        @Override
        public Object getItem(int position) {
            if (position >= 0 && position < workApps.size()) {
                return workApps.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout = new LinearLayout(LauncherActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);

            ImageView icon = new ImageView(LauncherActivity.this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(140, 140));

            TextView text = new TextView(LauncherActivity.this);
            text.setTextColor(Color.WHITE);
            text.setGravity(Gravity.CENTER_HORIZONTAL);
            text.setSingleLine(true);
            text.setHorizontallyScrolling(false);

            text.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.setMargins(0, (int)(4 * densityMultiplier()), 0, 0);
            text.setLayoutParams(textParams);

            if (position >= 0 && position < workApps.size()) {
                LauncherActivityInfo info = workApps.get(position);
                Drawable badgedIcon = getPackageManager().getUserBadgedIcon(info.getIcon(0), info.getUser());
                icon.setImageDrawable(badgedIcon);
                text.setText(info.getLabel());
            }

            layout.addView(icon);
            layout.addView(text);
            return layout;
        }
    }

    private float densityMultiplier() {
        return getResources().getDisplayMetrics().density;
    }
}
