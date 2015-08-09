/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package pct.droid.fragments.base;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.connectsdk.device.ConnectableDevice;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import pct.droid.R;
import pct.droid.activities.BeamPlayerActivity;
import pct.droid.activities.VideoPlayerActivity;
import pct.droid.base.beaming.BeamDeviceListener;
import pct.droid.base.beaming.BeamManager;
import pct.droid.base.preferences.Prefs;
import pct.droid.base.providers.media.models.Media;
import pct.droid.base.providers.subs.SubsProvider;
import pct.droid.base.subs.Caption;
import pct.droid.base.subs.FormatSRT;
import pct.droid.base.subs.TimedTextObject;
import pct.droid.base.torrent.DownloadStatus;
import pct.droid.base.torrent.StreamInfo;
import pct.droid.base.torrent.Torrent;
import pct.droid.base.torrent.TorrentService;
import pct.droid.base.utils.FileUtils;
import pct.droid.base.utils.LocaleUtils;
import pct.droid.base.utils.PrefUtils;
import pct.droid.base.utils.ThreadUtils;
import pct.droid.dialogfragments.FileSelectorDialogFragment;
import pct.droid.dialogfragments.NumberPickerDialogFragment;
import pct.droid.dialogfragments.StringArraySelectorDialogFragment;
import timber.log.Timber;

public abstract class BaseVideoPlayerFragment extends Fragment implements IVideoPlayer, TorrentService.Listener {

    private Handler mHandler = new Handler();
    private LibVLC mLibVLC;
    private String mLocation;
    private Long mResumePosition;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;

    private int mStreamerProgress = 0;

    protected StreamInfo mStreamInfo;
    protected Media mMedia;
    private String mCurrentSubsLang = "no-subs";
    private TimedTextObject mSubs;
    private Caption mLastSub = null;
    private File mSubsFile = null;

    private boolean mEnded = false;
    private boolean mSeeking = false;
    private boolean mReadyToPlay = false;
    protected boolean mShowReload = false;

    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;
    private int mSubtitleOffset = 0;

    private boolean mDisabledHardwareAcceleration = false;
    private int mPreviousHardwareAccelerationMode;


    protected Callback mCallback;

    /**
     * Handle libvlc asynchronous events
     */
    private final Handler mVlcEventHandler = new VideoPlayerEventHandler(this);

