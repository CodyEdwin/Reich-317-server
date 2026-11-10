package com.reich.server;

import java.io.File;
import java.util.List;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

/**
 * The main screen of the application. It shows the state of the server, the
 * controls to start and stop it and the console output of the server.
 */
public class MainActivity extends AppCompatActivity implements ServerRuntime.Listener, ServerLog.Listener {

    /**
     * The request code used to ask for the notification permission.
     */
    private static final int REQUEST_NOTIFICATIONS = 1001;

    /**
     * The maximum amount of lines shown in the console.
     */
    private static final int MAX_CONSOLE_LINES = 400;

    private ServerPreferences preferences;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView portInfo;
    private TextView addressInfo;
    private TextView storageInfo;
    private TextView consoleText;
    private TextView progressText;
    private ProgressBar progressBar;
    private View progressContainer;
    private ScrollView consoleScroll;
    private Button startButton;
    private Button stopButton;
    private TextInputEditText portInput;
    private SwitchMaterial wakeLockSwitch;
    private SwitchMaterial wifiLockSwitch;
    private SwitchMaterial bootSwitch;
    private SwitchMaterial autoStartSwitch;

    /**
     * The last y coordinate of a touch inside the console, used to detect the
     * direction of a swipe.
     */
    private float lastTouchY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ServerLog.install();

        preferences = new ServerPreferences(this);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusTitle = findViewById(R.id.statusTitle);
        statusDetail = findViewById(R.id.statusDetail);
        portInfo = findViewById(R.id.portInfo);
        addressInfo = findViewById(R.id.addressInfo);
        storageInfo = findViewById(R.id.storageInfo);
        consoleText = findViewById(R.id.consoleText);
        progressText = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
        progressContainer = findViewById(R.id.progressContainer);
        consoleScroll = findViewById(R.id.consoleScroll);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        portInput = findViewById(R.id.portInput);
        wakeLockSwitch = findViewById(R.id.wakeLockSwitch);
        wifiLockSwitch = findViewById(R.id.wifiLockSwitch);
        bootSwitch = findViewById(R.id.bootSwitch);
        autoStartSwitch = findViewById(R.id.autoStartSwitch);

