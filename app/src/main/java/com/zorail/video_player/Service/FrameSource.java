package com.zorail.video_player.Service;

import android.app.Activity;
import android.media.Image;

public interface FrameSource {
    void onReceiveImage(Image image, Activity activity);
}
