package pct.droid.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.Layout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.os.AsyncTask;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import com.squareup.picasso.Picasso;

import java.util.Arrays;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.Bind;
import butterknife.OnClick;
import android.support.annotation.Nullable;
import pct.droid.R;
import pct.droid.activities.TrailerPlayerActivity;
import pct.droid.activities.VideoPlayerActivity;
import pct.droid.base.preferences.Prefs;
import pct.droid.base.providers.media.models.Movie;
import pct.droid.base.providers.subs.SubsProvider;
import pct.droid.base.torrent.Magnet;
import pct.droid.base.torrent.StreamInfo;
import pct.droid.base.torrent.TorrentHealth;
import pct.droid.base.utils.LocaleUtils;
import pct.droid.base.utils.PixelUtils;
import pct.droid.base.utils.PrefUtils;
import pct.droid.base.utils.SortUtils;
import pct.droid.base.utils.StringUtils;
import pct.droid.base.utils.ThreadUtils;
import pct.droid.base.utils.VersionUtils;
import pct.droid.base.youtube.YouTubeData;
import pct.droid.dialogfragments.SynopsisDialogFragment;
import pct.droid.fragments.base.BaseDetailFragment;
import pct.droid.widget.OptionSelector;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.FormEncodingBuilder;

public class MovieDetailFragment extends BaseDetailFragment {

    private static Movie sMovie;
    private String mSelectedSubtitleLanguage, mSelectedQuality;
    private Boolean mAttached = false;
    private Magnet mMagnet;

    @Bind(R.id.play_button)
    ImageButton mPlayButton;
    @Bind(R.id.title)
    TextView mTitle;
    @Bind(R.id.health)
    ImageView mHealth;
    @Bind(R.id.meta)
    TextView mMeta;
    @Bind(R.id.synopsis)
    TextView mSynopsis;
    @Bind(R.id.read_more)
    Button mReadMore;
    @Bind(R.id.watch_trailer)
    Button mWatchTrailer;
    @Bind(R.id.magnet)
    ImageButton mOpenMagnet;
    @Bind(R.id.rating)
    RatingBar mRating;
    @Bind(R.id.subtitles)
    OptionSelector mSubtitles;
    @Bind(R.id.quality)
    OptionSelector mQuality;
    @Nullable
    @Bind(R.id.cover_image)
    ImageView mCoverImage;