        startButton.setOnClickListener(view -> startServer());
        stopButton.setOnClickListener(view -> GameServerService.stop(MainActivity.this));
        findViewById(R.id.clearConsoleButton).setOnClickListener(view -> {
            ServerLog.clear();
            consoleText.setText("");
        });
        findViewById(R.id.latestButton).setOnClickListener(view ->
                consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN)));
        setupConsoleScrolling();
        findViewById(R.id.batteryButton).setOnClickListener(view -> openBatterySettings());
        findViewById(R.id.copyAddressButton).setOnClickListener(view -> copyAddress());

        portInput.setText(String.valueOf(preferences.getPort()));
        wakeLockSwitch.setChecked(preferences.isWakeLockEnabled());
        wifiLockSwitch.setChecked(preferences.isWifiLockEnabled());
        bootSwitch.setChecked(preferences.isStartOnBootEnabled());
        autoStartSwitch.setChecked(preferences.isAutoStartEnabled());

        // The listeners are attached after the initial state has been set, so
        // that restoring the preferences does not write them back.
        wakeLockSwitch.setOnCheckedChangeListener((view, checked) -> preferences.setWakeLockEnabled(checked));
        wifiLockSwitch.setOnCheckedChangeListener((view, checked) -> preferences.setWifiLockEnabled(checked));
        bootSwitch.setOnCheckedChangeListener((view, checked) -> preferences.setStartOnBootEnabled(checked));
        autoStartSwitch.setOnCheckedChangeListener((view, checked) -> preferences.setAutoStartEnabled(checked));

        requestNotificationPermission();
        updateStatus();
        updateAddresses();
        updateStorageInfo();
        updateConsole(ServerLog.getHistory());
        installServerData();

        if (preferences.isAutoStartEnabled() && !ServerRuntime.isRunning()) {
            GameServerService.start(this, preferences.getPort());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ServerRuntime.addListener(this);
        ServerLog.addListener(this);
        updateStatus();
        updateAddresses();
    }

    @Override
    protected void onStop() {
        ServerRuntime.removeListener(this);
        ServerLog.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStateChanged(ServerRuntime.State state, String detail) {
        updateStatus();
    }

    @Override
    public void onLog(ServerLog.Entry entry, List<ServerLog.Entry> history) {
        updateConsole(history);
    }

    /**
     * Refreshes the status card.
     */
    private void updateStatus() {
        ServerRuntime.State state = ServerRuntime.getState();
        String detail = ServerRuntime.getDetail();

        switch (state) {
            case RUNNING:
                statusTitle.setText(R.string.status_running);
                break;
            case STARTING:
                statusTitle.setText(R.string.status_starting);
                break;
            case ERROR:
                statusTitle.setText(R.string.status_error);
                break;
            default:
                statusTitle.setText(R.string.status_stopped);
                break;
        }
        statusDetail.setText(detail);

        boolean running = state == ServerRuntime.State.RUNNING;
        boolean starting = state == ServerRuntime.State.STARTING;
        startButton.setEnabled(!running && !starting);
        stopButton.setEnabled(running || starting);

        portInfo.setText(getString(R.string.port_info, ServerRuntime.getPort()));
        updateAddresses();
    }

    /**
     * Makes the console scroll on its own.
     * <p>
     * The console lives inside the scrollable page, so the page would
     * otherwise swallow every swipe that starts inside the console. The page
     * is only allowed to take over again when the console is dragged towards
     * the edge it is already resting on.
     */
    private void setupConsoleScrolling() {
        consoleScroll.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchY = event.getY();
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_MOVE) {
                float y = event.getY();
                boolean draggingUp = y < lastTouchY;
                boolean atEdge = draggingUp
                        ? !view.canScrollVertically(-1)
                        : !view.canScrollVertically(1);
                if (atEdge) {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                }
                lastTouchY = y;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            // The console itself still handles the gesture.
            return false;
        });
    }

    /**
     * Shows how many characters are saved and where they are stored. The
     * characters are never overwritten by the application, they stay in the
     * private storage of the app until they are deleted by hand.
     */
    private void updateStorageInfo() {
        File directory = new File(AssetInstaller.getDataDirectory(this), "saves/characters");
        File[] files = directory.listFiles();
        int amount = 0;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    amount++;
                }
            }
        }
        storageInfo.setText(getString(R.string.storage_info, amount, directory.getAbsolutePath()));
    }

    /**
     * Refreshes the list of addresses the game client can connect to.
     */
    private void updateAddresses() {
        List<String> addresses = NetworkUtils.getLocalAddresses();
        if (addresses.isEmpty()) {
            addressInfo.setText(R.string.no_address);
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String address : addresses) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(address).append(':').append(ServerRuntime.getPort());
        }
        addressInfo.setText(builder.toString());
    }

    /**
     * Refreshes the console.
     * <p>
     * The console is only scrolled to the bottom when it already was at the
     * bottom, so scrolling up to read something is not interrupted by the
     * messages that keep coming in.
     *
     * @param history every logged line.
     */
    private void updateConsole(List<ServerLog.Entry> history) {
        boolean follow = isConsoleAtBottom();

        if (history.isEmpty()) {
            consoleText.setText("");
            return;
        }
        int from = Math.max(0, history.size() - MAX_CONSOLE_LINES);
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < history.size(); i++) {
            builder.append(history.get(i).format()).append('\n');
        }
        consoleText.setText(builder.toString());

        if (follow) {
            consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /**
     * @return {@code true} if the console is scrolled to the bottom (or if
     * there is nothing to scroll).
     */
    private boolean isConsoleAtBottom() {
        if (consoleScroll.getChildCount() == 0) {
            return true;
        }
        View child = consoleScroll.getChildAt(0);
        int contentHeight = child.getHeight();
        int viewHeight = consoleScroll.getHeight();
        if (contentHeight <= viewHeight) {
            return true;
        }
        return consoleScroll.getScrollY() + viewHeight >= contentHeight - 16;
    }

    /**
     * Starts the server service.
     */
    private void startServer() {
        int port = readPort();
        if (port <= 0 || port > 65535) {
            portInput.setError(getString(R.string.invalid_port));
            return;
        }
        portInput.setError(null);
        preferences.setPort(port);
        ServerRuntime.setPort(port);
        GameServerService.start(this, port);
    }

    /**
     * @return the port the user entered.
     */
    private int readPort() {
        Editable editable = portInput.getText();
        if (editable == null) {
            return preferences.getPort();
        }
        String text = editable.toString().trim();
        if (TextUtils.isEmpty(text)) {
            return preferences.getPort();
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Extracts the server data in the background and shows the progress.
     */
    private void installServerData() {
        progressContainer.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progressText.setText(R.string.preparing_data);

        Thread thread = new Thread(() -> {
            try {
                AssetInstaller.install(MainActivity.this, (percent, message) -> runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    progressText.setText(message);
                }));
            } catch (Throwable t) {
                runOnUiThread(() -> progressText.setText(getString(R.string.data_error, String.valueOf(t))));
            } finally {
                runOnUiThread(() -> {
                    progressContainer.setVisibility(View.GONE);
                    updateStorageInfo();
                });
            }
        }, "ReichDataInstaller");
        thread.start();
    }

    /**
     * Copies the address of the server to the clipboard.
     */
    private void copyAddress() {
        List<String> addresses = NetworkUtils.getLocalAddresses();
        if (addresses.isEmpty()) {
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Reich 317 address",
                addresses.get(0) + ":" + ServerRuntime.getPort()));
    }

    /**
     * Opens the battery optimisation settings so the user can exclude this
     * application from the battery optimisations.
     */
    private void openBatterySettings() {
        Intent request = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        if (request.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(request);
                return;
            } catch (Throwable ignored) {
                // Fall through to the settings list.
            }
        }
        Intent list = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        if (list.resolveActivity(getPackageManager()) != null) {
            startActivity(list);
        }
    }

    /**
     * Asks for the notification permission, it is required to show the
     * foreground service notification on Android 13 and above.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }
}
