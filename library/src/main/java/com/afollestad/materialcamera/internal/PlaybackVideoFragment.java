package com.afollestad.materialcamera.internal;

import android.app.Activity;
import android.app.Fragment;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.afollestad.materialcamera.R;
import com.afollestad.materialcamera.util.CameraUtil;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDTintHelper;

/**
 * @author Aidan Follestad (afollestad)
 */
public class PlaybackVideoFragment extends Fragment implements
        VideoStreamView.Callback, OutputUriInterface, View.OnClickListener {

    private TextView mPosition;
    private SeekBar mPositionSeek;
    private TextView mDuration;
    private ImageButton mPlayPause;
    private View mRetry;
    private View mUseVideo;
    private View mControlsFrame;
    private VideoStreamView mStreamer;
    private TextView mPlaybackContinueCountdownLabel;

    private String mOutputUri;
    private boolean mWasPlaying;
    private BaseCaptureInterface mInterface;

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mInterface = (BaseCaptureInterface) activity;
    }

    private Handler mPositionHandler;
    private final Runnable mPositionUpdater = new Runnable() {
        @Override
        public void run() {
            if (mStreamer == null || mPositionHandler == null) {
                if (mPosition != null) {
                    mPosition.setText(mDuration.getText());
                    mPositionSeek.setProgress(mPositionSeek.getMax());
                }
            } else {
                try {
                    final int currentPosition = mStreamer.getCurrentPosition();
                    mPosition.setText(CameraUtil.getDurationString(currentPosition));
                    mPositionSeek.setProgress(currentPosition);
                    mDuration.setText(String.format("-%s", CameraUtil.getDurationString(
                            mStreamer.getDuration() - mStreamer.getCurrentPosition())));
                    if (mPositionHandler == null) {
                        mPosition.setText(CameraUtil.getDurationString(mPositionSeek.getMax()));
                        mPositionSeek.setProgress(mPositionSeek.getMax());
                    }
                } catch (Throwable t) {
                    mPosition.setText(CameraUtil.getDurationString(0));
                    mPositionSeek.setProgress(mPositionSeek.getMax());
                }
            }

            if (mPlaybackContinueCountdownLabel != null && mPlaybackContinueCountdownLabel.getVisibility() == View.VISIBLE) {
                long diff = mInterface.getRecordingEnd() - System.currentTimeMillis();
                if (diff < 3 && mPlayPause != null) {
                    mRetry.setEnabled(false);
                    mPlayPause.setEnabled(false);
                    mUseVideo.setEnabled(false);
                }
                if (diff <= 0) {
                    useVideo();
                    return;
                }
                mPlaybackContinueCountdownLabel.setText(String.format("-%s", CameraUtil.getDurationString(diff)));
            }

            if (mPositionHandler != null)
                mPositionHandler.postDelayed(this, 200);
        }
    };

    public static PlaybackVideoFragment newInstance(String outputUri, boolean allowRetry, int primaryColor) {
        PlaybackVideoFragment fragment = new PlaybackVideoFragment();
        fragment.setRetainInstance(true);
        Bundle args = new Bundle();
        args.putString("output_uri", outputUri);
        args.putBoolean("allow_retry", allowRetry);
        args.putInt("primary_color", primaryColor);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null)
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mcam_fragment_videoplayback, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPosition = (TextView) view.findViewById(R.id.position);
        mPositionSeek = (SeekBar) view.findViewById(R.id.positionSeek);
        mDuration = (TextView) view.findViewById(R.id.duration);
        mPlayPause = (ImageButton) view.findViewById(R.id.playPause);
        mRetry = view.findViewById(R.id.retry);
        mUseVideo = view.findViewById(R.id.useVideo);
        mControlsFrame = view.findViewById(R.id.controlsFrame);
        mStreamer = (VideoStreamView) view.findViewById(R.id.playbackView);
        mPlaybackContinueCountdownLabel = (TextView) view.findViewById(R.id.playbackContinueCountdownLabel);

        view.findViewById(R.id.playbackFrame).setOnClickListener(this);
        mRetry.setOnClickListener(this);
        mPlayPause.setOnClickListener(this);
        mUseVideo.setOnClickListener(this);

        int primaryColor = CameraUtil.darkenColor(getArguments().getInt("primary_color"));
        primaryColor = Color.argb((int) (255 * 0.75f), Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        mControlsFrame.setBackgroundColor(primaryColor);

        mRetry.setVisibility(getArguments().getBoolean("allow_retry", true) ? View.VISIBLE : View.GONE);
        mPositionSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mPositionHandler == null)
                        startCounter();
                    mStreamer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mWasPlaying = mStreamer.isPlaying();
                mStreamer.pause();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mWasPlaying)
                    mStreamer.start(getActivity());
            }
        });
        MDTintHelper.setTint(mPositionSeek, Color.WHITE);
        mOutputUri = getArguments().getString("output_uri");

        if (mInterface.hasLengthLimit() && mInterface.shouldAutoSubmit()) {
            mPlaybackContinueCountdownLabel.setVisibility(View.VISIBLE);
            final long diff = mInterface.getRecordingEnd() - System.currentTimeMillis();
            mPlaybackContinueCountdownLabel.setText(String.format("-%s", CameraUtil.getDurationString(diff)));
            startCounter();
        } else {
            mPlaybackContinueCountdownLabel.setVisibility(View.GONE);
        }

        mStreamer.setURI(getActivity(), Uri.parse(mOutputUri), this);

        if (mStreamer.isPlaying())
            mPlayPause.setImageResource(R.drawable.mcam_action_pause);
        else
            mPlayPause.setImageResource(R.drawable.mcam_action_play);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPosition = null;
        mPositionSeek = null;
        mDuration = null;
        mPlayPause = null;
        mRetry = null;
        mUseVideo = null;
        mControlsFrame = null;
        mStreamer = null;
        mPlaybackContinueCountdownLabel = null;
    }

    private void startCounter() {
        if (mPositionHandler == null)
            mPositionHandler = new Handler();
        else mPositionHandler.removeCallbacks(mPositionUpdater);
        mPositionHandler.post(mPositionUpdater);
    }

    private void stopCounter() {
        if (mPositionHandler != null) {
            mPositionHandler.removeCallbacks(mPositionUpdater);
            mPositionHandler = null;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.playbackFrame) {
            mControlsFrame.animate().cancel();
            final float targetAlpha = mControlsFrame.getAlpha() == 1f ? 0f :
                    mControlsFrame.getAlpha() == 0f ? 1f :
                            mControlsFrame.getAlpha() > 0.5f ? 0f : 1f;
            mControlsFrame.animate().alpha(targetAlpha).start();
        } else if (v.getId() == R.id.playPause) {
            if (mStreamer != null) {
                if (mStreamer.isPlaying()) {
                    ((ImageButton) v).setImageResource(R.drawable.mcam_action_play);
                    mStreamer.pause();
                } else {
                    ((ImageButton) v).setImageResource(R.drawable.mcam_action_pause);
                    mStreamer.start(getActivity());
                    startCounter();
                }
            }
        } else if (v.getId() == R.id.retry) {
            mInterface.onRetry(mOutputUri);
        } else if (v.getId() == R.id.useVideo) {
            useVideo();
        }
    }

    private void useVideo() {
        if (mStreamer != null) {
            mStreamer.stop();
            mStreamer.release();
            mStreamer = null;
        }
        stopCounter();
        if (mInterface != null)
            mInterface.useVideo(mOutputUri);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (!mInterface.hasLengthLimit() && mPositionHandler != null)
            mPositionHandler.removeCallbacks(mPositionUpdater);
        mPositionSeek.setMax(mp.getDuration());
        mDuration.setText(String.format("-%s", CameraUtil.getDurationString(mp.getDuration())));
        mPlayPause.setEnabled(true);
        mRetry.setEnabled(true);
        mUseVideo.setEnabled(true);
    }

    @Override
    public void onCompleted() {
        stopCounter();
        if (mPlayPause != null)
            mPlayPause.setImageResource(R.drawable.mcam_action_play);
        if (mPositionSeek != null) {
            mPositionSeek.setProgress(mStreamer.getDuration());
            mPosition.setText(CameraUtil.getDurationString(mStreamer.getDuration()));
        }
    }

    @Override
    public void onError(MediaPlayer mp, int what, int extra) {
        if (what == -38) {
            // Error code -38 happens on some Samsung devices
            // Just ignore it
            return;
        }
        String errorMsg = "Preparation/playback error: ";
        switch (what) {
            default:
                errorMsg += String.format("Unknown error %d", what);
                break;
            case MediaPlayer.MEDIA_ERROR_IO:
                errorMsg += "I/O error";
                break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                errorMsg += "Malformed";
                break;
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                errorMsg += "Not valid for progressive playback";
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                errorMsg += "Server died";
                break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                errorMsg += "Timed out";
                break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                errorMsg += "Unsupported";
                break;
        }
        new MaterialDialog.Builder(getActivity())
                .title("Playback Error")
                .content(errorMsg)
                .positiveText(android.R.string.ok)
                .show();
    }

    @Override
    public void onBuffer(int percent) {
        if (mPositionSeek != null)
            mPositionSeek.setSecondaryProgress(percent);
    }

    @Override
    public String getOutputUri() {
        return getArguments().getString("output_uri");
    }
}