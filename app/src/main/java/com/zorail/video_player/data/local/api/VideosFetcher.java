package com.zorail.video_player.data.local.api;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class VideosFetcher {

    public static ArrayList<List<String>> getAllVideoData(Context context) {
        HashSet<List<String>> videoItemHashSet = new HashSet<>();
        String[] projection = { MediaStore.Video.VideoColumns.DATA ,MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.VideoColumns.DURATION, MediaStore.Video.Media.DATA };
        Cursor cursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);
        try {
            assert cursor != null;
            cursor.moveToFirst();
            do{
                List<String> videoData = new ArrayList<>();
                videoData.add(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)));
                videoData.add(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)));
                videoData.add(cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)));
                videoItemHashSet.add(videoData);
            }while(cursor.moveToNext());

            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>(videoItemHashSet);
    }
}
