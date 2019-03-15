package com.zorail.video_player.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.zorail.video_player.R;
import com.zorail.video_player.data.local.model.Video;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements MainContract.View{

    private MainContract.Presenter presenter;
    private MainAdapter videosAdapter;
    private RecyclerView videosList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(isReadStoragePermissionGranted()) {
            init();
        }
    }

    @Override
    public Context getActivity() {
        return MainActivity.this;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.deactivate();
    }

    @Override
    public void renderVideos(ArrayList<Video> videos) {
        videosAdapter.onVideosUpdate(videos);
    }

    @Override
    public void showErrorMessage() {

    }

    private void init() {
        videosList = findViewById(R.id.rv_videos);

        initializeRecyclerView();
        presenter = new MainPresenter(this);
        presenter.fetchVideos(getApplicationContext());
    }

    private void initializeRecyclerView() {
        videosList.setLayoutManager(new LinearLayoutManager(this));
        videosList.setHasFixedSize(true);

        videosAdapter = new MainAdapter();
        videosAdapter.onItemClick().subscribe(this::onVideoItemClick);
        videosList.setAdapter(videosAdapter);
    }

    private void onVideoItemClick(Video video) {
        presenter.showVideoScreen(video.getPath());
    }

    private Boolean isReadStoragePermissionGranted() {
        if(Build.VERSION.SDK_INT >= 23) {
            if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("TAG", "Permission is Granted");
                return true;
            } else {
                Log.v("TAG", "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else {
            Log.v("TAG", "Permission is granted1");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                Log.d("TAG", "External Storage");
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v("TAG","Permission: "+permissions[0]+ "was "+grantResults[0]);
                    init();
                }
                break;
        }
    }
}