    private View mRootView;
    private SurfaceHolder mVideoSurfaceHolder;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mRootView = super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getActivity() instanceof Callback && mCallback == null) mCallback = (Callback) getActivity();

        mResumePosition = mCallback.getResumePosition();
        mStreamInfo = mCallback.getInfo();
        mMedia = mStreamInfo.getMedia();

        //start subtitles
        if (null != mStreamInfo.getSubtitleLanguage()) {
            mCurrentSubsLang = mStreamInfo.getSubtitleLanguage();
            if (!mCurrentSubsLang.equals("no-subs")) {
                mSubsFile = new File(SubsProvider.getStorageLocation(getActivity()), mMedia.videoId + "-" + mCurrentSubsLang + ".srt");
                startSubtitles();
            }
        }


        mLibVLC = VLCInstance.get();
        mLibVLC.setHardwareAcceleration(PrefUtils.get(getActivity(), Prefs.HW_ACCELERATION, LibVLC.HW_ACCELERATION_AUTOMATIC));

        mVideoSurfaceHolder = getVideoSurface().getHolder();
        String chroma = PrefUtils.get(getActivity(), Prefs.PIXEL_FORMAT, "");
        switch (chroma) {
            case "YV12":
                mVideoSurfaceHolder.setFormat(ImageFormat.YV12);
                break;
            case "RV16":
                mVideoSurfaceHolder.setFormat(PixelFormat.RGB_565);
                break;
            default:
                mVideoSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
                break;
        }
        mVideoSurfaceHolder.addCallback(mSurfaceCallback);

        EventHandler em = EventHandler.getInstance();
        em.addHandler(mVlcEventHandler);

        Timber.d("Hardware acceleration mode: " + Integer.toString(mLibVLC.getHardwareAcceleration()));

        PrefUtils.save(getActivity(), VideoPlayerActivity.RESUME_POSITION, mResumePosition);

        if (mCallback.getService() != null)
            mCallback.getService().addListener(BaseVideoPlayerFragment.this);

        if (mReadyToPlay) {
            loadMedia();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof Callback) mCallback = (Callback) activity;
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mLibVLC != null) {
            long currentTime = mLibVLC.getTime();
            PrefUtils.save(getActivity(), VideoPlayerActivity.RESUME_POSITION, currentTime);

            /*
             * Pausing here generates errors because the vout is constantly
             * trying to refresh itself every 80ms while the surface is not
             * accessible anymore.
             * To workaround that, we keep the last known position in the preferences
             */
            mLibVLC.stop();
        }

        getVideoSurface().setKeepScreenOn(false);

        BeamManager.getInstance(getActivity()).removeDeviceListener(mDeviceListener);
    }

    @Override
    public void onResume() {
        super.onResume();

        resumeVideo();

        BeamManager.getInstance(getActivity()).addDeviceListener(mDeviceListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        EventHandler em = EventHandler.getInstance();
        em.removeHandler(mVlcEventHandler);

        // HW acceleration was temporarily disabled because of an error, restore the previous value.
        if (mDisabledHardwareAcceleration)
            mLibVLC.setHardwareAcceleration(mPreviousHardwareAccelerationMode);

        PrefUtils.save(getActivity(), VideoPlayerActivity.RESUME_POSITION, 0);
    }

    /**
     * External extras: - position (long) - position of the video to start with (in ms)
     */
    @SuppressWarnings({"unchecked"})
    public void loadMedia() {
        setProgressVisible(true);

        if (mStreamInfo != null && null != mStreamInfo.getVideoLocation()) {
            mLocation = mStreamInfo.getVideoLocation();
            if (!mLocation.startsWith("file://") && !mLocation.startsWith("http://") && !mLocation.startsWith("https://")) {
                mLocation = "file://" + mLocation;
            }
        } else {
            mReadyToPlay = true;
            return;
        }

        getVideoSurface().setKeepScreenOn(true);

        if (mLibVLC == null || mLibVLC.isPlaying() || mLocation == null || mLocation.isEmpty()) {
            mReadyToPlay = true;
            return;
        }

        if (!mLocation.startsWith("http"))
            mLocation = LibVLC.PathToURI(mLocation);

        Timber.d("Trying to play file: %s", mLocation);

        mLibVLC.playMRL(mLocation);
        mEnded = false;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mLibVLC.getLength() == 0) {
                    loadMedia();
                    setProgressVisible(true);
                }
            }
        }, 2000);

        long resumeTime = PrefUtils.get(getActivity(), VideoPlayerActivity.RESUME_POSITION, mResumePosition);
        if (resumeTime > 0) {
            mLibVLC.setTime(resumeTime);
        }
    }

	/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	 * abstract
	 * - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */

    protected abstract void setProgressVisible(boolean visible);

    protected abstract void showOverlay();

    protected abstract void showPlayerInfo(String info);

    protected abstract void onProgressChanged(long currentTime, long duration);

    protected abstract void updatePlayPauseState();

    protected abstract void onErrorEncountered();

    protected abstract void onHardwareAccelerationError();

    protected abstract void showTimedCaptionText(Caption text);

    protected abstract SurfaceView getVideoSurface();


	/* - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
	 * vlc methods
	 * - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - */


    protected void setSeeking(boolean seeking) {
        mSeeking = seeking;
    }

    protected boolean isSeeking() {
        return mSeeking;
    }

    private void resumeVideo() {
        if (mLibVLC == null)
            return;

        long resumePosition = PrefUtils.get(getActivity(), VideoPlayerActivity.RESUME_POSITION, 0);
        long length = mLibVLC.getLength();
        if (length > resumePosition && resumePosition > 0) {
            setCurrentTime(resumePosition);
            PrefUtils.save(getActivity(), VideoPlayerActivity.RESUME_POSITION, 0);
        }
    }

    private void play() {
        mLibVLC.play();
        getVideoSurface().setKeepScreenOn(true);

        resumeVideo();
    }

    private void pause() {
        mLibVLC.pause();
        getVideoSurface().setKeepScreenOn(false);
    }

    public void togglePlayPause() {
        if (mLibVLC == null)
            return;

        if (mEnded) {
            loadMedia();
        }

        if (mLibVLC.isPlaying()) {
            pause();
        } else {
            play();
        }
        updatePlayPauseState();
    }

    public void seekForwardClick() {
        seek(10000);
    }

    public void seekBackwardClick() {
        seek(-10000);
    }


    public void scaleClick() {
        if (mCurrentSize < SURFACE_ORIGINAL) {
            mCurrentSize++;
        } else {
            mCurrentSize = 0;
        }
        changeSurfaceSize(true);
        showOverlay();
    }

    protected void disableHardwareAcceleration() {
        mDisabledHardwareAcceleration = true;
        mPreviousHardwareAccelerationMode = getHardwareAccelerationMode();

        setHardwareAccelerationMode(LibVLC.HW_ACCELERATION_DISABLED);
    }

    protected void setCurrentTime(long time) {
        if (time / getDuration() * 100 <= getStreamerProgress()) {
            mLibVLC.setTime(time);
        }
    }

    protected long getCurrentTime() {
        return mLibVLC.getTime();
    }

    protected long getDuration() {
        return mLibVLC.getLength();
    }

    public int getStreamerProgress() {
        return mStreamerProgress;
    }

    /**
     * Is a video currently playing with VLC
     *
     * @return
     */
    protected boolean isPlaying() {
        if (null != mLibVLC && mLibVLC.isPlaying()) return true;
        return false;
    }

    // Required method for LibVLC
    public void eventHardwareAccelerationError() {
        EventHandler em = EventHandler.getInstance();
        em.callback(EventHandler.HardwareAccelerationError, new Bundle());
    }

    private void endReached() {
        mEnded = true;
		/* Exit player when reaching the end */
        // TODO: END, ASK USER TO CLOSE PLAYER?
    }

    public abstract void onPlaybackEndReached();

    private void handleHardwareAccelerationError() {
        mLibVLC.stop();
        onHardwareAccelerationError();
    }

    protected int getHardwareAccelerationMode() {
        return mLibVLC.getHardwareAcceleration();
    }

    private void setHardwareAccelerationMode(int mode) {
        mLibVLC.setHardwareAcceleration(mode);
    }

    @Override
    public void setSurfaceLayout(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
        if (width * height == 0)
            return;

        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        mVideoVisibleHeight = visible_height;
        mVideoVisibleWidth = visible_width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                changeSurfaceSize(false);

            }
        });
    }

    @Override
    public int configureSurface(Surface surface, final int width, final int height, final int hal) {
        return -1;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceSize(boolean message) {
        int sw = getActivity().getWindow().getDecorView().getWidth();
        int sh = getActivity().getWindow().getDecorView().getHeight();

        double dw = sw, dh = sh;

        if (sw < sh) {
            dw = sh;
            dh = sw;
        }

        // sanity check
        if (dw * dh == 0 || mVideoWidth * mVideoHeight == 0) {
            Timber.e("Invalid surface size");
            return;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
			/* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
			/* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;


        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (message) showPlayerInfo(getString(R.string.best_fit));
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_HORIZONTAL:
                dh = dw / ar;
                if (message) showPlayerInfo(getString(R.string.fit_horizontal));
                break;
            case SURFACE_FIT_VERTICAL:
                dw = dh * ar;
                if (message) showPlayerInfo(getString(R.string.fit_vertical));
                break;
            case SURFACE_FILL:
                if (message) showPlayerInfo(getString(R.string.fill));
                break;
            case SURFACE_16_9:
                if (message) showPlayerInfo("16:9");
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                if (message) showPlayerInfo("4:3");
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                if (message) showPlayerInfo(getString(R.string.original_size));
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        // force surface buffer size
        mVideoSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        ViewGroup.LayoutParams lp = getVideoSurface().getLayoutParams();
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        getVideoSurface().setLayoutParams(lp);

        getVideoSurface().invalidate();
    }


    protected void seek(int delta) {
        if (mLibVLC.getLength() <= 0 && !mSeeking) return;

        long position = mLibVLC.getTime() + delta;
        if (position < 0) position = 0;
        setCurrentTime(position);
        showOverlay();
        onProgressChanged(getCurrentTime(), getDuration());
        mLastSub = null;
        checkSubs();
    }

    protected void setLastSub(Caption sub) {
        mLastSub = sub;
    }


    private void startSubtitles() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    FileInputStream fileInputStream = new FileInputStream(mSubsFile);
                    FormatSRT formatSRT = new FormatSRT();
                    mSubs = formatSRT.parseFile(mSubsFile.toString(), FileUtils.inputstreamToCharsetString(fileInputStream, mCurrentSubsLang).split("\n"));
                    checkSubs();
                } catch (FileNotFoundException e) {
                    if (e.getMessage().contains("EBUSY")) {
                        startSubtitles();
                    }
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }


    protected void checkSubs() {
        if (mLibVLC != null && mLibVLC.isPlaying() && mSubs != null) {
            Collection<Caption> subtitles = mSubs.captions.values();
            double currentTime = getCurrentTime() - mSubtitleOffset;
            if (mLastSub != null && currentTime >= mLastSub.start.getMilliseconds() && currentTime <= mLastSub.end.getMilliseconds()) {
                showTimedCaptionText(mLastSub);
            } else {
                for (Caption caption : subtitles) {
                    if (currentTime >= caption.start.getMilliseconds() && currentTime <= caption.end.getMilliseconds()) {
                        mLastSub = caption;
                        showTimedCaptionText(caption);
                        break;
                    } else if (currentTime > caption.end.getMilliseconds()) {
                        showTimedCaptionText(null);
                    }
                }
            }
        }
    }


    public void onSubtitleLanguageSelected(String language) {
        if (mCurrentSubsLang != null && (language == null || mCurrentSubsLang.equals(language))) {
            return;
        }

        showTimedCaptionText(null);

        mCurrentSubsLang = language;
        mStreamInfo.setSubtitleLanguage(language);

        if (language.equals("no-subs")) {
            mSubs = null;
            return;
        }

        SubsProvider.download(getActivity(), mMedia, language, new com.squareup.okhttp.Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                mSubs = null;
                mCurrentSubsLang = "no-subs";

                try {
                    Snackbar.make(mRootView, "Subtitle download failed", Snackbar.LENGTH_SHORT).show();
                } catch (RuntimeException runtimeException) {
                    runtimeException.printStackTrace();
                }
            }

            @Override
            public void onResponse(Response response) throws IOException {
                mSubsFile = new File(SubsProvider.getStorageLocation(getActivity()), mMedia.videoId + "-" + mCurrentSubsLang + ".srt");
                startSubtitles();
            }
        });
    }


    private static class VideoPlayerEventHandler extends WeakHandler<BaseVideoPlayerFragment> {
        public VideoPlayerEventHandler(BaseVideoPlayerFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            BaseVideoPlayerFragment fragment = getOwner();
            if (fragment == null) return;

            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaPlayerPlaying:
                    fragment.resumeVideo();
                    fragment.setProgressVisible(false);
                    fragment.showOverlay();
                    break;
                case EventHandler.MediaPlayerEndReached:
                    fragment.endReached();
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    fragment.onErrorEncountered();
                    break;
                case EventHandler.HardwareAccelerationError:
                    fragment.handleHardwareAccelerationError();
                    break;
                case EventHandler.MediaPlayerTimeChanged:
                case EventHandler.MediaPlayerPositionChanged:
                    fragment.onProgressChanged(fragment.getCurrentTime(), fragment.getDuration());
                    fragment.checkSubs();
                    fragment.setProgressVisible(false);
                    break;
            }
            fragment.updatePlayPauseState();
        }
    }

    @Override
    public void onStreamStarted() {
    }

    @Override
    public void onStreamError(Exception e) {
    }

    @Override
    public void onStreamReady(File videoLocation) {
    }

    @Override
    public void onStreamProgress(DownloadStatus status) {
        int newProgress = (int) ((getDuration() / 100) * status.progress);
        if (mStreamerProgress < newProgress) {
            mStreamerProgress = newProgress;
        }
    }

    @Override
    public void onStreamMetaData(Torrent torrent) {

    }

    /**
     * attach and disattach surface to the lib
     */
    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (format == PixelFormat.RGBX_8888)
                Timber.d("Pixel format is RGBX_8888");
            else if (format == PixelFormat.RGB_565)
                Timber.d("Pixel format is RGB_565");
            else if (format == ImageFormat.YV12)
                Timber.d("Pixel format is YV12");
            else
                Timber.d("Pixel format is other/unknown");
            if (mLibVLC != null)
                mLibVLC.attachSurface(holder.getSurface(), BaseVideoPlayerFragment.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (mLibVLC != null)
                mLibVLC.detachSurface();
        }
    };

    public interface Callback {
        Long getResumePosition();
        StreamInfo getInfo();
        TorrentService getService();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.activity_player, menu);
        menu.findItem(R.id.action_reload).setVisible(mShowReload);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reload:
                loadMedia();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void subsClick() {
        if (mMedia != null && mMedia.subtitles != null) {
            if (getChildFragmentManager().findFragmentByTag("overlay_fragment") != null) return;

            String[] subsOptions = {
                    getString(R.string.subtitle_language),
                    getString(R.string.subtitle_size),
                    getString(R.string.subtitle_timing)
            };

            StringArraySelectorDialogFragment.show(getChildFragmentManager(), R.string.subtitle_settings, subsOptions, -1,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int position) {
                            switch (position) {
                                case 0:
                                    subsLanguageSettings();
                                    break;
                                case 1:
                                    subsSizeSettings();
                                    break;
                                case 2:
                                    subsTimingSettings();
                                    break;
                            }
                        }
                    });
        }
    }

    private void subsLanguageSettings() {
        String[] subtitles = mMedia.subtitles.keySet().toArray(new String[mMedia.subtitles.size()]);
        Arrays.sort(subtitles);
        final String[] adapterSubtitles = new String[subtitles.length + 2];
        System.arraycopy(subtitles, 0, adapterSubtitles, 1, subtitles.length);

        adapterSubtitles[0] = "no-subs";
        adapterSubtitles[adapterSubtitles.length - 1] = "custom";
        String[] readableNames = new String[adapterSubtitles.length];

        for (int i = 0; i < readableNames.length - 1; i++) {
            String language = adapterSubtitles[i];
            if (language.equals("no-subs")) {
                readableNames[i] = getString(R.string.no_subs);
            } else {
                Locale locale = LocaleUtils.toLocale(language);
                readableNames[i] = locale.getDisplayName(locale);
            }
        }

        readableNames[readableNames.length - 1] = "Custom..";

        StringArraySelectorDialogFragment.showSingleChoice(getChildFragmentManager(), R.string.subtitles, readableNames,
                Arrays.asList(adapterSubtitles).indexOf(mCurrentSubsLang), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, int position) {
                        if (position == adapterSubtitles.length - 1) {
                            FileSelectorDialogFragment.show(getChildFragmentManager(), new FileSelectorDialogFragment.Listener() {
                                @Override
                                public void onFileSelected(File f) {
                                    if (!f.getPath().endsWith(".srt")) {
                                        Snackbar.make(mRootView, R.string.unknown_error, Snackbar.LENGTH_SHORT).show();
                                        return;
                                    }
                                    FileSelectorDialogFragment.hide();
                                    mSubsFile = f;
                                    startSubtitles();
                                    dialog.dismiss();
                                }
                            });
                            return;
                        }
                        onSubtitleLanguageSelected(adapterSubtitles[position]);
                        dialog.dismiss();
                    }
                });
    }

    private void subsSizeSettings() {
        Bundle args = new Bundle();
        args.putString(NumberPickerDialogFragment.TITLE, getString(R.string.subtitle_size));
        args.putInt(NumberPickerDialogFragment.MAX_VALUE, 60);
        args.putInt(NumberPickerDialogFragment.MIN_VALUE, 10);
        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) PrefUtils.get(getActivity(), Prefs.SUBTITLE_SIZE, 16));

        NumberPickerDialogFragment dialogFragment = new NumberPickerDialogFragment();
        dialogFragment.setArguments(args);
        dialogFragment.setOnResultListener(new NumberPickerDialogFragment.ResultListener() {
            @Override
            public void onNewValue(int value) {
                updateSubtitleSize(value);
            }
        });
        dialogFragment.show(getChildFragmentManager(), "overlay_fragment");
    }

    private void subsTimingSettings() {
        Bundle args = new Bundle();
        args.putString(NumberPickerDialogFragment.TITLE, getString(R.string.subtitle_timing));
        args.putInt(NumberPickerDialogFragment.MAX_VALUE, 3600);
        args.putInt(NumberPickerDialogFragment.MIN_VALUE, -3600);
        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, mSubtitleOffset / 60);
        args.putBoolean(NumberPickerDialogFragment.FOCUSABLE, true);

        NumberPickerDialogFragment dialogFragment = new NumberPickerDialogFragment();
        dialogFragment.setArguments(args);
        dialogFragment.setOnResultListener(new NumberPickerDialogFragment.ResultListener() {
            @Override
            public void onNewValue(int value) {
                mSubtitleOffset = value * 60;
                showTimedCaptionText(null);
            }
        });
        dialogFragment.show(getChildFragmentManager(), "overlay_fragment");
    }

    protected abstract void updateSubtitleSize(int size);

    BeamDeviceListener mDeviceListener = new BeamDeviceListener() {

        @Override
        public void onDeviceReady(ConnectableDevice device) {
            super.onDeviceReady(device);
            BeamPlayerActivity.startActivity(getActivity(), mStreamInfo, getCurrentTime());
            getActivity().finish();
        }

    };

}
