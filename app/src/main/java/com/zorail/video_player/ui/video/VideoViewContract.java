package com.zorail.video_player.ui.video;


import android.app.Activity;
import android.content.Context;

import com.zorail.video_player.device.player.MediaPlayerImpl;

interface VideoViewContract {

    interface Presenter {
        void deactivate();

        MediaPlayerImpl getPlayer();

        void camera_use(Context context, Activity activity);

        void closeCamera();

        void play(String url);

        void releasePlayer();

        void setMediaSessionState(Boolean isActive);
    }

    interface View {
        void switch_use();
    }
}
