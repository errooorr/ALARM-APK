package ru.genrih.alarm;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlarmActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 30, 30, 30);
        root.setBackgroundColor(Color.rgb(16, 23, 37));
        TextView title = new TextView(this);
        title.setText("⏰ ПОДЪЁМ!\n\nГенрих звонит!");
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.WHITE);
        title.setTextSize(36);
        root.addView(title);
        Button dismiss = new Button(this);
        dismiss.setText("ВЫКЛЮЧИТЬ БУДИЛЬНИК");
        dismiss.setAllCaps(false);
        dismiss.setTextSize(18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = 80;
        root.addView(dismiss, lp);
        dismiss.setOnClickListener(v -> {
            stopService(new Intent(this, AlarmService.class));
            finish();
        });
        setContentView(root);
    }
}