    public static MovieDetailFragment newInstance(Movie movie) {
        sMovie = movie;
        return new MovieDetailFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRoot = inflater.inflate(R.layout.fragment_moviedetail, container, false);
        if (VersionUtils.isJellyBean() && container != null) {
            mRoot.setMinimumHeight(container.getMinimumHeight());
        }
        ButterKnife.bind(this, mRoot);

        if(sMovie != null) {

            if (!VersionUtils.isJellyBean()) {
                mPlayButton.setBackgroundDrawable(PixelUtils.changeDrawableColor(mPlayButton.getContext(), R.drawable.play_button_circle, sMovie.color));
            } else {
                mPlayButton.setBackground(PixelUtils.changeDrawableColor(mPlayButton.getContext(), R.drawable.play_button_circle, sMovie.color));
            }

            mTitle.setText(sMovie.title);
            if (!sMovie.rating.equals("-1")) {
                Double rating = Double.parseDouble(sMovie.rating);
                mRating.setProgress(rating.intValue());
                mRating.setContentDescription("Rating: " + rating.intValue() + " out of 10");
                mRating.setVisibility(View.VISIBLE);
            } else {
                mRating.setVisibility(View.GONE);
            }

            String metaDataStr = sMovie.year;
            if (!TextUtils.isEmpty(sMovie.runtime)) {
                metaDataStr += " • ";
                metaDataStr += sMovie.runtime + " " + getString(R.string.minutes);
            }

            if (!TextUtils.isEmpty(sMovie.genre)) {
                metaDataStr += " • ";
                metaDataStr += sMovie.genre;
            }

            mMeta.setText(metaDataStr);

            if (!TextUtils.isEmpty(sMovie.synopsis)) {
                mSynopsis.setText(sMovie.synopsis);
                mSynopsis.post(new Runnable() {
                    @Override
                    public void run() {
                        boolean ellipsized = false;
                        Layout layout = mSynopsis.getLayout();
                        if (layout == null) return;
                        int lines = layout.getLineCount();
                        if (lines > 0) {
                            int ellipsisCount = layout.getEllipsisCount(lines - 1);
                            if (ellipsisCount > 0) {
                                ellipsized = true;
                            }
                        }
                        mReadMore.setVisibility(ellipsized ? View.VISIBLE : View.GONE);
                    }
                });
            } else {
                mSynopsis.setClickable(false);
                mReadMore.setVisibility(View.GONE);
            }

            mWatchTrailer.setVisibility(sMovie.trailer == null || sMovie.trailer.isEmpty() ? View.GONE : View.VISIBLE);

            mSubtitles.setFragmentManager(getFragmentManager());
            mQuality.setFragmentManager(getFragmentManager());
            mSubtitles.setTitle(R.string.subtitles);
            mQuality.setTitle(R.string.quality);

            mSubtitles.setText(R.string.loading_subs);
            mSubtitles.setClickable(false);

            if (sMovie.getSubsProvider() != null) {
                sMovie.getSubsProvider().getList(sMovie, new SubsProvider.Callback() {
                    @Override
                    public void onSuccess(Map<String, String> subtitles) {
                        if (!mAttached) return;

                        if (subtitles == null) {
                            ThreadUtils.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mSubtitles.setText(R.string.no_subs_available);
                                }
                            });
                            return;
                        }

                        sMovie.subtitles = subtitles;

                        String[] languages = subtitles.keySet().toArray(new String[subtitles.size()]);
                        Arrays.sort(languages);
                        final String[] adapterLanguages = new String[languages.length + 1];
                        adapterLanguages[0] = "no-subs";
                        System.arraycopy(languages, 0, adapterLanguages, 1, languages.length);

                        String[] readableNames = new String[adapterLanguages.length];
                        for (int i = 0; i < readableNames.length; i++) {
                            String language = adapterLanguages[i];
                            if (language.equals("no-subs")) {
                                readableNames[i] = getString(R.string.no_subs);
                            } else {
                                Locale locale = LocaleUtils.toLocale(language);
                                readableNames[i] = locale.getDisplayName(locale);
                            }
                        }

                        mSubtitles.setListener(new OptionSelector.SelectorListener() {
                            @Override
                            public void onSelectionChanged(int position, String value) {
                                onSubtitleLanguageSelected(adapterLanguages[position]);
                            }
                        });
                        mSubtitles.setData(readableNames);
                        ThreadUtils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mSubtitles.setClickable(true);
                            }
                        });

