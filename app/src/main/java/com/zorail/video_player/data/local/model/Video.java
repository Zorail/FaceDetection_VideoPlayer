package com.zorail.video_player.data.local.model;

import android.graphics.Bitmap;

public class Video {

    private String displayName;
    private int duration;
    private Bitmap thumbnail;
    private String path;

    public Video(String displayName, int duration, String path) {
        this.displayName = displayName;
        this.duration = duration;
        this.path = path;
    }

    @Override
    public String toString() {
        return "Video{" +
                "displayName='" + displayName + '\'' +
                ", duration=" + duration +
                '}';
    }

    public String getPath() {
        return path;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
