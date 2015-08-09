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

package pct.droid.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;

import org.videolan.libvlc.LibVLC;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.Bind;
import pct.droid.BuildConfig;
import pct.droid.R;
import pct.droid.activities.base.PopcornBaseActivity;
import pct.droid.adapters.PreferencesListAdapter;
import pct.droid.base.Constants;
import pct.droid.base.preferences.DefaultPlayer;
import pct.droid.base.preferences.PrefItem;
import pct.droid.base.preferences.Prefs;
import pct.droid.base.updater.PopcornUpdater;
import pct.droid.base.utils.LocaleUtils;
import pct.droid.base.utils.PrefUtils;
import pct.droid.base.utils.ResourceUtils;
import pct.droid.base.utils.StorageUtils;
import pct.droid.dialogfragments.ChangeLogDialogFragment;
import pct.droid.dialogfragments.ApinputFragment;
import pct.droid.dialogfragments.ColorPickerDialogFragment;
import pct.droid.dialogfragments.NumberPickerDialogFragment;
import pct.droid.dialogfragments.SeekBarDialogFragment;
import pct.droid.dialogfragments.StringArraySelectorDialogFragment;
import pct.droid.utils.ToolbarUtils;

public class PreferencesActivity extends PopcornBaseActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener, DirectoryChooserFragment.OnFragmentInteractionListener {

