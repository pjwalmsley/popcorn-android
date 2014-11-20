/*****************************************************************************
 * VideoPlayerActivity.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package pct.droid.activities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaRouter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings.SettingNotFoundException;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import pct.droid.PopcornApplication;
import pct.droid.R;
import pct.droid.utils.StringUtils;

public class VideoPlayerActivity extends ActionBarActivity implements IVideoPlayer {

    public final static String TAG = "VLC/VideoPlayerActivity";
    public final static String LOCATION = "location";
    public final static String DATA = "data";
    public final static String QUALITY = "quality";

    // Internal intent identifier to distinguish between internal launch and
    // external intent.
    private final static String PLAY_FROM_VIDEOGRID = "org.videolan.vlc.gui.video.PLAY_FROM_VIDEOGRID";

    private SurfaceView mSurface;
    private SurfaceView mSubtitlesSurface;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSubtitlesSurfaceHolder;
    private FrameLayout mSurfaceFrame;
    private MediaRouter mMediaRouter;
    private MediaRouter.SimpleCallback mMediaRouterCallback;
    private SecondaryDisplay mPresentation;
    private LibVLC mLibVLC;
    private String mLocation;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;

    private SharedPreferences mSettings;

    /** Overlay */
    private ActionBar mActionBar;
    private boolean mOverlayUseStatusBar;
    private View mOverlayHeader;
    private View mOverlayOption;
    private View mOverlayProgress;
    private View mOverlayBackground;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = 3600000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SURFACE_SIZE = 3;
    private static final int AUDIO_SERVICE_CONNECTION_SUCCESS = 5;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 6;
    private static final int FADE_OUT_INFO = 4;
    private boolean mDragging;
    private boolean mShowing;
    private int mUiVisibility = -1;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mSysTime;
    private TextView mBattery;
    private TextView mTime;
    private TextView mLength;
    private TextView mInfo;
    private ImageView mLoading;
    private TextView mLoadingText;
    private ImageButton mPlayPause;
    private ImageButton mBackward;
    private ImageButton mForward;
    private boolean mEnableJumpButtons;
    private boolean mEnableBrightnessGesture;
    private boolean mEnableCloneMode;
    private boolean mDisplayRemainingTime = false;
    private int mScreenOrientation;
    private ImageButton mAudioTrack;
    private ImageButton mSubtitle;
    private ImageButton mLock;
    private ImageButton mSize;
    private ImageButton mMenu;
    private boolean mIsLocked = false;
    private int mLastAudioTrack = -1;
    private int mLastSpuTrack = -2;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mEndReached;
    private boolean mCanSeek;

    // Playlist
    private int savedIndexPosition = -1;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private OnAudioFocusChangeListener mAudioFocusListener;
    private boolean mMute = false;
    private int mVol, mVolSave;

    //Touch Events
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_VOLUME = 1;
    private static final int TOUCH_BRIGHTNESS = 2;
    private static final int TOUCH_SEEK = 3;
    private int mTouchAction;
    private int mSurfaceYDisplayRange;
    private float mTouchY, mTouchX;

    //stick event
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;

    // Brightness
    private boolean mIsFirstBrightnessGesture = true;

    // Tracks & Subtitles
    private Map<Integer,String> mAudioTracksList;
    private Map<Integer,String> mSubtitleTracksList;
    /**
     * Used to store a selected subtitle; see onActivityResult.
     * It is possible to have multiple custom subs in one session
     * (just like desktop VLC allows you as well.)
     */
    private final ArrayList<String> mSubtitleSelectedFiles = new ArrayList<String>();

    // Whether fallback from HW acceleration to SW decoding was done.
    private boolean mDisabledHardwareAcceleration = false;
    private int mPreviousHardwareAccelerationMode;

    // Tips
    private View mOverlayTips;
    private static final String PREF_TIPS_SHOWN = "video_player_tips_shown";

    // Navigation handling (DVD, Blu-Ray...)
    private ImageButton mNavMenu;
    private boolean mHasMenu = false;
    private boolean mIsNavMenu = false;

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (LibVlcUtil.isJellyBeanMR1OrLater()) {
            // Get the media router service (Miracast)
            mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
            mMediaRouterCallback = new MediaRouter.SimpleCallback() {
                @Override
                public void onRoutePresentationDisplayChanged(
                        MediaRouter router, MediaRouter.RouteInfo info) {
                    Log.d(TAG, "onRoutePresentationDisplayChanged: info=" + info);
                    removePresentation();
                }
            };
            Log.d(TAG, "MediaRouter information : " + mMediaRouter  .toString());
            mOverlayUseStatusBar = true;
        } else {
            mOverlayUseStatusBar = false;
        }

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        createPresentation();
        setContentView(mPresentation == null ? R.layout.activity_player : R.layout.activity_player_remote_control);

        if (LibVlcUtil.isICSOrLater())
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                    new OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            if (visibility == mUiVisibility)
                                return;
                            setSurfaceSize(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
                            if (visibility == View.SYSTEM_UI_FLAG_VISIBLE && !mShowing && !isFinishing()) {
                                showOverlay();
                            }
                            mUiVisibility = visibility;
                        }
                    }
            );

        /** initialize Views an their Events */
        if (mOverlayUseStatusBar) {
            mActionBar = getSupportActionBar();
            mActionBar.setDisplayShowHomeEnabled(false);
            mActionBar.setDisplayShowTitleEnabled(false);
            mActionBar.setBackgroundDrawable(null);
            mActionBar.setDisplayShowCustomEnabled(true);
            mActionBar.setCustomView(R.layout.activity_player_action_bar);

            ViewGroup view = (ViewGroup) mActionBar.getCustomView();
            /* Dispatch ActionBar touch events to the Activity */
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    onTouchEvent(event);
                    return true;
                }
            });
            mTitle = (TextView) view.findViewById(R.id.player_overlay_title);
        } else {
            mOverlayHeader = findViewById(R.id.player_overlay_header);
            /* header */
            mTitle = (TextView) findViewById(R.id.player_overlay_title);
            mSysTime = (TextView) findViewById(R.id.player_overlay_systime);
            mBattery = (TextView) findViewById(R.id.player_overlay_battery);
        }
        mOverlayOption = findViewById(R.id.option_overlay);
        mOverlayProgress = findViewById(R.id.progress_overlay);
        mOverlayBackground = findViewById(R.id.player_overlay_background);

        // Position and remaining time
        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mTime.setOnClickListener(mRemainingTimeListener);
        mLength = (TextView) findViewById(R.id.player_overlay_length);
        mLength.setOnClickListener(mRemainingTimeListener);

        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_info);

        mEnableBrightnessGesture = mSettings.getBoolean("enable_brightness_gesture", true);
        mScreenOrientation = Integer.valueOf(
                mSettings.getString("screen_orientation_value", "4" /*SCREEN_ORIENTATION_SENSOR*/));

        mEnableJumpButtons = mSettings.getBoolean("enable_jump_buttons", false);
        mPlayPause = (ImageButton) findViewById(R.id.player_overlay_play);
        mPlayPause.setOnClickListener(mPlayPauseListener);
        mBackward = (ImageButton) findViewById(R.id.player_overlay_backward);
        mBackward.setOnClickListener(mBackwardListener);
        mForward = (ImageButton) findViewById(R.id.player_overlay_forward);
        mForward.setOnClickListener(mForwardListener);

        mAudioTrack = (ImageButton) findViewById(R.id.player_overlay_audio);
        mAudioTrack.setVisibility(View.GONE);
        mSubtitle = (ImageButton) findViewById(R.id.player_overlay_subtitle);
        mSubtitle.setVisibility(View.GONE);
        mNavMenu = (ImageButton) findViewById(R.id.player_overlay_navmenu);
        mNavMenu.setVisibility(View.GONE);

        mLock = (ImageButton) findViewById(R.id.lock_overlay_button);
        mLock.setOnClickListener(mLockListener);

        mSize = (ImageButton) findViewById(R.id.player_overlay_size);
        mSize.setOnClickListener(mSizeListener);

        mMenu = (ImageButton) findViewById(R.id.player_overlay_adv_function);

        try {
            mLibVLC = VLCInstance.getLibVlcInstance();
        } catch (LibVlcException e) {
            Log.d(TAG, "LibVLC initialisation failed");
            return;
        }

        mSurface = (SurfaceView) findViewById(R.id.player_surface);
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);
        String chroma = mSettings.getString("chroma_format", "");
        if(LibVlcUtil.isGingerbreadOrLater() && chroma.equals("YV12")) {
            mSurfaceHolder.setFormat(ImageFormat.YV12);
        } else if (chroma.equals("RV16")) {
            mSurfaceHolder.setFormat(PixelFormat.RGB_565);
        } else {
            mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
        }

        mSubtitlesSurface = (SurfaceView) findViewById(R.id.subtitles_surface);
        mSubtitlesSurfaceHolder = mSubtitlesSurface.getHolder();
        mSubtitlesSurfaceHolder.setFormat(PixelFormat.RGBA_8888);
        mSubtitlesSurface.setZOrderMediaOverlay(true);
        if (mPresentation == null) {
            mSurfaceHolder.addCallback(mSurfaceCallback);
            mSubtitlesSurfaceHolder.addCallback(mSubtitlesSurfaceCallback);
        }

        mSeekbar = (SeekBar) findViewById(R.id.player_overlay_seekbar);
        mSeekbar.setOnSeekBarChangeListener(mSeekListener);

        /* Loading view */
        mLoading = (ImageView) findViewById(R.id.player_overlay_loading);
        mLoadingText = (TextView) findViewById(R.id.player_overlay_loading_text);
        startLoadingAnimation();

        mSwitchingView = false;
        mEndReached = false;

        IntentFilter filter = new IntentFilter();
        if (!mOverlayUseStatusBar)
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(PopcornApplication.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);

        Log.d(TAG,
                "Hardware acceleration mode: "
                        + Integer.toString(mLibVLC.getHardwareAcceleration()));

        /* Only show the subtitles surface when using "Full Acceleration" mode */
        if (mLibVLC.getHardwareAcceleration() == LibVLC.HW_ACCELERATION_FULL)
            mSubtitlesSurface.setVisibility(View.VISIBLE);
        // Signal to LibVLC that the videoPlayerActivity was created, thus the
        // SurfaceView is now available for MediaCodec direct rendering.
        mLibVLC.eventVideoPlayerActivityCreated(true);

        EventHandler em = EventHandler.getInstance();
        em.addHandler(eventHandler);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Extra initialization when no secondary display is detected
        if (mPresentation == null) {
            // Orientation
            // 100 is the value for screen_orientation_start_lock
            setRequestedOrientation(mScreenOrientation != 100
                    ? mScreenOrientation
                    : getScreenOrientation());
           
        } else
            setRequestedOrientation(getScreenOrientation());

        updateNavStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mMediaRouter != null) {
            // Stop listening for changes to media routes.
            mediaRouterAddCallback(false);
        }

        if(mSwitchingView) {
            Log.d(TAG, "mLocation = \"" + mLocation + "\"");
            AudioServiceController.getInstance().showWithoutParse(savedIndexPosition);
            AudioServiceController.getInstance().unbindAudioService(this);
            return;
        }

        long time = mLibVLC.getTime();
        long length = mLibVLC.getLength();
        //remove saved position if in the last 5 seconds
        if (length - time < 5000)
            time = 0;
        else
            time -= 5000; // go back 5 seconds, to compensate loading time

        /*
         * Pausing here generates errors because the vout is constantly
         * trying to refresh itself every 80ms while the surface is not
         * accessible anymore.
         * To workaround that, we keep the last known position in the playlist
         * in savedIndexPosition to be able to restore it during onResume().
         */
        mLibVLC.stop();

        mSurface.setKeepScreenOn(false);
        
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onStop() {
        super.onStop();

        // Dismiss the presentation when the activity is not visible.
        if (mPresentation != null) {
            Log.i(TAG, "Dismissing presentation because the activity is no longer visible.");
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);

        EventHandler em = EventHandler.getInstance();
        em.removeHandler(eventHandler);

        // MediaCodec opaque direct rendering should not be used anymore since there is no surface to attach.
        mLibVLC.eventVideoPlayerActivityCreated(false);
        // HW acceleration was temporarily disabled because of an error, restore the previous value.
        if (mDisabledHardwareAcceleration)
            mLibVLC.setHardwareAcceleration(mPreviousHardwareAccelerationMode);

        mAudioManager = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSwitchingView = false;
        AudioServiceController.getInstance().bindAudioService(this,
                new AudioServiceController.AudioServiceConnectionListener() {
                    @Override
                    public void onConnectionSuccess() {
                        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_SUCCESS);
                    }

                    @Override
                    public void onConnectionFailed() {
                        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);
                    }
                });

        if (mMediaRouter != null) {
            // Listen for changes to media routes.
            mediaRouterAddCallback(true);
        }
    }

    /**
     * Add or remove MediaRouter callbacks. This is provided for version targeting.
     *
     * @param add true to add, false to remove
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void mediaRouterAddCallback(boolean add) {
        if(!LibVlcUtil.isJellyBeanMR1OrLater() || mMediaRouter == null) return;

        if(add)
            mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        else
            mMediaRouter.removeCallback(mMediaRouterCallback);
    }

    private void startPlayback() {
        loadMedia();

        /*
         * if the activity has been paused by pressing the power button,
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in the background.
         * To workaround that, pause playback if the lockscreen is displayed
         */
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mLibVLC != null && mLibVLC.isPlaying()) {
                    KeyguardManager km = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
                    if (km.inKeyguardRestrictedInputMode())
                        mLibVLC.pause();
                }
            }}, 500);

        // Add any selected subtitle file from the file picker
        if(mSubtitleSelectedFiles.size() > 0) {
            for(String file : mSubtitleSelectedFiles) {
                Log.i(TAG, "Adding user-selected subtitle " + file);
                mLibVLC.addSubtitleTrack(file);
            }
        }
    }

    public static void start(Context context, String location) {
        start(context, location, null, -1, false, false);
    }

    public static void start(Context context, String location, Boolean fromStart) {
        start(context, location, null, -1, false, fromStart);
    }

    public static void start(Context context, String location, String title, Boolean dontParse) {
        start(context, location, title, -1, dontParse, false);
    }

    public static void start(Context context, String location, String title, int position, Boolean dontParse) {
        start(context, location, title, position, dontParse, false);
    }

    public static void start(Context context, String location, String title, int position, Boolean dontParse, Boolean fromStart) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.setAction(VideoPlayerActivity.PLAY_FROM_VIDEOGRID);
        intent.putExtra("itemLocation", location);
        intent.putExtra("itemTitle", title);
        intent.putExtra("dontParse", dontParse);
        intent.putExtra("fromStart", fromStart);
        intent.putExtra("itemPosition", position);

        if (dontParse)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(intent);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)) {
                if (mOverlayUseStatusBar)
                    return;
                int batteryLevel = intent.getIntExtra("level", 0);
                if (batteryLevel >= 50)
                    mBattery.setTextColor(Color.GREEN);
                else if (batteryLevel >= 30)
                    mBattery.setTextColor(Color.YELLOW);
                else
                    mBattery.setTextColor(Color.RED);
                mBattery.setText(String.format("%d%%", batteryLevel));
            }
            else if (action.equalsIgnoreCase(PopcornApplication.SLEEP_INTENT)) {
                finish();
            }
        }
    };

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        showOverlay();
        return true;
    }

    @TargetApi(12) //only active for Android 3.1+
    public boolean dispatchGenericMotionEvent(MotionEvent event){

        InputDevice mInputDevice = event.getDevice();

        float x = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X);
        float y = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y);
        float z = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Z);
        float rz = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RZ);

        if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY){
            if (Math.abs(x) > 0.3){
                seek(x > 0.0f ? 10000 : -10000);
                mLastMove = System.currentTimeMillis();
            } else if (Math.abs(y) > 0.3){
                if (mIsFirstBrightnessGesture)
                    initBrightnessTouch();
                changeBrightness(-y/10f);
                mLastMove = System.currentTimeMillis();
            } else if (Math.abs(rz) > 0.3){
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int delta = -(int) ((rz / 7) * mAudioMax);
                int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
                setAudioVolume(vol);
                mLastMove = System.currentTimeMillis();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        showOverlay(OVERLAY_TIMEOUT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_BUTTON_R1:
                seek(10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_BUTTON_L1:
                seek(-10000);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_BUTTON_A:
                doPlayPause();
                return true;
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_BUTTON_Y:
                selectSubtitles();
                return true;
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
            case KeyEvent.KEYCODE_BUTTON_B:
                selectAudioTrack();
                return true;
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_MENU:
                showNavMenu();
                return true;
            case KeyEvent.KEYCODE_O:
                showAdvancedOptions(mMenu);
                return true;
            case KeyEvent.KEYCODE_A:
                resizeVideo();
                return true;
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_BUTTON_X:
                updateMute();
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                finish();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setSurfaceSize(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
        if (width * height == 0)
            return;

        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        mVideoVisibleHeight = visible_height;
        mVideoVisibleWidth  = visible_width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        Message msg = mHandler.obtainMessage(SURFACE_SIZE);
        mHandler.sendMessage(msg);
    }

    /**
     * Lock screen rotation
     */
    private void lockScreen() {
        if(mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                setRequestedOrientation(14 /* SCREEN_ORIENTATION_LOCKED */);
            else
                setRequestedOrientation(getScreenOrientation());
        }
        showInfo("Locked", 1000);
        mLock.setBackgroundResource(R.drawable.ic_action_switch);
        mTime.setEnabled(false);
        mSeekbar.setEnabled(false);
        mLength.setEnabled(false);
        hideOverlay(true);
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        if(mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        showInfo("Unlocked", 1000);
        mLock.setBackgroundResource(R.drawable.ic_action_switch);
        mTime.setEnabled(true);
        mSeekbar.setEnabled(true);
        mLength.setEnabled(true);
        mShowing = false;
        showOverlay();
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    private void showInfo(String text, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void showInfo(int textid, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    /**
     * Show text in the info view
     * @param text
     */
    private void showInfo(String text) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
    private void hideInfo(int delay) {
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay);
    }

    /**
     * hide the info view
     */
    private void hideInfo() {
        hideInfo(0);
    }

    private void fadeOutInfo() {
        if (mInfo.getVisibility() == View.VISIBLE)
            mInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
        mInfo.setVisibility(View.INVISIBLE);
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private int changeAudioFocus(boolean acquire) {
        if(!LibVlcUtil.isFroyoOrLater()) // NOP if not supported
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

        if (mAudioFocusListener == null) {
            mAudioFocusListener = new OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    /*
                     * Pause playback during alerts and notifications
                     */
                    switch (focusChange)
                    {
                        case AudioManager.AUDIOFOCUS_LOSS:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if (mLibVLC.isPlaying())
                                mLibVLC.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            if (!mLibVLC.isPlaying())
                                mLibVLC.play();
                            break;
                    }
                }
            };
        }

        int result;
        if(acquire) {
            result = mAudioManager.requestAudioFocus(mAudioFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            mAudioManager.setParameters("bgm_state=true");
        }
        else {
            if (mAudioManager != null) {
                result = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                mAudioManager.setParameters("bgm_state=false");
            }
            else
                result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }

        return result;
    }

    /**
     *  Handle libvlc asynchronous events
     */
    private final Handler eventHandler = new VideoPlayerEventHandler(this);

    private static class VideoPlayerEventHandler extends WeakHandler<VideoPlayerActivity> {
        public VideoPlayerEventHandler(VideoPlayerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerActivity activity = getOwner();
            if(activity == null) return;
            // Do not handle events if we are leaving the VideoPlayerActivity
            if (activity.mSwitchingView) return;

            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaParsedChanged:
                    Log.i(TAG, "MediaParsedChanged");
                    activity.updateNavStatus();
                    if (!activity.mHasMenu && activity.mLibVLC.getVideoTracksCount() < 1) {
                        Log.i(TAG, "No video track, open in audio mode");
                        activity.switchToAudioMode();
                    }
                    break;
                case EventHandler.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");
                    activity.stopLoadingAnimation();
                    activity.showOverlay();
                    /** FIXME: update the track list when it changes during the
                     *  playback. (#7540) */
                    activity.setESTrackLists(true);
                    activity.setESTracks();
                    activity.changeAudioFocus(true);
                    activity.updateNavStatus();
                    break;
                case EventHandler.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    break;
                case EventHandler.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    activity.changeAudioFocus(false);
                    break;
                case EventHandler.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    activity.changeAudioFocus(false);
                    activity.endReached();
                    break;
                case EventHandler.MediaPlayerVout:
                    activity.updateNavStatus();
                    if (!activity.mHasMenu)
                        activity.handleVout(msg);
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    if (!activity.mCanSeek)
                        activity.mCanSeek = true;
                    //don't spam the logs
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    Log.i(TAG, "MediaPlayerEncounteredError");
                    activity.encounteredError();
                    break;
                case EventHandler.HardwareAccelerationError:
                    Log.i(TAG, "HardwareAccelerationError");
                    activity.handleHardwareAccelerationError();
                    break;
                case EventHandler.MediaPlayerTimeChanged:
                    // avoid useless error logs
                    break;
                default:
                    Log.e(TAG, String.format("Event not handled (0x%x)", msg.getData().getInt("event")));
                    break;
            }
            activity.updateOverlayPausePlay();
        }
    };

    /**
     * Handle resize of the surface and the overlay
     */
    private final Handler mHandler = new VideoPlayerHandler(this);

    private static class VideoPlayerHandler extends WeakHandler<VideoPlayerActivity> {
        public VideoPlayerHandler(VideoPlayerActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerActivity activity = getOwner();
            if(activity == null) // WeakReference could be GC'ed early
                return;

            switch (msg.what) {
                case FADE_OUT:
                    activity.hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    int pos = activity.setOverlayProgress();
                    if (activity.canShowProgress()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SURFACE_SIZE:
                    activity.changeSurfaceSize();
                    break;
                case FADE_OUT_INFO:
                    activity.fadeOutInfo();
                    break;
                case AUDIO_SERVICE_CONNECTION_SUCCESS:
                    activity.startPlayback();
                    break;
                case AUDIO_SERVICE_CONNECTION_FAILED:
                    activity.finish();
                    break;
            }
        }
    };

    private boolean canShowProgress() {
        return !mDragging && mShowing && mLibVLC.isPlaying();
    }

    private void endReached() {
        if(mLibVLC.getMediaList().expandMedia(savedIndexPosition) == 0) {
            Log.d(TAG, "Found a video playlist, expanding it");
            eventHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadMedia();
                }
            }, 1000);
        } else {
            /* Exit player when reaching the end */
            mEndReached = true;
            finish();
        }
    }

    private void encounteredError() {
        /* Encountered Error, exit player with a message */
        AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .setTitle("Encountered error")
                .setMessage("Encountered error")
                .create();
        dialog.show();
    }

    public void eventHardwareAccelerationError() {
        EventHandler em = EventHandler.getInstance();
        em.callback(EventHandler.HardwareAccelerationError, new Bundle());
    }

    private void handleHardwareAccelerationError() {
        mLibVLC.stop();
        AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mDisabledHardwareAcceleration = true;
                        mPreviousHardwareAccelerationMode = mLibVLC.getHardwareAcceleration();
                        mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
                        mSubtitlesSurface.setVisibility(View.INVISIBLE);
                        loadMedia();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .setTitle(R.string.hardware_acceleration_error_title)
                .setMessage(R.string.hardware_acceleration_error_message)
                .create();
        if(!isFinishing())
            dialog.show();
    }

    private void handleVout(Message msg) {
        if (msg.getData().getInt("data") == 0 && !mEndReached) {
            /* Video track lost, open in audio mode */
            Log.i(TAG, "Video track lost, switching to audio");
            mSwitchingView = true;
            finish();
        }
    }

    private void switchToAudioMode() {
        mSwitchingView = true;
        // Show the MainActivity if it is not in background.
        if (getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            Intent i = new Intent(this, OverviewActivity.class);
            startActivity(i);
        }
        finish();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceSize() {
        int sw;
        int sh;

        // get screen size
        if (mPresentation == null) {
            sw = getWindow().getDecorView().getWidth();
            sh = getWindow().getDecorView().getHeight();
        } else {
            sw = mPresentation.getWindow().getDecorView().getWidth();
            sh = mPresentation.getWindow().getDecorView().getHeight();
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        if (mPresentation == null) {
            // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
            isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        } else {
            isPortrait = false;
        }

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // sanity check
        if (dw * dh == 0 || mVideoWidth * mVideoHeight == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double)mVideoVisibleWidth / (double)mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double)mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_HORIZONTAL:
                dh = dw / ar;
                break;
            case SURFACE_FIT_VERTICAL:
                dw = dh * ar;
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        SurfaceHolder surfaceHolder;
        SurfaceHolder subtitlesSurfaceHolder;
        FrameLayout surfaceFrame;

        if (mPresentation == null) {
            surface = mSurface;
            subtitlesSurface = mSubtitlesSurface;
            surfaceHolder = mSurfaceHolder;
            subtitlesSurfaceHolder = mSubtitlesSurfaceHolder;
            surfaceFrame = mSurfaceFrame;
        } else {
            surface = mPresentation.mSurface;
            subtitlesSurface = mPresentation.mSubtitlesSurface;
            surfaceHolder = mPresentation.mSurfaceHolder;
            subtitlesSurfaceHolder = mPresentation.mSubtitlesSurfaceHolder;
            surfaceFrame = mPresentation.mSurfaceFrame;
        }

        // force surface buffer size
        surfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
        subtitlesSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        LayoutParams lp = surface.getLayoutParams();
        lp.width  = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        surface.setLayoutParams(lp);
        subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        subtitlesSurface.invalidate();
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsLocked) {
            // locked, only handle show/hide & ignore all actions
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay(true);
                }
            }
            return false;
        }

        DisplayMetrics screen = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(screen);

        if (mSurfaceYDisplayRange == 0)
            mSurfaceYDisplayRange = Math.min(screen.widthPixels, screen.heightPixels);

        float y_changed = event.getRawY() - mTouchY;
        float x_changed = event.getRawX() - mTouchX;

        // coef is the gradient's move to determine a neutral zone
        float coef = Math.abs (y_changed / x_changed);
        float xgesturesize = ((x_changed / screen.xdpi) * 2.54f);

        /* Offset for Mouse Events */
        int[] offset = new int[2];
        mSurface.getLocationOnScreen(offset);
        int xTouch = Math.round((event.getRawX() - offset[0]) * mVideoWidth / mSurface.getWidth());
        int yTouch = Math.round((event.getRawY() - offset[1]) * mVideoHeight / mSurface.getHeight());

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                // Audio
                mTouchY = event.getRawY();
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                mTouchAction = TOUCH_NONE;
                // Seek
                mTouchX = event.getRawX();
                // Mouse events for the core
                LibVLC.sendMouseEvent(MotionEvent.ACTION_DOWN, 0, xTouch, yTouch);
                break;

            case MotionEvent.ACTION_MOVE:
                // Mouse events for the core
                LibVLC.sendMouseEvent(MotionEvent.ACTION_MOVE, 0, xTouch, yTouch);

                // No volume/brightness action if coef < 2 or a secondary display is connected
                //TODO : Volume action when a secondary display is connected
                if (coef > 2 && mPresentation == null) {
                    // Volume (Up or Down - Right side)
                    if (!mEnableBrightnessGesture || (int)mTouchX > (screen.widthPixels / 2)){
                        doVolumeTouch(y_changed);
                    }
                    // Brightness (Up or Down - Left side)
                    if (mEnableBrightnessGesture && (int)mTouchX < (screen.widthPixels / 2)){
                        doBrightnessTouch(y_changed);
                    }
                    // Extend the overlay for a little while, so that it doesn't
                    // disappear on the user if more adjustment is needed. This
                    // is because on devices with soft navigation (e.g. Galaxy
                    // Nexus), gestures can't be made without activating the UI.
                    if(AndroidDevices.hasNavBar())
                        showOverlay();
                }
                // Seek (Right or Left move)
                doSeekTouch(coef, xgesturesize, false);
                break;

            case MotionEvent.ACTION_UP:
                // Mouse events for the core
                LibVLC.sendMouseEvent(MotionEvent.ACTION_UP, 0, xTouch, yTouch);

                // Audio or Brightness
                if ( mTouchAction == TOUCH_NONE) {
                    if (!mShowing) {
                        showOverlay();
                    } else {
                        hideOverlay(true);
                    }
                }
                // Seek
                doSeekTouch(coef, xgesturesize, true);
                break;
        }
        return mTouchAction != TOUCH_NONE;
    }

    private void doSeekTouch(float coef, float gesturesize, boolean seek) {
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (coef > 0.5 || Math.abs(gesturesize) < 1 || !mCanSeek)
            return;

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
            return;
        mTouchAction = TOUCH_SEEK;

        // Always show seekbar when searching
        if (!mShowing) showOverlay();

        long length = mLibVLC.getLength();
        long time = mLibVLC.getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        int jump = (int) (Math.signum(gesturesize) * ((600000 * Math.pow((gesturesize / 8), 4)) + 3000));

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        //Jump !
        if (seek && length > 0)
            mLibVLC.setTime(time + jump);

        if (length > 0)
            //Show the jump's size
            showInfo(String.format("%s%s (%s)",
                    jump >= 0 ? "+" : "",
                    StringUtils.millisToString(jump),
                    StringUtils.millisToString(time + jump)), 1000);
        else
            showInfo("Unseekable stream", 1000);
    }

    private void doVolumeTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
            return;
        int delta = -(int) ((y_changed / mSurfaceYDisplayRange) * mAudioMax);
        int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
        if (delta != 0) {
            setAudioVolume(vol);
        }
    }

    private void setAudioVolume(int vol) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
        mTouchAction = TOUCH_VOLUME;
        showInfo(getString(R.string.volume) + '\u00A0' + Integer.toString(vol),1000);
    }

    private void updateMute () {
        if (!mMute) {
            mVolSave = mVol;
            mMute = true;
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
            showInfo("Sound off",1000);
        } else {
            mVol = mVolSave;
            mMute = false;
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mVol, 0);
            showInfo("Sound on",1000);
        }
    }

    private void initBrightnessTouch() {
        float brightnesstemp = 0.01f;
        // Initialize the layoutParams screen brightness
        try {
            brightnesstemp = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
        } catch (SettingNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightnesstemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
            return;
        if (mIsFirstBrightnessGesture) initBrightnessTouch();
        mTouchAction = TOUCH_BRIGHTNESS;

        // Set delta : 0.07f is arbitrary for now, it possibly will change in the future
        float delta = - y_changed / mSurfaceYDisplayRange * 0.07f;

        changeBrightness(delta);
    }

    private void changeBrightness(float delta) {
        // Estimate and adjust Brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness =  Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1);
        // Set Brightness
        getWindow().setAttributes(lp);
        showInfo(getString(R.string.brightness) + '\u00A0' + Math.round(lp.screenBrightness*15),1000);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlay(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay();
            hideInfo();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && mCanSeek) {
                mLibVLC.setTime(progress);
                setOverlayProgress();
                mTime.setText(StringUtils.millisToString(progress));
                showInfo(StringUtils.millisToString(progress));
            }

        }
    };

    /**
     *
     */
    private final OnClickListener mAudioTrackListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            selectAudioTrack();
        }
    };

    private void selectAudioTrack() {
        if (mAudioTracksList == null) return;

        final String[] arrList = new String[mAudioTracksList.size()];
        int i = 0;
        int listPosition = 0;
        for(Map.Entry<Integer,String> entry : mAudioTracksList.entrySet()) {
            arrList[i] = entry.getValue();
            // map the track position to the list position
            if(entry.getKey() == mLibVLC.getAudioTrack())
                listPosition = i;
            i++;
        }
        AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setTitle("Audio track")
                .setSingleChoiceItems(arrList, listPosition, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int listPosition) {
                        /*int trackID = -1;
                        // Reverse map search...
                        for(Map.Entry<Integer, String> entry : mAudioTracksList.entrySet()) {
                            if(arrList[listPosition].equals(entry.getValue())) {
                                trackID = entry.getKey();
                                break;
                            }
                        }
                        if(trackID < 0) return;

                        MediaDatabase.getInstance().updateMedia(
                                mLocation,
                                MediaDatabase.mediaColumn.MEDIA_AUDIOTRACK,
                                trackID);
                        mLibVLC.setAudioTrack(trackID);
                        dialog.dismiss();*/
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOwnerActivity(VideoPlayerActivity.this);
        dialog.show();
    }

    /**
     *
     */
    private final OnClickListener mSubtitlesListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            selectSubtitles();
        }
    };

    private void selectSubtitles() {
        final String[] arrList = new String[mSubtitleTracksList.size()];
        int i = 0;
        int listPosition = 0;
        for(Map.Entry<Integer,String> entry : mSubtitleTracksList.entrySet()) {
            arrList[i] = entry.getValue();
            // map the track position to the list position
            if(entry.getKey() == mLibVLC.getSpuTrack())
                listPosition = i;
            i++;
        }

        AlertDialog dialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setTitle("Text track")
                .setSingleChoiceItems(arrList, listPosition, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int listPosition) {
                        /*int trackID = -2;
                        // Reverse map search...
                        for(Map.Entry<Integer, String> entry : mSubtitleTracksList.entrySet()) {
                            if(arrList[listPosition].equals(entry.getValue())) {
                                trackID = entry.getKey();
                                break;
                            }
                        }
                        if(trackID < -1) return;

                        MediaDatabase.getInstance().updateMedia(
                                mLocation,
                                MediaDatabase.mediaColumn.MEDIA_SPUTRACK,
                                trackID);
                        mLibVLC.setSpuTrack(trackID);
                        dialog.dismiss();*/
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOwnerActivity(VideoPlayerActivity.this);
        dialog.show();
    }

    private final OnClickListener mNavMenuListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            showNavMenu();
        }
    };

    private void showNavMenu() {
        /* Try to return to the menu. */
        /* FIXME: not working correctly in all cases */
        mLibVLC.setTitle(0);
    }

    /**
     *
     */
    private final OnClickListener mPlayPauseListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            doPlayPause();
        }
    };

    private final void doPlayPause() {
        if (mLibVLC.isPlaying())
            pause();
        else
            play();
        showOverlay();
    }

    /**
     *
     */
    private final OnClickListener mBackwardListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            seek(-10000);
        }
    };

    /**
     *
     */
    private final OnClickListener mForwardListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            seek(10000);
        }
    };

    public void seek(int delta) {
        // unseekable stream
        if(mLibVLC.getLength() <= 0 || !mCanSeek) return;

        long position = mLibVLC.getTime() + delta;
        if (position < 0) position = 0;
        mLibVLC.setTime(position);
        showOverlay();
    }

    /**
     *
     */
    private final OnClickListener mLockListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mIsLocked) {
                mIsLocked = false;
                unlockScreen();
            } else {
                mIsLocked = true;
                lockScreen();
            }
        }
    };

    /**
     *
     */
    private final OnClickListener mSizeListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            resizeVideo();
        }
    };

    private void resizeVideo() {
        if (mCurrentSize < SURFACE_ORIGINAL) {
            mCurrentSize++;
        } else {
            mCurrentSize = 0;
        }
        changeSurfaceSize();
        showOverlay();
    }

    private final OnClickListener mRemainingTimeListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mDisplayRemainingTime = !mDisplayRemainingTime;
            showOverlay();
        }
    };

    /**
     * attach and disattach surface to the lib
     */
    private final SurfaceHolder.Callback mSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(format == PixelFormat.RGBX_8888)
                Log.d(TAG, "Pixel format is RGBX_8888");
            else if(format == PixelFormat.RGB_565)
                Log.d(TAG, "Pixel format is RGB_565");
            else if(format == ImageFormat.YV12)
                Log.d(TAG, "Pixel format is YV12");
            else
                Log.d(TAG, "Pixel format is other/unknown");
            if(mLibVLC != null)
                mLibVLC.attachSurface(holder.getSurface(), VideoPlayerActivity.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if(mLibVLC != null)
                mLibVLC.detachSurface();
        }
    };

    private final SurfaceHolder.Callback mSubtitlesSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(mLibVLC != null)
                mLibVLC.attachSubtitlesSurface(holder.getSurface());
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if(mLibVLC != null)
                mLibVLC.detachSubtitlesSurface();
        }
    };

    /**
     * show overlay the the default timeout
     */
    private void showOverlay() {
        showOverlay(OVERLAY_TIMEOUT);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setActionBarVisibility(boolean show) {
        if (show)
            mActionBar.show();
        else
            mActionBar.hide();
    }

    /**
     * show overlay
     */
    private void showOverlay(int timeout) {
        if (mIsNavMenu)
            return;
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            if (!mIsLocked) {
                if (mOverlayUseStatusBar)
                    setActionBarVisibility(true);
                else
                    mOverlayHeader.setVisibility(View.VISIBLE);
                mOverlayOption.setVisibility(View.VISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mMenu.setVisibility(View.VISIBLE);
                dimStatusBar(false);
            }
            mOverlayProgress.setVisibility(View.VISIBLE);
            if (mPresentation != null) mOverlayBackground.setVisibility(View.VISIBLE);
        }
        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
        updateOverlayPausePlay();
    }


    /**
     * hider overlay
     */
    private void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            Log.i(TAG, "remove View!");
            if (mOverlayTips != null) mOverlayTips.setVisibility(View.INVISIBLE);
            if (!fromUser && !mIsLocked) {
                if (!mOverlayUseStatusBar)
                    mOverlayHeader.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayOption.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayProgress.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mPlayPause.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mMenu.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            }
            if (mPresentation != null) {
                mOverlayBackground.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayBackground.setVisibility(View.INVISIBLE);
            }
            if (mOverlayUseStatusBar)
                setActionBarVisibility(false);
            else
                mOverlayHeader.setVisibility(View.INVISIBLE);
            mOverlayOption.setVisibility(View.INVISIBLE);
            mOverlayProgress.setVisibility(View.INVISIBLE);
            mPlayPause.setVisibility(View.INVISIBLE);
            mMenu.setVisibility(View.INVISIBLE);
            mShowing = false;
            dimStatusBar(true);
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void dimStatusBar(boolean dim) {
        if (!LibVlcUtil.isHoneycombOrLater() || !AndroidDevices.hasNavBar() || mIsNavMenu)
            return;
        int layout = 0;
        if (!AndroidDevices.hasCombBar() && LibVlcUtil.isJellyBeanOrLater())
            layout = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (mOverlayUseStatusBar)
            layout |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;

        int visibility = layout;
        if (dim) {
            visibility |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (!AndroidDevices.hasCombBar()) {
                visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                if (mOverlayUseStatusBar)
                    visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
        } else {
            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
        }
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    private void updateOverlayPausePlay() {
        if (mLibVLC == null)
            return;

        if (mPresentation == null)
            mPlayPause.setBackgroundResource(mLibVLC.isPlaying() ? R.drawable.ic_av_pause
                    : R.drawable.ic_av_play);
        else
            mPlayPause.setBackgroundResource(mLibVLC.isPlaying() ? R.drawable.play_button_circle
                    : R.drawable.play_button_circle);
    }

    /**
     * update the overlay
     */
    private int setOverlayProgress() {
        if (mLibVLC == null) {
            return 0;
        }
        int time = (int) mLibVLC.getTime();
        int length = (int) mLibVLC.getLength();

        // Update all view elements
        boolean isSeekable = mEnableJumpButtons && length > 0;
        mBackward.setVisibility(isSeekable ? View.VISIBLE : View.GONE);
        mForward.setVisibility(isSeekable ? View.VISIBLE : View.GONE);
        mSeekbar.setMax(length);
        mSeekbar.setProgress(time);
        if (!mOverlayUseStatusBar)
            mSysTime.setText(DateFormat.getTimeFormat(this).format(new Date(System.currentTimeMillis())));
        if (time >= 0) mTime.setText(StringUtils.millisToString(time));
        if (length >= 0) mLength.setText(mDisplayRemainingTime && length > 0
                ? "- " + StringUtils.millisToString(length - time)
                : StringUtils.millisToString(length));

        return time;
    }

    private void setESTracks() {
        if (mLastAudioTrack >= 0) {
            mLibVLC.setAudioTrack(mLastAudioTrack);
            mLastAudioTrack = -1;
        }
        if (mLastSpuTrack >= -1) {
            mLibVLC.setSpuTrack(mLastSpuTrack);
            mLastSpuTrack = -2;
        }
    }

    private void setESTrackLists(boolean force) {
        if(mAudioTracksList == null || force) {
            if (mLibVLC.getAudioTracksCount() > 2) {
                mAudioTracksList = mLibVLC.getAudioTrackDescription();
                mAudioTrack.setOnClickListener(mAudioTrackListener);
                mAudioTrack.setVisibility(View.VISIBLE);
            }
            else {
                mAudioTrack.setVisibility(View.GONE);
                mAudioTrack.setOnClickListener(null);
            }
        }
        if (mSubtitleTracksList == null || force) {
            if (mLibVLC.getSpuTracksCount() > 0) {
                mSubtitleTracksList = mLibVLC.getSpuTrackDescription();
                mSubtitle.setOnClickListener(mSubtitlesListener);
                mSubtitle.setVisibility(View.VISIBLE);
            }
            else {
                mSubtitle.setVisibility(View.GONE);
                mSubtitle.setOnClickListener(null);
            }
        }
    }


    /**
     *
     */
    private void play() {
        mLibVLC.play();
        mSurface.setKeepScreenOn(true);
    }

    /**
     *
     */
    private void pause() {
        mLibVLC.pause();
        mSurface.setKeepScreenOn(false);
    }

    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     */
    @SuppressWarnings({ "unchecked" })
    private void loadMedia() {
        mLocation = null;
        String title = "Title";
        boolean dontParse = false;
        boolean fromStart = false;
        String itemTitle = null;
        int itemPosition = -1; // Index in the media list as passed by AudioServer (used only for vout transition internally)
        long intentPosition = -1; // position passed in by intent (ms)

        if(getIntent().getExtras().containsKey(LOCATION)) {
            mLocation = getIntent().getStringExtra(LOCATION);
        }

        mSurface.setKeepScreenOn(true);

        if(mLibVLC == null)
            return;

        /* WARNING: hack to avoid a crash in mediacodec on KitKat.
         * Disable hardware acceleration if the media has a ts extension. */
        if (mLocation != null && LibVlcUtil.isKitKatOrLater()) {
            String locationLC = mLocation.toLowerCase(Locale.ENGLISH);
            if (locationLC.endsWith(".ts")
                    || locationLC.endsWith(".tts")
                    || locationLC.endsWith(".m2t")
                    || locationLC.endsWith(".mts")
                    || locationLC.endsWith(".m2ts")) {
                mDisabledHardwareAcceleration = true;
                mPreviousHardwareAccelerationMode = mLibVLC.getHardwareAcceleration();
                mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            }
        }

        /* Start / resume playback */
        if(dontParse && itemPosition >= 0) {
            // Provided externally from AudioService
            Log.d(TAG, "Continuing playback from AudioService at index " + itemPosition);
            savedIndexPosition = itemPosition;
            if(!mLibVLC.isPlaying()) {
                // AudioService-transitioned playback for item after sleep and resume
                mLibVLC.playIndex(savedIndexPosition);
                dontParse = false;
            }
            else {
                stopLoadingAnimation();
                showOverlay();
            }
            updateNavStatus();
        } else if (savedIndexPosition > -1) {
            AudioServiceController.getInstance().stop(); // Stop the previous playback.
            mLibVLC.setMediaList();
            mLibVLC.playIndex(savedIndexPosition);
        } else if (mLocation != null && mLocation.length() > 0 && !dontParse) {
            AudioServiceController.getInstance().stop(); // Stop the previous playback.
            mLibVLC.setMediaList();
            mLibVLC.getMediaList().add(new Media(mLibVLC, mLocation));
            savedIndexPosition = mLibVLC.getMediaList().size() - 1;
            mLibVLC.playIndex(savedIndexPosition);
        }
        mCanSeek = false;


        mTitle.setText(title);
    }

    @SuppressWarnings("deprecation")
    private int getScreenRotation(){
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO /* Android 2.2 has getRotation */) {
            try {
                Method m = display.getClass().getDeclaredMethod("getRotation");
                return (Integer) m.invoke(display);
            } catch (Exception e) {
                return Surface.ROTATION_0;
            }
        } else {
            return display.getOrientation();
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private int getScreenOrientation(){
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int rot = getScreenRotation();
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        @SuppressWarnings("deprecation")
        boolean defaultWide = display.getWidth() > display.getHeight();
        if(rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide;
        if(defaultWide) {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                default:
                    return 0;
            }
        } else {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                default:
                    return 0;
            }
        }
    }

    public void showAdvancedOptions(View v) {
        //CommonDialogs.advancedOptions(this, v, MenuType.Video);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void createPresentation() {
        if (mMediaRouter == null || mEnableCloneMode)
            return;

        // Get the current route and its presentation display.
        MediaRouter.RouteInfo route = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

        Display presentationDisplay = route != null ? route.getPresentationDisplay() : null;

        if (presentationDisplay != null) {
            // Show a new presentation if possible.
            Log.i(TAG, "Showing presentation on display: " + presentationDisplay);
            mPresentation = new SecondaryDisplay(this, presentationDisplay);
            mPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mPresentation.show();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mPresentation = null;
            }
        } else
            Log.i(TAG, "No secondary display detected");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void removePresentation() {
        if (mMediaRouter == null)
            return;

        // Dismiss the current presentation if the display has changed.
        Log.i(TAG, "Dismissing presentation because the current route no longer "
                + "has a presentation display.");
        mLibVLC.pause(); // Stop sending frames to avoid a crash.
        finish(); //TODO restore the video on the new display instead of closing
        if (mPresentation != null) mPresentation.dismiss();
        mPresentation = null;
    }

    /**
     * Listens for when presentations are dismissed.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (dialog == mPresentation) {
                Log.i(TAG, "Presentation was dismissed.");
                mPresentation = null;
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private final class SecondaryDisplay extends Presentation {
        public final static String TAG = "VLC/SecondaryDisplay";

        private SurfaceView mSurface;
        private SurfaceView mSubtitlesSurface;
        private SurfaceHolder mSurfaceHolder;
        private SurfaceHolder mSubtitlesSurfaceHolder;
        private FrameLayout mSurfaceFrame;
        private LibVLC mLibVLC;

        public SecondaryDisplay(Context context, Display display) {
            super(context, display);
            if (context instanceof Activity) {
                setOwnerActivity((Activity) context);
            }
            try {
                mLibVLC = VLCInstance.getLibVlcInstance();
            } catch (LibVlcException e) {
                Log.d(TAG, "LibVLC initialisation failed");
                return;
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_player_remote);

            mSurface = (SurfaceView) findViewById(R.id.remote_player_surface);
            mSurfaceHolder = mSurface.getHolder();
            mSurfaceFrame = (FrameLayout) findViewById(R.id.remote_player_surface_frame);
            String chroma = mSettings.getString("chroma_format", "");
            if(LibVlcUtil.isGingerbreadOrLater() && chroma.equals("YV12")) {
                mSurfaceHolder.setFormat(ImageFormat.YV12);
            } else if (chroma.equals("RV16")) {
                mSurfaceHolder.setFormat(PixelFormat.RGB_565);
            } else {
                mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
            }

            VideoPlayerActivity activity = (VideoPlayerActivity)getOwnerActivity();
            if (activity == null) {
                Log.e(TAG, "Failed to get the VideoPlayerActivity instance, secondary display won't work");
                return;
            }

            mSurfaceHolder.addCallback(activity.mSurfaceCallback);

            mSubtitlesSurface = (SurfaceView) findViewById(R.id.remote_subtitles_surface);
            mSubtitlesSurfaceHolder = mSubtitlesSurface.getHolder();
            mSubtitlesSurfaceHolder.setFormat(PixelFormat.RGBA_8888);
            mSubtitlesSurface.setZOrderMediaOverlay(true);
            mSubtitlesSurfaceHolder.addCallback(activity.mSubtitlesSurfaceCallback);

            /* Only show the subtitles surface when using "Full Acceleration" mode */
            if (mLibVLC != null && mLibVLC.getHardwareAcceleration() == LibVLC.HW_ACCELERATION_FULL)
                mSubtitlesSurface.setVisibility(View.VISIBLE);
            Log.i(TAG, "Secondary display created");
        }
    }

    /**
     * Start the video loading animation.
     */
    private void startLoadingAnimation() {
        AnimationSet anim = new AnimationSet(true);
        RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(800);
        rotate.setInterpolator(new DecelerateInterpolator());
        rotate.setRepeatCount(RotateAnimation.INFINITE);
        anim.addAnimation(rotate);
        mLoading.startAnimation(anim);
        mLoadingText.setVisibility(View.VISIBLE);
    }

    /**
     * Stop the video loading animation.
     */
    private void stopLoadingAnimation() {
        mLoading.setVisibility(View.INVISIBLE);
        mLoading.clearAnimation();
        mLoadingText.setVisibility(View.GONE);
    }

    public void onClickOverlayTips(View v) {
        mOverlayTips.setVisibility(View.GONE);
    }

    public void onClickDismissTips(View v) {
        mOverlayTips.setVisibility(View.GONE);
        Editor editor = mSettings.edit();
        editor.putBoolean(PREF_TIPS_SHOWN, true);
        editor.commit();
    }

    private void updateNavStatus() {
        mHasMenu = mLibVLC.getChapterCountForTitle(0) > 1 && mLibVLC.getTitleCount() > 1;
        mIsNavMenu = mHasMenu && mLibVLC.getTitle() == 0;
        /***
         * HACK ALERT: assume that any media with >1 titles = DVD with menus
         * Should be replaced with a more robust title/chapter selection popup
         */

        Log.d(TAG,
                "updateNavStatus: getChapterCountForTitle(0) = "
                        + mLibVLC.getChapterCountForTitle(0)
                        + ", getTitleCount() = " + mLibVLC.getTitleCount());
        if (mIsNavMenu) {
            /*
             * Keep the overlay hidden in order to have touch events directly
             * transmitted to navigation handling.
             */
            hideOverlay(false);
        }
        else if (mHasMenu) {
            setESTrackLists(true);
            setESTracks();

            /* Show the return to menu button. */
            mNavMenu.setVisibility(View.VISIBLE);
            mNavMenu.setOnClickListener(mNavMenuListener);
        }
        else
            mNavMenu.setVisibility(View.GONE);

    }
}
