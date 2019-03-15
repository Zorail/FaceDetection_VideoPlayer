package com.zorail.video_player.ui.main;

import android.content.Context;

import com.zorail.video_player.data.local.model.Video;

import java.util.ArrayList;

interface MainContract {

    interface Presenter {

        void fetchVideos(Context context);

        void deactivate();

        void showVideoScreen(String url);

    }

    interface View {
        void renderVideos(ArrayList<Video> videos);

        Context getActivity();

        void showErrorMessage();
    }
}
