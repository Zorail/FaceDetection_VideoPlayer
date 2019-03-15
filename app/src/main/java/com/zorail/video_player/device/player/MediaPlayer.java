package com.zorail.video_player.device.player;

import android.content.Context;

import com.google.android.exoplayer2.ExoPlayer;

interface MediaPlayer {

    void play(String url);

    ExoPlayer getPlayerImpl(Context context);

    void releasePlayer();

    void setMediaSessionState(Boolean isActive);

    void pausePlayer();

    void playPlayer();

    boolean getPlayerState();
}
