package com.zorail.video_player.data.local;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;

import com.zorail.video_player.data.local.api.VideosFetcher;
import com.zorail.video_player.data.local.model.Video;

import java.util.ArrayList;
import java.util.List;

public class VideosService {

    public static ArrayList<Video> getAllVideos(Context context) {
        ArrayList<List<String>> videoLists = VideosFetcher.getAllVideoData(context);

        ArrayList<Video> videoObjects = new ArrayList<>();
        for(List e: videoLists) {
//            Bitmap image = ThumbnailUtils.createVideoThumbnail(e.get(2).toString(),MediaStore.Video.Thumbnails.MICRO_KIND);
            Video video = new Video(
                    e.get(0).toString(),
                    Integer.parseInt(e.get(1).toString()),
                    e.get(2).toString()
            );
//            video.setThumbnail(image);
            videoObjects.add(video);
        }

        return videoObjects;
    }
}
