package com.winlator.cmod.core;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.winlator.cmod.R;

public class PreloaderDialog {
    private final Activity activity;
    private Dialog dialog;

    public PreloaderDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        if (dialog != null) return;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.preloader_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }

        android.widget.LinearLayout container = dialog.findViewById(R.id.LLContent);
        if (container != null) {
            float density = activity.getResources().getDisplayMetrics().density;
            com.winlator.cmod.widget.ChasingBorderDrawable animatedBorder = new com.winlator.cmod.widget.ChasingBorderDrawable(16f, 1.5f, density);
            
            android.graphics.drawable.GradientDrawable solidBg = new android.graphics.drawable.GradientDrawable();
            solidBg.setColor(android.graphics.Color.parseColor("#171A1C"));
            solidBg.setCornerRadius(16f * density);
            
            android.graphics.drawable.Drawable[] layers = {solidBg, animatedBorder};
            android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(layers);
            container.setBackground(layerDrawable);
        }
    }

    public synchronized void show(int textResId) {
        if (dialog == null) create();
        ((TextView)dialog.findViewById(R.id.TextView)).setText(textResId);
        
        android.widget.ProgressBar pb = dialog.findViewById(R.id.ProgressBar);
        if (pb != null) {
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00BDD8")));
        }

        if (!isShowing()) dialog.show();
    }

    public synchronized void show(String text) {
        if (dialog == null) create();
        ((TextView)dialog.findViewById(R.id.TextView)).setText(text);
        
        android.widget.ProgressBar pb = dialog.findViewById(R.id.ProgressBar);
        if (pb != null) {
            boolean isComplete = text.contains("(100%)");
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor(isComplete ? "#2E7D32" : "#00BDD8")
            ));
        }

        if (!isShowing()) dialog.show();
    }

    public void showOnUiThread(final int textResId) {
        activity.runOnUiThread(() -> show(textResId));
    }

    public void showOnUiThread(final String text) {
        activity.runOnUiThread(() -> show(text));
    }

    public synchronized void close() {
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        }
        catch (Exception e) {}
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