    private List<Object> mPrefItems = new ArrayList<>();
    private LinearLayoutManager mLayoutManager;
    private DirectoryChooserFragment mDirectoryChooserFragment;

    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.recyclerView)
    RecyclerView recyclerView;
    @Bind(R.id.rootLayout)
    ViewGroup rootLayout;

    public static Intent startActivity(Activity activity) {
        Intent intent = new Intent(activity, PreferencesActivity.class);
        activity.startActivity(intent);
        return intent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.activity_preferences);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.preferences);

        ToolbarUtils.updateToolbarHeight(this, toolbar);

        mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);

        PrefUtils.getPrefs(this).registerOnSharedPreferenceChangeListener(this);

        refreshItems();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        PrefUtils.getPrefs(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    private void refreshItems() {
        mPrefItems = new ArrayList<>();
        mPrefItems.add(getResources().getString(R.string.general));

        final String[] items = {getString(R.string.title_movies), getString(R.string.title_shows), getString(R.string.title_anime)};
        final String[] qualities = getResources().getStringArray(R.array.video_qualities);
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_default_view, R.string.default_view, Prefs.DEFAULT_VIEW, 0,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.SINGLE_CHOICE,
                                (int) item.getValue(), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        item.saveValue(position);
                                        dialog.dismiss();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return items[(Integer) item.getValue()];
                    }
                }));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_default_player, R.string.default_player, Prefs.DEFAULT_PLAYER, "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        int currentPosition = 0;
                        String currentValue = item.getValue().toString();

                        final Map<String, String> players = DefaultPlayer.getVideoPlayerApps();
                        final String[] playerDatas = players.keySet().toArray(new String[players.size()]);
                        String[] items = new String[players.size() + 1];
                        items[0] = getString(R.string.internal_player);
                        for (int i = 0; i < playerDatas.length; i++) {
                            String playerData = playerDatas[i];
                            String playerName = players.get(playerData);

                            items[i + 1] = playerName;
                            if (playerData.equals(currentValue)) {
                                currentPosition = i + 1;
                            }
                        }

                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.SINGLE_CHOICE, currentPosition,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        if (position == 0) {
                                            DefaultPlayer.set("", "");
                                        } else {
                                            String playerData = playerDatas[position - 1];
                                            DefaultPlayer.set(players.get(playerData), playerData);
                                        }
                                        dialog.dismiss();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return PrefUtils.get(PreferencesActivity.this, Prefs.DEFAULT_PLAYER_NAME, getString(R.string.internal_player));
                    }
                }));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_action_quality, R.string.quality, Prefs.QUALITY_DEFAULT, "720p",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        openListSelectionDialog(item.getTitle(), qualities, StringArraySelectorDialogFragment.SINGLE_CHOICE,
                                Arrays.asList(qualities).indexOf(item.getValue()), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        item.saveValue(qualities[position]);
                                        dialog.dismiss();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return (String) item.getValue();
                    }
                }));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_app_language, R.string.i18n_language, Prefs.LOCALE, "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        int currentPosition = 0;
                        String currentValue = item.getValue().toString();

                        final String[] languages = getResources().getStringArray(R.array.translation_languages);
                        Arrays.sort(languages);

                        String[] items = new String[languages.length + 1];
                        items[0] = getString(R.string.device_language);
                        for (int i = 0; i < languages.length; i++) {
                            Locale locale = LocaleUtils.toLocale(languages[i]);
                            items[i + 1] = locale.getDisplayName(locale);
                            if (languages[i].equals(currentValue)) {
                                currentPosition = i + 1;
                            }
                        }

                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.SINGLE_CHOICE, currentPosition,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        if (position == 0) {
                                            item.clearValue();
                                        } else {
                                            item.saveValue(languages[position - 1]);
                                        }

                                        dialog.dismiss();

                                        Snackbar.make(rootLayout, R.string.restart_effect, Snackbar.LENGTH_LONG).show();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        String langCode = item.getValue().toString();
                        if (langCode.isEmpty())
                            return getString(R.string.device_language);

                        Locale locale = LocaleUtils.toLocale(langCode);
                        return locale.getDisplayName(locale);

                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_wifi_only, R.string.stream_over_wifi_only, Prefs.WIFI_ONLY, true,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        item.saveValue(!(boolean) item.getValue());
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        boolean enabled = (boolean) item.getValue();
                        return enabled ? getString(R.string.enabled) : getString(R.string.disabled);
                    }
                }));

        mPrefItems.add(getResources().getString(R.string.jsit));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_key, R.string.jsitapi, Prefs.API_KEY, "Enter your API KEY" ,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(ApinputFragment.TITLE, item.getTitle());
                        // args.putInt(ApinputFragment.MAX_VALUE, 60);
                        // args.putInt(ApinputFragment.MIN_VALUE, 10);
                        // args.putInt(ApinputFragment.DEFAULT_VALUE, (int) item.getValue());

                        ApinputFragment dialogFragment = new ApinputFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new ApinputFragment.ResultListener() {
                            @Override
                            public void onNewValue(String value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getSupportFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return item.getValue().toString();
                    }
                }));

        mPrefItems.add(getResources().getString(R.string.subtitles));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_subtitle_color, R.string.subtitle_color, Prefs.SUBTITLE_COLOR, Color.WHITE,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(NumberPickerDialogFragment.TITLE, item.getTitle());
                        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) item.getValue());

                        ColorPickerDialogFragment dialogFragment = new ColorPickerDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new ColorPickerDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return String.format("#%06X", 0xFFFFFF & (int) item.getValue());
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_subtitle_size, R.string.subtitle_size, Prefs.SUBTITLE_SIZE, 16,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(NumberPickerDialogFragment.TITLE, item.getTitle());
                        args.putInt(NumberPickerDialogFragment.MAX_VALUE, 60);
                        args.putInt(NumberPickerDialogFragment.MIN_VALUE, 10);
                        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) item.getValue());

                        NumberPickerDialogFragment dialogFragment = new NumberPickerDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new NumberPickerDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getSupportFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return Integer.toString((int) item.getValue());
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_subtitle_stroke_color, R.string.subtitle_stroke_color, Prefs.SUBTITLE_STROKE_COLOR, Color.BLACK,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(NumberPickerDialogFragment.TITLE, item.getTitle());
                        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) item.getValue());

                        ColorPickerDialogFragment dialogFragment = new ColorPickerDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new ColorPickerDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return String.format("#%06X", 0xFFFFFF & (int) item.getValue());
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_subtitle_stroke_width, R.string.subtitle_stroke_width, Prefs.SUBTITLE_STROKE_WIDTH, 2,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(NumberPickerDialogFragment.TITLE, item.getTitle());
                        args.putInt(NumberPickerDialogFragment.MAX_VALUE, 5);
                        args.putInt(NumberPickerDialogFragment.MIN_VALUE, 0);
                        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) item.getValue());

                        NumberPickerDialogFragment dialogFragment = new NumberPickerDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new NumberPickerDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getSupportFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return Integer.toString((int) item.getValue());
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_subtitle_lang, R.string.subtitle_language, Prefs.SUBTITLE_DEFAULT, "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        int currentPosition = 0;
                        String currentValue = item.getValue().toString();

                        final String[] languages = getResources().getStringArray(R.array.subtitle_languages);
                        String[] items = new String[languages.length + 1];
                        items[0] = getString(R.string.no_default_set);
                        for (int i = 0; i < languages.length; i++) {
                            Locale locale = LocaleUtils.toLocale(languages[i]);
                            items[i + 1] = locale.getDisplayName(locale);
                            if (languages[i].equals(currentValue)) {
                                currentPosition = i + 1;
                            }
                        }

                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.SINGLE_CHOICE, currentPosition,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        if (position == 0) {
                                            item.clearValue();
                                        } else {
                                            item.saveValue(languages[position - 1]);
                                        }
                                        dialog.dismiss();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        String langCode = item.getValue().toString();
                        if (langCode.isEmpty())
                            return getString(R.string.no_default_set);

                        Locale locale = LocaleUtils.toLocale(langCode);
                        return locale.getDisplayName(locale);
                    }
                }));

        mPrefItems.add(getResources().getString(R.string.torrents));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_download_limit, R.string.download_speed, Prefs.LIBTORRENT_DOWNLOAD_LIMIT, 0,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(SeekBarDialogFragment.TITLE, item.getTitle());
                        args.putInt(SeekBarDialogFragment.MAX_VALUE, 2000);
                        args.putInt(SeekBarDialogFragment.MIN_VALUE, 0);
                        args.putInt(SeekBarDialogFragment.DEFAULT_VALUE, (int) item.getValue() / 1000);

                        SeekBarDialogFragment dialogFragment = new SeekBarDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new SeekBarDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        int limit = (Integer) item.getValue();
                        if (limit == 0) {
                            return getString(R.string.unlimited);
                        } else {
                            return (limit/1000) + " kB/s";
                        }
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_upload_limit, R.string.upload_speed, Prefs.LIBTORRENT_UPLOAD_LIMIT, 0,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(SeekBarDialogFragment.TITLE, item.getTitle());
                        args.putInt(SeekBarDialogFragment.MAX_VALUE, 2000);
                        args.putInt(SeekBarDialogFragment.MIN_VALUE, 0);
                        args.putInt(SeekBarDialogFragment.DEFAULT_VALUE, (int) item.getValue() / 1000);

                        SeekBarDialogFragment dialogFragment = new SeekBarDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new SeekBarDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        int limit = (Integer) item.getValue();
                        if (limit == 0) {
                            return getString(R.string.unlimited);
                        } else {
                            return (limit/1000) + " kB/s";
                        }
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_connections, R.string.max_connections, Prefs.LIBTORRENT_CONNECTION_LIMIT, 200,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(NumberPickerDialogFragment.TITLE, item.getTitle());
                        args.putInt(NumberPickerDialogFragment.MAX_VALUE, 200);
                        args.putInt(NumberPickerDialogFragment.MIN_VALUE, 10);
                        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) item.getValue());

                        NumberPickerDialogFragment dialogFragment = new NumberPickerDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new NumberPickerDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getSupportFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        int limit = (Integer) item.getValue();
                        return limit + " connections";
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_dht, R.string.dht_limit, Prefs.LIBTORRENT_DHT_LIMIT, 200,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        Bundle args = new Bundle();
                        args.putString(NumberPickerDialogFragment.TITLE, item.getTitle());
                        args.putInt(NumberPickerDialogFragment.MAX_VALUE, 200);
                        args.putInt(NumberPickerDialogFragment.MIN_VALUE, 10);
                        args.putInt(NumberPickerDialogFragment.DEFAULT_VALUE, (int) item.getValue());

                        NumberPickerDialogFragment dialogFragment = new NumberPickerDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnResultListener(new NumberPickerDialogFragment.ResultListener() {
                            @Override
                            public void onNewValue(int value) {
                                item.saveValue(value);
                            }
                        });
                        dialogFragment.show(getSupportFragmentManager(), "pref_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        int limit = (Integer) item.getValue();
                        return limit + " nodes";
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_storage_location, R.string.storage_location, Prefs.STORAGE_LOCATION,
                StorageUtils.getIdealCacheDirectory(this),
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        String[] items = {getString(R.string.storage_automatic), getString(R.string.storage_choose)};

                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.NORMAL, -1,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        if (position == 0) {
                                            item.clearValue();
                                        } else {
                                            mDirectoryChooserFragment = DirectoryChooserFragment.newInstance("pct.droid", null);
                                            mDirectoryChooserFragment.show(getFragmentManager(), "pref_fragment");
                                            dialog.dismiss();
                                        }
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return item.getValue().toString();
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_remove_cache, R.string.remove_cache, Prefs.REMOVE_CACHE, true,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        PrefUtils.save(PreferencesActivity.this, Prefs.REMOVE_CACHE, !(boolean) item.getValue());
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        boolean enabled = (boolean) item.getValue();
                        return enabled ? getString(R.string.enabled) : getString(R.string.disabled);
                    }
                }));

        mPrefItems.add(getResources().getString(R.string.advanced));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_hw_accel, R.string.hw_acceleration, Prefs.HW_ACCELERATION,
                LibVLC.HW_ACCELERATION_AUTOMATIC,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        String[] items = {getString(R.string.hw_automatic), getString(R.string.disabled), getString(R.string.hw_decoding),
                                getString(R.string.hw_full)};

                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.SINGLE_CHOICE,
                                (int) item.getValue() + 1, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        item.saveValue(position - 1);
                                        dialog.dismiss();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        switch ((int) item.getValue()) {
                            case LibVLC.HW_ACCELERATION_DECODING:
                                return getString(R.string.hw_decoding);
                            case LibVLC.HW_ACCELERATION_DISABLED:
                                return getString(R.string.disabled);
                            case LibVLC.HW_ACCELERATION_FULL:
                                return getString(R.string.hw_full);
                            default:
                            case LibVLC.HW_ACCELERATION_AUTOMATIC:
                                return getString(R.string.hw_automatic);
                        }
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_pixel_format, R.string.pixel_format, Prefs.PIXEL_FORMAT, "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(final PrefItem item) {
                        String[] items = {getString(R.string.rgb16), getString(R.string.rgb32), getString(R.string.yuv)};

                        String currentValue = (String) item.getValue();
                        int current = 1;
                        if (currentValue.equals("YV12")) {
                            current = 2;
                        } else if (currentValue.equals("RV16")) {
                            current = 0;
                        }

                        openListSelectionDialog(item.getTitle(), items, StringArraySelectorDialogFragment.SINGLE_CHOICE,
                                current, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int position) {
                                        if(position == 2) {
                                            item.saveValue("YV12");
                                        } else if (position == 0) {
                                            item.saveValue("RV16");
                                        } else {
                                            item.saveValue("");
                                        }
                                        dialog.dismiss();
                                    }
                                });
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        if (item.getValue().equals("YV12")) {
                            return getString(R.string.yuv);
                        } else if (item.getValue().equals("RV16")) {
                            return getString(R.string.rgb16);
                        }
                        return getString(R.string.rgb32);
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_nav_vpn, R.string.show_vpn, Prefs.SHOW_VPN, true,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        item.saveValue(!(boolean) item.getValue());
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        boolean enabled = (boolean) item.getValue();
                        return enabled ? getString(R.string.enabled) : getString(R.string.disabled);
                    }
                }));

        mPrefItems.add(getResources().getString(R.string.updates));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_auto_update, R.string.auto_updates, Prefs.AUTOMATIC_UPDATES, true,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        item.saveValue(!(boolean) item.getValue());
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        boolean enabled = (boolean) item.getValue();
                        return enabled ? getString(R.string.enabled) : getString(R.string.disabled);
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_check_update, R.string.check_for_updates, PopcornUpdater.LAST_UPDATE_CHECK, 1,
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        PopcornUpdater.getInstance(PreferencesActivity.this).checkUpdatesManually();
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        long timeStamp = Long.parseLong(PrefUtils.get(PreferencesActivity.this, PopcornUpdater.LAST_UPDATE_CHECK, "0"));
                        Calendar cal = Calendar.getInstance(Locale.getDefault());
                        cal.setTimeInMillis(timeStamp);
                        String time = SimpleDateFormat.getTimeInstance(SimpleDateFormat.MEDIUM, Locale.getDefault()).format(timeStamp);
                        String date = DateFormat.format("dd-MM-yyy", cal).toString();
                        return getString(R.string.last_check) + ": " + date + " " + time;
                    }
                }));

        mPrefItems.add(getResources().getString(R.string.about));

        if (!Constants.DEBUG_ENABLED) {
            mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_report_bug, R.string.report_a_bug, "", "",
                    new PrefItem.OnClickListener() {
                        @Override
                        public void onClick(PrefItem item) {
                            Intent i = new Intent(Intent.ACTION_VIEW);
                            int version = BuildConfig.VERSION_CODE;
                            String versionName = BuildConfig.VERSION_NAME;
                            i.setData(Uri.parse("https://git.popcorntime.io/popcorntime/android/issues/new?issue[label_ids][]=17&issue[label_ids][]=12&issue[title]=Bug:%20&issue[description]=I found a bug in build " + versionName + " (" + version + ") of the Android application. These are the steps to reproduce: "));
                            startActivity(i);
                        }
                    },
                    new PrefItem.SubTitleGenerator() {
                        @Override
                        public String get(PrefItem item) {
                            return getString(R.string.tap_to_open);
                        }
                    }));
        }

        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_changelog, R.string.changelog, "", "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        ChangeLogDialogFragment changeLogDialogFragment = new ChangeLogDialogFragment();
                        changeLogDialogFragment.show(getSupportFragmentManager(), "prefs_fragment");
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return getString(R.string.tap_to_open);
                    }
                }));

        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_open_source, R.string.open_source_licenses, "", "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(Constants.GIT_URL + "popcorntime/android/blob/development/NOTICE.md"));
                        startActivity(i);
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return getString(R.string.tap_to_open);
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_version, R.string.version, "", "",
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        try {
                            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                            return packageInfo.versionName + " - " + Build.CPU_ABI;
                        } catch (PackageManager.NameNotFoundException e) {
                            e.printStackTrace();
                        }
                        return "?.? (?) - ?";
                    }
                }));
        mPrefItems.add(new PrefItem(this, R.drawable.ic_prefs_about, R.string.about, "", "",
                new PrefItem.OnClickListener() {
                    @Override
                    public void onClick(PrefItem item) {
                        AboutActivity.startActivity(PreferencesActivity.this);
                    }
                },
                new PrefItem.SubTitleGenerator() {
                    @Override
                    public String get(PrefItem item) {
                        return getString(R.string.tap_to_open);
                    }
                }));

        if (recyclerView.getAdapter() != null && mLayoutManager != null) {
            int position = mLayoutManager.findFirstVisibleItemPosition();
            View v = mLayoutManager.findViewByPosition(position);
            recyclerView.setAdapter(new PreferencesListAdapter(mPrefItems));
            if (v != null) {
                int offset = v.getTop();
                mLayoutManager.scrollToPositionWithOffset(position, offset);
            } else {
                mLayoutManager.scrollToPosition(position);
            }
        } else {
            recyclerView.setAdapter(new PreferencesListAdapter(mPrefItems));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshItems();
        toolbar.setMinimumHeight((int) ResourceUtils.getAttributeDimension(this, this.getTheme(), R.attr.actionBarSize));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (isUseChangeablePref(key)) {
            refreshItems();
        }
    }

    private boolean isUseChangeablePref(String key) {
        boolean b = false;
        for (Object item : mPrefItems) {
            if (item instanceof PrefItem) {
                PrefItem pref = (PrefItem) item;
                if (pref.getPrefKey().equals(key))
                    b = true;
            }
        }
        return b;
    }

    private void openListSelectionDialog(String title, String[] items, int mode, int defaultPosition,
                                         DialogInterface.OnClickListener onClickListener) {
        if (mode == StringArraySelectorDialogFragment.NORMAL) {
            StringArraySelectorDialogFragment.show(getSupportFragmentManager(), title, items, defaultPosition, onClickListener);
        } else if (mode == StringArraySelectorDialogFragment.SINGLE_CHOICE) {
            StringArraySelectorDialogFragment.showSingleChoice(getSupportFragmentManager(), title, items, defaultPosition, onClickListener);
        }
    }

    @Override
    public void onSelectDirectory(@NonNull String s) {
        File f = new File(s);
        if (f.canWrite()) {
            PrefUtils.save(this, Prefs.STORAGE_LOCATION, s + "/pct.droid");
        } else {
            Toast.makeText(this, R.string.not_writable, Toast.LENGTH_SHORT).show();
        }
        mDirectoryChooserFragment.dismiss();
    }

    @Override
    public void onCancelChooser() {
        mDirectoryChooserFragment.dismiss();
    }
}
