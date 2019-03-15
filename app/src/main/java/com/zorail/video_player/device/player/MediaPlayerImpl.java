package com.zorail.video_player.device.player;

import android.content.Context;
import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.zorail.video_player.R;

public class MediaPlayerImpl implements MediaPlayer {

    private final String TAG = "MediaPlayerTag";

    private ExoPlayer exoPlayer;
    private Context context;
    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;

    @Override
    public void play(String url) {
        String userAgent = Util.getUserAgent(context, context.getString(R.string.app_name));

        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(context, userAgent))
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(Uri.parse(url));

        exoPlayer.prepare(mediaSource);

        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    public ExoPlayer getPlayerImpl(Context context) {
        this.context = context;
        initializePlayer();
        initializeMediaSession();
        return exoPlayer;
    }

    @Override
    public void releasePlayer() {
        exoPlayer.stop();
        exoPlayer.release();
    }

    @Override
    public void setMediaSessionState(Boolean isActive) {
        mediaSession.setActive(isActive);
    }

    @Override
    public void pausePlayer() {
        exoPlayer.setPlayWhenReady(false);
    }

    @Override
    public void playPlayer() {
        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    public boolean getPlayerState() {
        return exoPlayer.getPlayWhenReady();
    }

    private void initializePlayer() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector();
        DefaultLoadControl loadControl = new DefaultLoadControl();
        DefaultRenderersFactory rendersFactory = new DefaultRenderersFactory(context);

        exoPlayer = ExoPlayerFactory.newSimpleInstance(rendersFactory, trackSelector, loadControl);
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setMediaButtonReceiver(null);

        stateBuilder = new PlaybackStateCompat.Builder()
                    .setActions(
                            PlaybackStateCompat.ACTION_PLAY |
                            PlaybackStateCompat.ACTION_PAUSE |
                            PlaybackStateCompat.ACTION_PLAY_PAUSE |
                            PlaybackStateCompat.ACTION_FAST_FORWARD |
                            PlaybackStateCompat.ACTION_REWIND
                    );
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setCallback(new SessionCallback());

        mediaSession.setActive(true);
    }

    private class SessionCallback extends MediaSessionCompat.Callback {
        private int SEEK_WINDOW_MILLIS = 10000;

        @Override
        public void onPlay() {
            exoPlayer.setPlayWhenReady(true);
        }

        @Override
        public void onPause() {
            exoPlayer.setPlayWhenReady(false);
        }

        @Override
        public void onRewind() {
            exoPlayer.seekTo(exoPlayer.getCurrentPosition() - SEEK_WINDOW_MILLIS);
        }

        @Override
        public void onFastForward() {
            exoPlayer.seekTo(exoPlayer.getContentPosition() + SEEK_WINDOW_MILLIS);
        }
    }

}
