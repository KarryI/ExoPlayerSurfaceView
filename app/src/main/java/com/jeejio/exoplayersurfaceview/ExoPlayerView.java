package com.jeejio.exoplayersurfaceview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.video.VideoDecoderGLSurfaceView;
import com.google.android.exoplayer2.video.VideoListener;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

public class ExoPlayerView extends FrameLayout {

    /**
     * Determines when the buffering view is shown. One of {@link #SHOW_BUFFERING_NEVER}, {@link
     * #SHOW_BUFFERING_WHEN_PLAYING} or {@link #SHOW_BUFFERING_ALWAYS}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SHOW_BUFFERING_NEVER, SHOW_BUFFERING_WHEN_PLAYING, SHOW_BUFFERING_ALWAYS})
    public @interface ShowBuffering {}
    /** The buffering view is never shown. */
    public static final int SHOW_BUFFERING_NEVER = 0;
    /**
     * The buffering view is shown when the player is in the {@link Player#STATE_BUFFERING buffering}
     * state and {@link Player#getPlayWhenReady() playWhenReady} is {@code true}.
     */
    public static final int SHOW_BUFFERING_WHEN_PLAYING = 1;
    /**
     * The buffering view is always shown when the player is in the {@link Player#STATE_BUFFERING
     * buffering} state.
     */
    public static final int SHOW_BUFFERING_ALWAYS = 2;
    // LINT.ThenChange(../../../../../../res/values/attrs.xml)

    // LINT.IfChange
    private static final int SURFACE_TYPE_NONE = 0;
    private static final int SURFACE_TYPE_SURFACE_VIEW = 1;
    private static final int SURFACE_TYPE_TEXTURE_VIEW = 2;
    private static final int SURFACE_TYPE_SPHERICAL_GL_SURFACE_VIEW = 3;
    private static final int SURFACE_TYPE_VIDEO_DECODER_GL_SURFACE_VIEW = 4;
    // LINT.ThenChange(../../../../../../res/values/attrs.xml)

    private final ComponentListener componentListener;
    @Nullable private final View surfaceView;

    @Nullable private Player player;
    private boolean keepContentOnPlayerReset;
    @Nullable private ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider;

    private int textureViewRotation;

    public ExoPlayerView(Context context) {
        this(context, /* attrs= */ null);
    }

    public ExoPlayerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    @SuppressWarnings({"nullness:argument.type.incompatible", "nullness:method.invocation.invalid"})
    public ExoPlayerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        componentListener = new ComponentListener();

        if (isInEditMode()) {
            surfaceView = null;
            return;
        }

