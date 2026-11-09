package com.kidslock.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.FrameLayout;

public class LockOverlayView extends FrameLayout {

    public LockOverlayView(Context context) {
        super(context);
        init(context);
    }

    public LockOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.overlay_lock, this, true);
        Button btn = findViewById(R.id.btnOverlayUnlock);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                // 点击后打开锁屏答题界面
                android.content.Intent intent = new android.content.Intent(context, LockScreenActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            });
        }
    }
}