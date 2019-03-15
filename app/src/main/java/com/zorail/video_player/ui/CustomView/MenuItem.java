package com.zorail.video_player.ui.CustomView;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

import com.zorail.video_player.R;

public class MenuItem extends RelativeLayout {
    LayoutInflater mInflater;
    public MenuItem(Context context) {
        super(context);
        mInflater = LayoutInflater.from(context);
        init();
    }

    public MenuItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInflater = LayoutInflater.from(context);
        init();
    }

    public MenuItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mInflater = LayoutInflater.from(context);
        init();
    }

    public void init() {
        mInflater.inflate(R.layout.toggle_switch, this, true);

    }
}
