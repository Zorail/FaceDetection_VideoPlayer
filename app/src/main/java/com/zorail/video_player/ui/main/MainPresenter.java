package com.zorail.video_player.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.zorail.video_player.data.local.VideosService;
import com.zorail.video_player.data.local.model.Video;
import com.zorail.video_player.ui.video.VideoViewActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;


public class MainPresenter implements MainContract.Presenter {

    private MainContract.View view;

    MainPresenter(MainContract.View view) {
        this.view = view;
    }
    @Override
    public void fetchVideos(Context context) {
        ArrayList<Video> videos = VideosService.getAllVideos(context);
        onVideosFetchedSuccessFully(videos);
    }

    @Override
    public void deactivate() {

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void showVideoScreen(String url) {
        Intent i = new Intent(view.getActivity(),VideoViewActivity.class);
        i.putExtra(VideoViewActivity.VIDEO_URL_EXTRA, url);
        view.getActivity().startActivity(i);
    }

    private void onVideosFetchedSuccessFully(ArrayList<Video> videos) {
        view.renderVideos(videos);
    }

}
