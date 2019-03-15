package com.zorail.video_player.ui.video;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Switch;

import com.google.android.exoplayer2.ui.PlayerView;
import com.zorail.video_player.R;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)

public class VideoViewActivity extends AppCompatActivity implements VideoViewContract.View {

    private PlayerView videoView;
    private VideoViewContract.Presenter presenter;
    Switch aSwitch;

    private View itemChooser;

    public static final String VIDEO_URL_EXTRA = "video_url_extra";
    private static final String STATE_RESUME_WINDOW = "resumeWindow";
    private static final String STATE_RESUME_POSITION = "resumePosition";
    private static final String STATE_PLAYER_FULLSCREEN = "playerFullscreen";

    private int mResumeWindow;
    private long mResumePosition;
    private boolean mExoPlayerFullscreen = false;

//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_view);
        presenter=new VideoViewPresenter(this, this);
//        mWorkerResultReceiver = new WorkerResultReceiver(new Handler());
//        mWorkerResultReceiver.setReceiver(this);

        aSwitch=findViewById(R.id.switch_toggle);

        if(savedInstanceState != null) {
            mResumeWindow = savedInstanceState.getInt(STATE_RESUME_WINDOW);
            mResumePosition = savedInstanceState.getLong(STATE_RESUME_POSITION);
            mExoPlayerFullscreen = savedInstanceState.getBoolean(STATE_PLAYER_FULLSCREEN);
        }
    }

//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onResume() {
        super.onResume();

        if(videoView == null) {

        }
        init();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            presenter.releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            presenter.releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        presenter.deactivate();
        presenter.setMediaSessionState(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putInt(STATE_RESUME_WINDOW, mResumeWindow);
        outState.putLong(STATE_RESUME_POSITION, mResumePosition);
        outState.putBoolean(STATE_PLAYER_FULLSCREEN, mExoPlayerFullscreen);

        super.onSaveInstanceState(outState);
    }

    private Boolean isReadStoragePermissionGranted() {
        if(Build.VERSION.SDK_INT >= 23) {
            if(checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("TAG", "Permission is Granted");
                return true;
            } else {
                Log.v("TAG", "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.CAMERA}, 1);
                return false;
            }
        } else {
            Log.v("TAG", "Permission is granted1");
            return true;
        }
    }

    private void init() {

        String videoUrl = getIntent().getStringExtra(VIDEO_URL_EXTRA);

        videoView = findViewById(R.id.ep_video_view);

        videoView.setPlayer(presenter.getPlayer().getPlayerImpl(this));

        presenter.play(videoUrl);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.menu,menu);
        MenuItem item = menu.findItem(R.id.switch_);
        aSwitch = item.getActionView().findViewById(R.id.switch_toggle);
        aSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if(checked) {
                if (isReadStoragePermissionGranted()) {
                    presenter.camera_use(getApplicationContext(), VideoViewActivity.this);
                } else {
                    isReadStoragePermissionGranted();
                }
            } else {
                presenter.closeCamera();
            }
        });
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.switch_:
                switch_use();
                return true;

            default: return super.onOptionsItemSelected(item);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode) {
            case 1: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    presenter.camera_use(getApplicationContext(), VideoViewActivity.this);
                } else {
                    aSwitch.setChecked(false);
                }
            }
            return;
        }
    }

    @Override
    public void switch_use() {
//        aSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            if(isChecked){
////                startCameraFromBackground(VideoViewActivity.this, mWorkerResultReceiver);
//                presenter.camera_use(getApplicationContext());
//            } else {
//                presenter.closeCamera();
//            }
//
//        });
    }

}
