package com.apkforge.runtime;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;

public class ControleIaService extends AccessibilityService {
    private static final String CHANNEL_ID = "controle_ia_active";
    private static final int NOTIFICATION_ID = 4412;
    private static volatile ControleIaService instance;

    private boolean controlling;
    private boolean paused;
    private LinearLayout overlay;
    private WindowManager windowManager;
    private String lastSnapshot = "";

    public static ControleIaService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        createNotificationChannel();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!controlling || event == null || rootInActiveWindow == null) return;
        lastSnapshot = collectText(rootInActiveWindow, 0).trim();
    }

    @Override public void onInterrupt() { pauseControl(); }

    @Override public void onDestroy() {
        stopControl();
        instance = null;
        super.onDestroy();
    }

    public void startControl() {
        controlling = true;
        paused = false;
        showNotification();
        showOverlay();
    }

    public void pauseControl() {
        if (!controlling) return;
        paused = true;
        showNotification();
        updateOverlay();
    }

    public void resumeControl() {
        if (!controlling) return;
        paused = false;
        showNotification();
        updateOverlay();
    }

    public void stopControl() {
        controlling = false;
        paused = false;
        lastSnapshot = "";
        removeOverlay();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    public String getScreenSnapshot() {
        if (!lastSnapshot.isEmpty()) return lastSnapshot;
        return rootInActiveWindow == null ? "" : collectText(rootInActiveWindow, 0).trim();
    }

    public boolean clickText(String target) {
        if (!controlling || paused || target == null || target.trim().isEmpty()) return false;
        AccessibilityNodeInfo node = findNode(rootInActiveWindow, target);
        if (node == null) return false;
        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 8 && current != null; i++, current = current.getParent()) {
            if (current.isClickable()) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    public boolean typeText(String text) {
        if (!controlling || paused || rootInActiveWindow == null) return false;
        AccessibilityNodeInfo field = rootInActiveWindow.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (field == null) field = findEditable(rootInActiveWindow);
        if (field == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text == null ? "" : text);
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    public boolean scrollDown() {
        AccessibilityNodeInfo node = findScrollable(rootInActiveWindow);
        return !paused && controlling && node != null && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    private String collectText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 24) return "";
        String own = ((node.getText() == null ? "" : node.getText().toString()) + " " +
                (node.getContentDescription() == null ? "" : node.getContentDescription().toString())).trim();
        StringBuilder result = new StringBuilder(own);
        for (int i = 0; i < node.getChildCount(); i++) {
            String child = collectText(node.getChild(i), depth + 1);
            if (!child.isEmpty()) result.append('\n').append(child);
        }
        return result.toString();
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String target) {
        if (node == null) return null;
        String value = ((node.getText() == null ? "" : node.getText().toString()) + " " +
                (node.getContentDescription() == null ? "" : node.getContentDescription().toString())).toLowerCase();
        if (value.contains(target.toLowerCase().trim())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findNode(node.getChild(i), target);
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findEditable(node.getChild(i));
            if (match != null) return match;
        }
        return null;
    }

    private AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isScrollable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findScrollable(node.getChild(i));
            if (match != null) return match;
        }
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Controle IA ativo", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Indica quando o Controle IA está ativo");
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void showNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Controle IA")
                .setContentText(paused ? "Controle pausado" : "Controle de tela disponível")
                .setOngoing(true);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setPadding(12, 8, 12, 8);
        view.setBackgroundColor(Color.argb(235, 15, 23, 42));
        Button pause = new Button(this);
        pause.setText("II");
        pause.setTextColor(Color.WHITE);
        pause.setOnClickListener(v -> { if (paused) resumeControl(); else pauseControl(); });
        Button stop = new Button(this);
        stop.setText("PARAR");
        stop.setTextColor(Color.rgb(254, 202, 202));
        stop.setOnClickListener(v -> stopControl());
        view.addView(pause);
        view.addView(stop);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.BOTTOM;
        params.x = 18;
        params.y = 110;
        try { windowManager.addView(view, params); overlay = view; } catch (Exception ignored) { overlay = null; }
    }

    private void updateOverlay() {
        if (overlay != null && overlay.getChildCount() > 0 && overlay.getChildAt(0) instanceof Button) {
            ((Button) overlay.getChildAt(0)).setText(paused ? ">" : "II");
        }
    }

    private void removeOverlay() {
        if (overlay == null) return;
        try { if (windowManager != null) windowManager.removeView(overlay); } catch (Exception ignored) {}
        overlay = null;
        windowManager = null;
    }
}
