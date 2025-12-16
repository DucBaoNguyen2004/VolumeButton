package com.example.volumebutton;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private View collapsedView;
    private View expandedMenu;

    private int lastX;
    private int lastY;

    private AudioManager audioManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not binding
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Start Foreground Service (Required for Android O+)
        startForeground();

        // Initialize Audio Manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Inflate layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);

        collapsedView = floatingView.findViewById(R.id.collapsed_iv);
        expandedMenu = floatingView.findViewById(R.id.expanded_menu);

        // WindowManager Layout Params
        // Initially WRAP_CONTENT for just the button
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Initial Position
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingView, params);

        // Setup Touch Listener for Dragging and Clicking
        setupTouchListener();

        // Setup Menu Buttons
        setupMenuButtons();
    }

    private void startForeground() {
        String channelId = "floating_service_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Floating Button Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Assistive Touch")
                .setContentText("Service is running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(1, notification);
    }

    private void setupTouchListener() {
        collapsedView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        int Xdiff = (int) (event.getRawX() - initialTouchX);
                        int Ydiff = (int) (event.getRawY() - initialTouchY);

                        // If the drag was small, treat it as a click
                        if (Xdiff < 10 && Ydiff < 10) {
                            if (isViewCollapsed()) {
                                expandMenu();
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });

        // Tap outside to close
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isViewCollapsed()) {
                    // If menu is expanded and we tap somewhere (that isn't the buttons,
                    // because buttons consume their own touch), we collapse.
                    collapseMenu();
                    return true;
                }
                return false;
            }
        });
    }

    private void setupMenuButtons() {
        Button btnVolUp = floatingView.findViewById(R.id.btn_vol_up);
        Button btnVolDown = floatingView.findViewById(R.id.btn_vol_down);
        Button btnClose = floatingView.findViewById(R.id.btn_close);

        btnVolUp.setOnClickListener(v -> {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        });

        btnVolDown.setOnClickListener(v -> {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        });

        btnClose.setOnClickListener(v -> {
            stopSelf();
        });
    }

    private boolean isViewCollapsed() {
        return collapsedView.getVisibility() == View.VISIBLE;
    }

    private void expandMenu() {
        // Save current position
        lastX = params.x;
        lastY = params.y;

        // Show menu, hide button
        collapsedView.setVisibility(View.GONE);
        expandedMenu.setVisibility(View.VISIBLE);

        // Expand window to full screen to capture outside clicks
        // BUT keep FLAG_NOT_FOCUSABLE so we don't steal key events (like Back button)
        // Note: With FLAG_NOT_FOCUSABLE and MATCH_PARENT, we block touches to
        // everything behind us.
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;

        // When expanded, we might want to center the menu or keep it where the button
        // was.
        // For simplicity of this "AssistiveTouch" clone, often the menu opens in the
        // center.
        // Let's reset gravity to CENTER for the menu mode.
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        windowManager.updateViewLayout(floatingView, params);
    }

    private void collapseMenu() {
        collapsedView.setVisibility(View.VISIBLE);
        expandedMenu.setVisibility(View.GONE);

        // Restore window size to wrap content
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Restore position / gravity
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = lastX;
        params.y = lastY;

        windowManager.updateViewLayout(floatingView, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null)
            windowManager.removeView(floatingView);
    }
}
