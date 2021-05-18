package com.jeejio.exoplayersurfaceview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    protected ExoPlayerView playerView;
    protected SimpleExoPlayer player;
    private MediaItem mediaItem;
    private DataSource.Factory dataSourceFactory;
    private DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters trackSelectorParameters;

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        dataSourceFactory = DemoUtil.getDataSourceFactory(this);
        setContentView(R.layout.activity_main);

        DefaultTrackSelector.ParametersBuilder builder =
                new DefaultTrackSelector.ParametersBuilder(this);
        trackSelectorParameters = builder.build();

        playerView = findViewById(R.id.player_view);
        playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
        playerView.requestFocus();
    }

    protected boolean initializePlayer() {
        if (player == null) {
            mediaItem = createMediaItem();

            RenderersFactory renderersFactory = DemoUtil.buildRenderersFactory(this,false);
            MediaSourceFactory mediaSourceFactory =
                    new DefaultMediaSourceFactory(dataSourceFactory);

            trackSelector = new DefaultTrackSelector(this);
            trackSelector.setParameters(trackSelectorParameters);
            player = new SimpleExoPlayer.Builder(this, renderersFactory)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .setTrackSelector(trackSelector)
                            .build();
            player.addListener(new PlayerEventListener());
            player.addAnalyticsListener(new EventLogger(trackSelector));
            player.setAudioAttributes(AudioAttributes.DEFAULT,true);
            player.setPlayWhenReady(true);
            playerView.setPlayer(player);
        }

        player.setMediaItem(mediaItem);
        player.prepare();
        return true;
    }


    private MediaItem createMediaItem(){
        Uri uri = Uri.parse("http://ivi.bupt.edu.cn/hls/cctv1hd.m3u8");
        MediaItem.Builder mediaItem = new MediaItem.Builder();
        String adaptiveMimeType =
                Util.getAdaptiveMimeTypeForContentType(Util.inferContentType(uri));
        mediaItem
                .setUri(uri)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle("title").build())
                .setMimeType(adaptiveMimeType);
        return mediaItem.build();
    }

    @Override
    public void onStart() {
        super.onStart();
        initializePlayer();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (player == null) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    public void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    protected void releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters();
            player.release();
            player = null;
            mediaItem = null;
            trackSelector = null;
        }

    }

    private void updateTrackSelectorParameters() {
        if (trackSelector != null) {
            trackSelectorParameters = trackSelector.getParameters();
        }
    }

    private class PlayerEventListener implements Player.EventListener {

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
            Log.d(TAG,"onPlaybackStateChanged: "+playbackState);
        }

        @Override
        public void onPlayerError(@NonNull ExoPlaybackException e) {
            Log.d(TAG,"onPlayerError: "+e.toString());
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(
                @NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
            Log.d(TAG,"onTracksChanged");

        }
    }

    private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

        @Override
        @NonNull
        public Pair<Integer, String> getErrorMessage(@NonNull ExoPlaybackException e) {
            String errorString = getString(R.string.error_generic);
            if (e.type == ExoPlaybackException.TYPE_RENDERER) {
                Exception cause = e.getRendererException();
                if (cause instanceof MediaCodecRenderer.DecoderInitializationException) {
                    // Special case for decoder initialization failures.
                    MediaCodecRenderer.DecoderInitializationException decoderInitializationException =
                            (MediaCodecRenderer.DecoderInitializationException) cause;
                    if (decoderInitializationException.codecInfo == null) {
                        if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                            errorString = getString(R.string.error_querying_decoders);
                        } else if (decoderInitializationException.secureDecoderRequired) {
                            errorString =
                                    getString(
                                            R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
                        } else {
                            errorString =
                                    getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
                        }
                    } else {
                        errorString =
                                getString(
                                        R.string.error_instantiating_decoder,
                                        decoderInitializationException.codecInfo.name);
                    }
                }
            }
            return Pair.create(0, errorString);
        }
    }
}