        int surfaceType = SURFACE_TYPE_SURFACE_VIEW;

        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);

        // Create a surface view and insert it into the content frame, if there is one.
        if (surfaceType != SURFACE_TYPE_NONE) {
            ViewGroup.LayoutParams params =
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            switch (surfaceType) {
                case SURFACE_TYPE_TEXTURE_VIEW:
                    surfaceView = new TextureView(context);
                    break;

                case SURFACE_TYPE_VIDEO_DECODER_GL_SURFACE_VIEW:
                    surfaceView = new VideoDecoderGLSurfaceView(context);
                    break;
                default:
                    surfaceView = new SurfaceView(context);
                    break;
            }
            surfaceView.setLayoutParams(params);
            addView(surfaceView, 0);
        } else {
            surfaceView = null;
        }
    }

    /**
     * Switches the view targeted by a given {@link Player}.
     *
     * @param player The player whose target view is being switched.
     * @param oldPlayerView The old view to detach from the player.
     * @param newPlayerView The new view to attach to the player.
     */
    public static void switchTargetView(
            Player player,
            @Nullable ExoPlayerView oldPlayerView,
            @Nullable ExoPlayerView newPlayerView) {
        if (oldPlayerView == newPlayerView) {
            return;
        }
        // We attach the new view before detaching the old one because this ordering allows the player
        // to swap directly from one surface to another, without transitioning through a state where no
        // surface is attached. This is significantly more efficient and achieves a more seamless
        // transition when using platform provided video decoders.
        if (newPlayerView != null) {
            newPlayerView.setPlayer(player);
        }
        if (oldPlayerView != null) {
            oldPlayerView.setPlayer(null);
        }
    }

    /** Returns the player currently set on this view, or null if no player is set. */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /**
     * Set the {@link Player} to use.
     *
     * <p>To transition a {@link Player} from targeting one view to another, it's recommended to use
     * {@link #switchTargetView(Player, ExoPlayerView, ExoPlayerView)} rather than this method.
     * If you do wish to use this method directly, be sure to attach the player to the new view
     * <em>before</em> calling {@code setPlayer(null)} to detach it from the old one. This ordering is
     * significantly more efficient and may allow for more seamless transitions.
     *
     * @param player The {@link Player} to use, or {@code null} to detach the current player. Only
     *     players which are accessed on the main thread are supported ({@code
     *     player.getApplicationLooper() == Looper.getMainLooper()}).
     */
    public void setPlayer(@Nullable Player player) {
        Assertions.checkState(Looper.myLooper() == Looper.getMainLooper());
        Assertions.checkArgument(
                player == null || player.getApplicationLooper() == Looper.getMainLooper());
        if (this.player == player) {
            return;
        }
        @Nullable Player oldPlayer = this.player;
        if (oldPlayer != null) {
            oldPlayer.removeListener(componentListener);
            @Nullable Player.VideoComponent oldVideoComponent = oldPlayer.getVideoComponent();
            if (oldVideoComponent != null) {
                oldVideoComponent.removeVideoListener(componentListener);
                if (surfaceView instanceof TextureView) {
                    oldVideoComponent.clearVideoTextureView((TextureView) surfaceView);
                } else if (surfaceView instanceof VideoDecoderGLSurfaceView) {
                    oldVideoComponent.setVideoDecoderOutputBufferRenderer(null);
                } else if (surfaceView instanceof SurfaceView) {
                    oldVideoComponent.clearVideoSurfaceView((SurfaceView) surfaceView);
                }
            }
            @Nullable Player.TextComponent oldTextComponent = oldPlayer.getTextComponent();
            if (oldTextComponent != null) {
                oldTextComponent.removeTextOutput(componentListener);
            }
        }
        this.player = player;

        updateForCurrentTrackSelections(/* isNewPlayer= */ true);
        if (player != null) {
            @Nullable Player.VideoComponent newVideoComponent = player.getVideoComponent();
            if (newVideoComponent != null) {
                if (surfaceView instanceof TextureView) {
                    newVideoComponent.setVideoTextureView((TextureView) surfaceView);
                } else if (surfaceView instanceof VideoDecoderGLSurfaceView) {
                    newVideoComponent.setVideoDecoderOutputBufferRenderer(
                            ((VideoDecoderGLSurfaceView) surfaceView).getVideoDecoderOutputBufferRenderer());
                } else if (surfaceView instanceof SurfaceView) {
                    newVideoComponent.setVideoSurfaceView((SurfaceView) surfaceView);
                }
                newVideoComponent.addVideoListener(componentListener);
            }
            @Nullable Player.TextComponent newTextComponent = player.getTextComponent();
            if (newTextComponent != null) {
                newTextComponent.addTextOutput(componentListener);
            }
            player.addListener(componentListener);
        } else {
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (surfaceView instanceof SurfaceView) {
            // Work around https://github.com/google/ExoPlayer/issues/3160.
            surfaceView.setVisibility(visibility);
        }
    }

    /**
     * Sets whether the currently displayed video frame or media artwork is kept visible when the
     * player is reset. A player reset is defined to mean the player being re-prepared with different
     * media, the player transitioning to unprepared media, {@link Player#stop(boolean)} being called
     * with {@code reset=true}, or the player being replaced or cleared by calling {@link
     * #setPlayer(Player)}.
     *
     * <p>If enabled, the currently displayed video frame or media artwork will be kept visible until
     * the player set on the view has been successfully prepared with new media and loaded enough of
     * it to have determined the available tracks. Hence enabling this option allows transitioning
     * from playing one piece of media to another, or from using one player instance to another,
     * without clearing the view's content.
     *
     * <p>If disabled, the currently displayed video frame or media artwork will be hidden as soon as
     * the player is reset. Note that the video frame is hidden by making {@code exo_shutter} visible.
     * Hence the video frame will not be hidden if using a custom layout that omits this view.
     *
     * @param keepContentOnPlayerReset Whether the currently displayed video frame or media artwork is
     *     kept visible when the player is reset.
     */
    public void setKeepContentOnPlayerReset(boolean keepContentOnPlayerReset) {
        if (this.keepContentOnPlayerReset != keepContentOnPlayerReset) {
            this.keepContentOnPlayerReset = keepContentOnPlayerReset;
            updateForCurrentTrackSelections(/* isNewPlayer= */ false);
        }
    }


    /**
     * Sets the optional {@link ErrorMessageProvider}.
     *
     * @param errorMessageProvider The error message provider.
     */
    public void setErrorMessageProvider(
            @Nullable ErrorMessageProvider<? super ExoPlaybackException> errorMessageProvider) {
        if (this.errorMessageProvider != errorMessageProvider) {
            this.errorMessageProvider = errorMessageProvider;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }



    /**
     * Gets the view onto which video is rendered. This is a:
     *
     * <ul>
     *   <li>{@link SurfaceView} by default, or if the {@code surface_type} attribute is set to {@code
     *       surface_view}.
     *   <li>{@link TextureView} if {@code surface_type} is {@code texture_view}.
     *       spherical_gl_surface_view}.
     *   <li>{@link VideoDecoderGLSurfaceView} if {@code surface_type} is {@code
     *       video_decoder_gl_surface_view}.
     *   <li>{@code null} if {@code surface_type} is {@code none}.
     * </ul>
     *
     * @return The {@link SurfaceView}, {@link TextureView}, {@link
     *     VideoDecoderGLSurfaceView} or {@code null}.
     */
    @Nullable
    public View getVideoSurfaceView() {
        return surfaceView;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        return false;
    }

    /**
     * Should be called when the player is visible to the user and if {@code surface_type} is {@code
     * spherical_gl_surface_view}. It is the counterpart to {@link #onPause()}.
     *
     * <p>This method should typically be called in {@code Activity.onStart()}, or {@code
     * Activity.onResume()} for API versions &lt;= 23.
     */
    public void onResume() {

    }

    /**
     * Should be called when the player is no longer visible to the user and if {@code surface_type}
     * is {@code spherical_gl_surface_view}. It is the counterpart to {@link #onResume()}.
     *
     * <p>This method should typically be called in {@code Activity.onStop()}, or {@code
     * Activity.onPause()} for API versions &lt;= 23.
     */
    public void onPause() {
    }

    private void updateForCurrentTrackSelections(boolean isNewPlayer) {
        @Nullable Player player = this.player;
        if (player == null || player.getCurrentTrackGroups().isEmpty()) {
            if (!keepContentOnPlayerReset) {
            }
            return;
        }

        if (isNewPlayer && !keepContentOnPlayerReset) {
            // Hide any video from the previous player.
        }

        TrackSelectionArray selections = player.getCurrentTrackSelections();
        for (int i = 0; i < selections.length; i++) {
            if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO && selections.get(i) != null) {
                // Video enabled so artwork must be hidden. If the shutter is closed, it will be opened in
                // onRenderedFirstFrame().
                return;
            }
        }
    }


    /** Applies a texture rotation to a {@link TextureView}. */
    private static void applyTextureViewRotation(TextureView textureView, int textureViewRotation) {
        Matrix transformMatrix = new Matrix();
        float textureViewWidth = textureView.getWidth();
        float textureViewHeight = textureView.getHeight();
        if (textureViewWidth != 0 && textureViewHeight != 0 && textureViewRotation != 0) {
            float pivotX = textureViewWidth / 2;
            float pivotY = textureViewHeight / 2;
            transformMatrix.postRotate(textureViewRotation, pivotX, pivotY);

            // After rotation, scale the rotated texture to fit the TextureView size.
            RectF originalTextureRect = new RectF(0, 0, textureViewWidth, textureViewHeight);
            RectF rotatedTextureRect = new RectF();
            transformMatrix.mapRect(rotatedTextureRect, originalTextureRect);
            transformMatrix.postScale(
                    textureViewWidth / rotatedTextureRect.width(),
                    textureViewHeight / rotatedTextureRect.height(),
                    pivotX,
                    pivotY);
        }
        textureView.setTransform(transformMatrix);
    }

    @SuppressLint("InlinedApi")
    private boolean isDpadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_UP_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    private final class ComponentListener
            implements Player.EventListener,
            TextOutput,
            VideoListener,
            View.OnLayoutChangeListener{

        private final Timeline.Period period;
        private @Nullable Object lastPeriodUidWithTracks;

        public ComponentListener() {
            period = new Timeline.Period();
        }

        // TextOutput implementation

        @Override
        public void onCues(List<Cue> cues) {

        }

        // VideoListener implementation

        @Override
        public void onVideoSizeChanged(
                int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            float videoAspectRatio =
                    (height == 0 || width == 0) ? 1 : (width * pixelWidthHeightRatio) / height;

            if (surfaceView instanceof TextureView) {
                // Try to apply rotation transformation when our surface is a TextureView.
                if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                    // We will apply a rotation 90/270 degree to the output texture of the TextureView.
                    // In this case, the output video's width and height will be swapped.
                    videoAspectRatio = 1 / videoAspectRatio;
                }
                if (textureViewRotation != 0) {
                    surfaceView.removeOnLayoutChangeListener(this);
                }
                textureViewRotation = unappliedRotationDegrees;
                if (textureViewRotation != 0) {
                    // The texture view's dimensions might be changed after layout step.
                    // So add an OnLayoutChangeListener to apply rotation after layout step.
                    surfaceView.addOnLayoutChangeListener(this);
                }
                applyTextureViewRotation((TextureView) surfaceView, textureViewRotation);
            }

        }

        @Override
        public void onRenderedFirstFrame() {

        }

        @Override
        public void onTracksChanged(TrackGroupArray tracks, TrackSelectionArray selections) {
            // Suppress the update if transitioning to an unprepared period within the same window. This
            // is necessary to avoid closing the shutter when such a transition occurs. See:
            // https://github.com/google/ExoPlayer/issues/5507.
            Player player = checkNotNull(ExoPlayerView.this.player);
            Timeline timeline = player.getCurrentTimeline();
            if (timeline.isEmpty()) {
                lastPeriodUidWithTracks = null;
            } else if (!player.getCurrentTrackGroups().isEmpty()) {
                lastPeriodUidWithTracks =
                        timeline.getPeriod(player.getCurrentPeriodIndex(), period, /* setIds= */ true).uid;
            } else if (lastPeriodUidWithTracks != null) {
                int lastPeriodIndexWithTracks = timeline.getIndexOfPeriod(lastPeriodUidWithTracks);
                if (lastPeriodIndexWithTracks != C.INDEX_UNSET) {
                    int lastWindowIndexWithTracks =
                            timeline.getPeriod(lastPeriodIndexWithTracks, period).windowIndex;
                    if (player.getCurrentWindowIndex() == lastWindowIndexWithTracks) {
                        // We're in the same window. Suppress the update.
                        return;
                    }
                }
                lastPeriodUidWithTracks = null;
            }

            updateForCurrentTrackSelections(/* isNewPlayer= */ false);
        }

        // Player.EventListener implementation

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
        }

        @Override
        public void onPlayWhenReadyChanged(
                boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
        }

        @Override
        public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {

        }

        // OnLayoutChangeListener implementation

        @Override
        public void onLayoutChange(
                View view,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            applyTextureViewRotation((TextureView) view, textureViewRotation);
        }

    }
}