                        String defaultSubtitle = PrefUtils.get(mSubtitles.getContext(), Prefs.SUBTITLE_DEFAULT, null);
                        if (subtitles.containsKey(defaultSubtitle)) {
                            onSubtitleLanguageSelected(defaultSubtitle);
                            mSubtitles.setDefault(Arrays.asList(adapterLanguages).indexOf(defaultSubtitle));
                        } else {
                            onSubtitleLanguageSelected("no-subs");
                            mSubtitles.setDefault(Arrays.asList(adapterLanguages).indexOf("no-subs"));
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        mSubtitles.setData(new String[0]);
                        mSubtitles.setClickable(true);
                    }
                });
            } else {
                mSubtitles.setClickable(false);
                mSubtitles.setText(R.string.no_subs_available);
            }

            if (sMovie.torrents.size() > 0) {
                final String[] qualities = sMovie.torrents.keySet().toArray(new String[sMovie.torrents.size()]);
                SortUtils.sortQualities(qualities);
                mQuality.setData(qualities);
                mQuality.setListener(new OptionSelector.SelectorListener() {
                    @Override
                    public void onSelectionChanged(int position, String value) {
                        mSelectedQuality = value;
                        renderHealth();
                        updateMagnet();
                    }
                });
                String defaultQuality = PrefUtils.get(mQuality.getContext(), Prefs.QUALITY_DEFAULT, "720p");
                int qualityIndex = Arrays.asList(qualities).contains(defaultQuality) ? Arrays.asList(qualities).indexOf(defaultQuality) : qualities.length - 1;
                mSelectedQuality = qualities[qualityIndex];
                mQuality.setText(mSelectedQuality);
                mQuality.setDefault(qualityIndex);
                renderHealth();
                updateMagnet();
            }

            if (mCoverImage != null) {
                Picasso.with(mCoverImage.getContext()).load(sMovie.image).into(mCoverImage);
            }
        }

        return mRoot;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAttached = true;
        if (activity instanceof FragmentListener)
            mCallback = (FragmentListener) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mAttached = false;
    }

    private void renderHealth() {
        if(mHealth.getVisibility() == View.GONE) {
            mHealth.setVisibility(View.VISIBLE);
        }

        TorrentHealth health = TorrentHealth.calculate(sMovie.torrents.get(mSelectedQuality).seeds, sMovie.torrents.get(mSelectedQuality).peers);
        mHealth.setImageResource(health.getImageResource());
    }

    private void updateMagnet() {
        if(mMagnet == null) {
            mMagnet = new Magnet(mActivity, sMovie.torrents.get(mSelectedQuality).url);
        }
        mMagnet.setUrl(sMovie.torrents.get(mSelectedQuality).url);

        if(!mMagnet.canOpen()) {
            mOpenMagnet.setVisibility(View.GONE);
        } else {
            mOpenMagnet.setVisibility(View.VISIBLE);
        }
    }

    @OnClick(R.id.read_more)
    public void openReadMore(View v) {
        if (getFragmentManager().findFragmentByTag("overlay_fragment") != null)
            return;
        SynopsisDialogFragment synopsisDialogFragment = new SynopsisDialogFragment();
        Bundle b = new Bundle();
        b.putString("text", sMovie.synopsis);
        synopsisDialogFragment.setArguments(b);
        synopsisDialogFragment.show(getFragmentManager(), "overlay_fragment");
    }

    @OnClick(R.id.watch_trailer)
    public void openTrailer(View v) {
        Intent trailerIntent = new Intent(mActivity, TrailerPlayerActivity.class);
        if (!YouTubeData.isYouTubeUrl(sMovie.trailer)) {
            trailerIntent = new Intent(mActivity, VideoPlayerActivity.class);
        }
        trailerIntent.putExtra(TrailerPlayerActivity.DATA, sMovie);
        trailerIntent.putExtra(TrailerPlayerActivity.LOCATION, sMovie.trailer);
        startActivity(trailerIntent);
    }

    @OnClick(R.id.play_button)
    public void play() {
    final String torrentHash = sMovie.torrents.get(mSelectedQuality).hash;
    final String apikey = PrefUtils.get(getActivity(), Prefs.API_KEY, "");
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String result = null;

                RequestBody formBody = new FormEncodingBuilder()
                        .add("api_key", apikey)
                        .add("info_hash",torrentHash)
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.justseed.it/torrent/add.csp")
                        .post(formBody)
                        .build();

                OkHttpClient client = new OkHttpClient();

                try {
                    Response response = client.newCall(request).execute();
                    result = response.body().string();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return result;
            }

            @Override
            protected void onPostExecute(String result) {

                Toast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();

            }
        }.execute();
    }
    @OnClick(R.id.magnet)
    public void openMagnet() {
        mMagnet.open(mActivity);
    }

    private void onSubtitleLanguageSelected(String language) {
        mSelectedSubtitleLanguage = language;
        if (!language.equals("no-subs")) {
            final Locale locale = LocaleUtils.toLocale(language);
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSubtitles.setText(StringUtils.uppercaseFirst(locale.getDisplayName(locale)));
                }
            });
        } else {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSubtitles.setText(R.string.no_subs);
                }
            });
        }
    }
}
