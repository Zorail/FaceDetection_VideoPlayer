package com.zorail.video_player.ui.main;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

import com.zorail.video_player.R;
import com.zorail.video_player.data.local.model.Video;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class MainAdapter extends RecyclerView.Adapter<MainAdapter.VideoViewHolder> {

    private ArrayList<Video> videos = new ArrayList<>();
    private Subject<Video> onVideoClickSubject = BehaviorSubject.create();

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.video_item_view, parent, false);
        return new VideoViewHolder(itemView, onVideoClickSubject);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        holder.setVideo(videos.get(position));
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    void onVideosUpdate(ArrayList<Video> videos) {
        this.videos = videos;
        notifyDataSetChanged();
    }

    Observable<Video> onItemClick() {
        Long CLICK_THROTTLE_WINDOW_MILLIS = 300L;
        return onVideoClickSubject.throttleFirst(CLICK_THROTTLE_WINDOW_MILLIS, TimeUnit.MILLISECONDS);
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {

        private LinearLayout main_video_item_container;
        private TextView tv_main_video_title;
        private Video video;
        private ImageView tv_main_video_thumbnail;
        private Subject<Video> clickSubject;

        VideoViewHolder(View itemView, Subject<Video> clickSubject) {
            super(itemView);
            this.clickSubject = clickSubject;
            tv_main_video_title = itemView.findViewById(R.id.tv_main_video_title);
            tv_main_video_thumbnail=itemView.findViewById(R.id.thumbnail);
            main_video_item_container = itemView.findViewById(R.id.main_video_item_container);
        }

        private void setVideo(Video video) {
            this.video = video;
//            this.tv_main_video_thumbnail.setImageBitmap(video.getThumbnail());
            this.tv_main_video_title.setText(video.getDisplayName());
            this.main_video_item_container.setOnClickListener(view -> onMovieClick());
        }

        private void onMovieClick() {
           clickSubject.onNext(video);
        }
    }
}
