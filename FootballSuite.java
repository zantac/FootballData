
package com.waellotfy.footballsuite;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.widget.TextView;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.webkit.MimeTypeMap;
import android.util.Log;
import android.os.Environment;
import android.text.util.Linkify;
import android.text.style.URLSpan;
import android.text.method.LinkMovementMethod;
import android.graphics.PixelFormat;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.RectF;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.HVArrangement;
import com.google.appinventor.components.runtime.util.YailList;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.ByteBufferList;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.OkHttpDownloader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;

public class FootballSuite extends AndroidNonvisibleComponent implements Component {

    // --- Constants ---
    private static final String PREFS_NAME = "FootballDataPlusPrefs";
    private static final String LAST_NEWS_COUNT_KEY = "lastNewsCount";
    private static final String SHOWN_ADS_KEY = "shownAds";
    private static final String CACHE_TIMESTAMP_KEY = "cacheTimestamp";
    private static final long CACHE_EXPIRY_MS = 3600000L; // 1 hour
    
    // Performance Constants
    private static final int MAX_CACHED_VIEWS = 30;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int THREAD_POOL_SIZE = 3;

    // Cache keys for frequently accessed JSON fields
    private static final Set<String> MATCH_FIELDS = new HashSet<>(Arrays.asList(
        "match_id", "date", "time", "status", "home_team_id", "away_team_id"
    ));
    
    // Team fields cache
    private static final Set<String> TEAM_FIELDS = new HashSet<>(Arrays.asList(
        "team_id", "name", "logo", "group", "city", "field", "information"
    ));
    
    // Player fields cache
    private static final Set<String> PLAYER_FIELDS = new HashSet<>(Arrays.asList(
        "players", "coach", "goalkeepers", "defenders", "midfielders", "attackers"
    ));
    
    // --- Class Fields ---
    private final Activity activity;
    private final Context context;
    private final Form form;
    private JSONObject jsonData;
    private SharedPreferences prefs;
    private ScrollView lastCreatedMatchListScrollView;
    private View firstUpcomingMatchView;
    
    // Cached Data for Performance
    private Map<String, JSONObject> cachedTeamsById;
    private List<JSONObject> cachedMatchesList;
    private boolean isDataCached = false;
    private boolean useCache = true;
    
    // Thread Pool
    private ExecutorService backgroundExecutor;
    
    // ScrollView and upcoming match view for team match list
    private ScrollView lastCreatedTeamMatchListScrollView;
    private View firstUpcomingTeamMatchView;
    
    
    private Map<String, WeakReference<View>> viewCache = new ConcurrentHashMap<>();
    
    // --- Data delimiter constants used in scorers/assists arrays ---
    private static final String ASSISTS_DELIMITER_AR = "صناعة الاهداف";
    private static final String ASSISTS_DELIMITER_EN = "Assists";
    private static final String SUBSTITUTES_AR = "البدلاء";
    private static final String SUBSTITUTES_EN = "substitutes";

    // --- Theme color constants (compile-time int literals, no parseColor overhead) ---
    private static final int CLR_CARD   = 0xFF0B2447; // dark blue card bg
    private static final int CLR_AQUA   = 0xFF15D8FF; // aqua accent / key text
    private static final int CLR_TEAL   = 0xFF7EC8D8; // muted teal secondary
    private static final int CLR_BORDER = 0xFF0D3A52; // subtle border
    private static final int CLR_DIALOG = 0xFF071530; // very dark dialog bg
    private static final int CLR_HINT   = 0xFF4DA8C4; // search hint text

    // --- UI Customization Fields ---
    private int primaryTextColor = Color.WHITE;  // Light text for dark backgrounds
    private int secondaryTextColor = Color.parseColor("#FFD8FFFF");  // Light cyan (fully opaque)
    private int cardBackgroundColor = Color.parseColor("#FF0D1F3C");  // Dark blue (fully opaque)
    private int headerBackgroundColor = Color.parseColor("#FF021628");  // Primary dark (fully opaque)
    private int overlayBackgroundColor = Color.argb(0x80, Color.red(headerBackgroundColor), Color.green(headerBackgroundColor), Color.blue(headerBackgroundColor));
    private int dividerColor = Color.parseColor("#FF0D3A52");  // Subtle divider (fully opaque)
    private int accentColor = Color.parseColor("#FFD8FFFF");  // Light cyan accent (fully opaque)
    private int groupHeaderTextColor = Color.WHITE;  // Light text
    private int shadowColor = Color.BLACK; // Shadow color for watermark and overlays
    private int listCardBackgroundColor = Color.parseColor("#FF15D8FF");  // Bright aqua card background
    // --- Helper Classes ---
    private class TeamStats {
        String teamId;
        int position = 0, points = 0, matchesPlayed = 0, wins = 0, draws = 0, 
            losses = 0, goalsFor = 0, goalsAgainst = 0, penaltyPoints = 0;
        TeamStats(String tId) { this.teamId = tId; }
        int getGoalDifference() { return goalsFor - goalsAgainst; }
    }
    
    private class PlayerStat {
        String playerName, teamId, teamName;
        int goals = 0, assists = 0, cleanSheets = 0;
        PlayerStat(String pName, String tId, String tName) { 
            this.playerName = pName; 
            this.teamId = tId; 
            this.teamName = tName; 
        }
    }
    
    // --- Constructor ---
    public FootballSuite(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.activity = (Activity) container.$context();
        this.form = container.$form();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Initialize thread pools
        backgroundExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "BackgroundThread-" + count.getAndIncrement());
            }
        });
        
        
        
        // Initialize caches
        
        cachedTeamsById = new HashMap<>();
        cachedMatchesList = new ArrayList<>();
        
        // Configure Picasso
        try {
            Context appCtx = context.getApplicationContext();
            Picasso.Builder builder = new Picasso.Builder(appCtx);
            builder.downloader(new OkHttpDownloader(appCtx, 50 * 1024 * 1024));
            builder.memoryCache(new LruCache(15 * 1024 * 1024));
            builder.defaultBitmapConfig(Bitmap.Config.RGB_565);
            try {
                Picasso.setSingletonInstance(builder.build());
            } catch (IllegalStateException ise) {
                // Singleton already set by app — keep existing instance
            }
        } catch (Exception e) {
            // Picasso already set
        }
    }
    
    // --- UI Properties ---
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
    @SimpleProperty(description = "Sets the color for main titles, team names, and primary info.")
    public void PrimaryTextColor(int color) { this.primaryTextColor = color; }
    @SimpleProperty public int PrimaryTextColor() { return this.primaryTextColor; }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF444444")
    @SimpleProperty(description = "Sets the color for secondary text like dates, venues, and notes.")
    public void SecondaryTextColor(int color) { this.secondaryTextColor = color; }
    @SimpleProperty public int SecondaryTextColor() { return this.secondaryTextColor; }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFFFFFFF")
    @SimpleProperty(description = "Sets the background color for match cards and news items.")
    public void CardBackgroundColor(int color) { this.cardBackgroundColor = color; }
    @SimpleProperty public int CardBackgroundColor() { return this.cardBackgroundColor; }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFF5F5F5")
    @SimpleProperty(description = "Sets the background color for group headers and date strips.")
    public void HeaderBackgroundColor(int color) { this.headerBackgroundColor = color; }
    @SimpleProperty public int HeaderBackgroundColor() { return this.headerBackgroundColor; }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFE0E0E0")
    @SimpleProperty(description = "Sets the color of the divider lines.")
    public void DividerColor(int color) { this.dividerColor = color; }
    @SimpleProperty public int DividerColor() { return this.dividerColor; }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFB71C1C")
    @SimpleProperty(description = "Sets the accent color used for points, high scores, or special highlights.")
    public void AccentColor(int color) { this.accentColor = color; }
    @SimpleProperty public int AccentColor() { return this.accentColor; }
    
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
    @SimpleProperty(description = "Sets the text color for group/section headers.")
    public void GroupHeaderTextColor(int color) { this.groupHeaderTextColor = color; }
    @SimpleProperty public int GroupHeaderTextColor() { return this.groupHeaderTextColor; }

    // --- Events ---
    @SimpleEvent public void AdClosed() { EventDispatcher.dispatchEvent(this, "AdClosed"); }
    @SimpleEvent public void AfterParsingFail(String error) { EventDispatcher.dispatchEvent(this, "AfterParsingFail", error); }
    @SimpleEvent public void AfterParsingSuccess() { EventDispatcher.dispatchEvent(this, "AfterParsingSuccess"); }
    @SimpleEvent public void AgeClicked(String competitionId, String age, boolean hasSectors, String matchesUrlOrNull) { 
        EventDispatcher.dispatchEvent(this, "AgeClicked", competitionId, age, hasSectors, matchesUrlOrNull); 
    }
    @SimpleEvent public void CompetitionClicked(String competitionId, String competitionName, boolean hasAges) { 
        EventDispatcher.dispatchEvent(this, "CompetitionClicked", competitionId, competitionName, hasAges); 
    }
    @SimpleEvent public void MatchClicked(String matchId) { EventDispatcher.dispatchEvent(this, "MatchClicked", matchId); }
    @SimpleEvent public void NewNewsFound(int newCount, String message) { EventDispatcher.dispatchEvent(this, "NewNewsFound", newCount, message); }
    @SimpleEvent public void SeasonClicked(String seasonName) { EventDispatcher.dispatchEvent(this, "SeasonClicked", seasonName); }
    @SimpleEvent public void SectorClicked(String sectorName, String matchesUrl) { EventDispatcher.dispatchEvent(this, "SectorClicked", sectorName, matchesUrl); }
    @SimpleEvent public void TeamClicked(String teamId) { EventDispatcher.dispatchEvent(this, "TeamClicked", teamId); }
    @SimpleEvent public void UpdateRequired(String newVersionName, String newVersionCode) { 
        EventDispatcher.dispatchEvent(this, "UpdateRequired", newVersionName, newVersionCode); 
    }
    @SimpleEvent public void AppIsUpToDate() { EventDispatcher.dispatchEvent(this, "AppIsUpToDate"); }
    @SimpleEvent(description = "Event raised when an image is clicked in news. Returns the image URL.")
    public void NewsImageClicked(String imageUrl) {
    EventDispatcher.dispatchEvent(this, "NewsImageClicked", imageUrl);
    }

    @SimpleEvent(description = "Event raised when image is shared by the user.")
    public void ImageShared(String imageUrl) {
    EventDispatcher.dispatchEvent(this, "ImageShared", imageUrl);
    }
    @SimpleEvent(description = "Event raised when a player name is clicked. Returns player name, team ID, team name, and stat type (goals/assists/clean sheets).")
    public void PlayerClicked(String playerName, String teamId, String teamName, String statType) {
    EventDispatcher.dispatchEvent(this, "PlayerClicked", playerName, teamId, teamName, statType);
    }
    @SimpleEvent(description = "Event raised when an image download is complete. Returns the file path.")
    public void ImageDownloadComplete(String filePath) {
    EventDispatcher.dispatchEvent(this, "ImageDownloadComplete", filePath);
    }
    
    



     



    // Add this method to show full-screen image
    @SimpleFunction(description = "Shows an image in full screen mode with share button.")
    public void ShowFullScreenImage(String imageUrl) {
    if (imageUrl == null || imageUrl.isEmpty()) {
        AfterParsingFail("Image URL is empty");
        return;
    }
    
    showFullScreenImageDialog(imageUrl);
    }

  




    // --- Main Public Functions ---
    
    @SimpleFunction(description = "Clears all cached data to ensure fresh data is shown when loading new competition")
    public void ClearCache() {
        // Clear cached data
        if (cachedTeamsById != null) {
            cachedTeamsById.clear();
        }
        if (cachedMatchesList != null) {
            cachedMatchesList.clear();
        }
        if (viewCache != null) {
            viewCache.clear();
        }
        isDataCached = false;
        
        // Do not recreate Picasso singleton here — rely on app-wide instance.
        
        // Clear any references to old views
        lastCreatedMatchListScrollView = null;
        firstUpcomingMatchView = null;
        lastCreatedTeamMatchListScrollView = null;
        firstUpcomingTeamMatchView = null;
    }

    @SimpleFunction(description = "Dispose the component: shutdown background threads and clear caches.")
    public void Dispose() {
        try {
            if (backgroundExecutor != null) {
                backgroundExecutor.shutdownNow();
                backgroundExecutor = null;
            }
        } catch (Exception e) {
            Log.w("FootballSuite", "Error shutting down executor", e);
        }

        // Clear caches and view refs
        ClearCache();
    }

    @SimpleFunction(description = "Clears all views inside the provided container and resets internal view references.")
    public void ClearViews(final HVArrangement container) {
        if (container == null) return;
        final View view = container.getView();
        if (view == null) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup vg = (ViewGroup) view;
                    vg.removeAllViews();

                    // Reset match/team list references if they belonged to this container
                    try {
                        if (lastCreatedMatchListScrollView != null) {
                            ViewParent p = lastCreatedMatchListScrollView.getParent();
                            if (p == vg) {
                                lastCreatedMatchListScrollView = null;
                                firstUpcomingMatchView = null;
                            }
                        }
                        if (lastCreatedTeamMatchListScrollView != null) {
                            ViewParent p2 = lastCreatedTeamMatchListScrollView.getParent();
                            if (p2 == vg) {
                                lastCreatedTeamMatchListScrollView = null;
                                firstUpcomingTeamMatchView = null;
                            }
                        }
                    } catch (Exception ignored) {}

                    // Remove any cached view references that are descendants of this container
                    try {
                        Set<String> keysToRemove = new HashSet<>();
                        for (Map.Entry<String, WeakReference<View>> e : viewCache.entrySet()) {
                            WeakReference<View> ref = e.getValue();
                            View v = ref == null ? null : ref.get();
                            if (v == null) { keysToRemove.add(e.getKey()); continue; }
                            if (isViewDescendantOf(v, vg)) keysToRemove.add(e.getKey());
                        }
                        for (String k : keysToRemove) viewCache.remove(k);
                    } catch (Exception ignored) {}

                } catch (Exception e) {
                    Log.w("FootballSuite", "ClearViews error", e);
                }
            }
        });
    }

    // Helper to check whether a view is a descendant of a given ViewGroup
    private boolean isViewDescendantOf(View v, ViewGroup parent) {
        if (v == null || parent == null) return false;
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == parent) return true;
            if (p instanceof View) p = ((View) p).getParent(); else break;
        }
        return false;
    }

    private String getLocalizedEmptyText(String key, String language) {
        boolean isAr = "ar".equalsIgnoreCase(language);
        switch (key) {
            case "match_data":
                return isAr ? "لا يوجد بيانات للمباريات" : "No matches data";
            case "news_empty":
                return isAr ? "لا توجد أخبار" : "No news available";
            case "news_no_match":
                return isAr ? "لا توجد أخبار مطابقة" : "No matching news found";
            default:
                return "";
        }
    }

    private TextView buildEmptyTextView(String message) {
        TextView emptyView = new TextView(context);
        emptyView.setText(message);
        emptyView.setTextColor(this.secondaryTextColor);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(16);
        int padding = dpToPx(16);
        emptyView.setPadding(padding, padding, padding, padding);
        return emptyView;
    }

    @SimpleFunction
public void ParseJsonFromUrl(String url) {
    if (useCache && isDataCached && cachedMatchesList != null && jsonData != null) {
        // Use cached data if available and not expired
        long cacheAge = System.currentTimeMillis() - prefs.getLong(CACHE_TIMESTAMP_KEY, 0);
        if (cacheAge < CACHE_EXPIRY_MS) {
            AfterParsingSuccess();
            return;
        }
    }
    parseJsonFromUrlWithRetry(url, 0);
}
    
    @SimpleFunction(description = "Sets the internal JSON data from a provided text string.")
    public void SetJsonDataFromString(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            AfterParsingFail("Input JSON string is empty.");
            return;
        }
        try {
            // Clear cache before loading new data
            ClearCache();
            this.jsonData = new JSONObject(jsonString);
            prefs.edit().putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis()).apply();
            preCacheData();
            AfterParsingSuccess();
        } catch (JSONException e) {
            this.jsonData = null;
            AfterParsingFail("JSON Parsing Error: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Calculates and displays a league standings table.")
    public void CalculateAndShowStandings(final HVArrangement container, final String groupId, final String stageId, final String lang) {
        // Add null checks
    if (jsonData == null) {
        AfterParsingFail("JSON data is not loaded");
        return;
    }
    
    if (container == null) {
        AfterParsingFail("Container is null");
        return;
    }
    
    if (container.getView() == null) {
        AfterParsingFail("Container view is null");
        return;
    }
        
        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final java.util.List<TeamStats> standings = calculateStandingsForGroup(groupId, stageId);
                    
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (standings == null || standings.isEmpty()) return;
                            ViewGroup vg = (ViewGroup) container.getView();
                            vg.removeAllViews();
                            try {
                                View table = buildStandingsTable(standings, lang);
                                vg.addView(table, new ViewGroup.LayoutParams(-1, -1));
                            } catch (JSONException e) {
                                AfterParsingFail("Error building table: " + e.getMessage());
                            }
                        }
                    });
                } catch (final Exception e) {
                    activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + e.getMessage()); }});
                }
            }
        });
    }

    @SimpleFunction(description = "Creates and displays a searchable list of age categories for a given competition.")
    public void CreateAgeList(HVArrangement container, String competitionId, final String language) {
        if (jsonData == null) return;
        try {
            JSONObject competition = findCompetitionById(competitionId);
            if (competition == null) return;
            final JSONArray ages = competition.optJSONArray("ages");
            if (ages == null || ages.length() == 0) return;
            createSearchableListView(container, language, "age", ages, competitionId, null);
        } catch (Exception e) {
            AfterParsingFail("Error creating age list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates and displays standings tables for all groups found in the data.")
public void CreateAllGroupsStandings(HVArrangement container, final String lang) {
    if (jsonData == null) return;
    try {
        ViewGroup vg = (ViewGroup) container.getView();
        vg.removeAllViews();
        ScrollView sv = new ScrollView(context);
        LinearLayout ml = new LinearLayout(context);
        ml.setOrientation(LinearLayout.VERTICAL);
        ml.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        
        java.util.List<String> groups = getJavaGroupList();
        
        // If no groups or only one group, show standings without collapse header
        if (groups.isEmpty() || groups.size() == 1) {
            // Calculate standings for all teams (pass empty string for group)
            String groupId = groups.isEmpty() ? "" : groups.get(0);
            java.util.List<TeamStats> standings = calculateStandingsForGroup(groupId, "");
            if (standings != null && !standings.isEmpty()) {
                View table = buildStandingsTable(standings, lang);
                ml.addView(table);
            } else {
                TextView noData = new TextView(context);
                noData.setText(getLocalizedText(null, "no_data_available", lang));
                noData.setTextColor(this.secondaryTextColor);
                noData.setGravity(Gravity.CENTER);
                noData.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                ml.addView(noData);
            }
        } else {
            // Multiple groups - show with collapse/expand functionality
            final java.util.Map<String, Boolean> expandedStates = new java.util.HashMap<>();
            for (String gid : groups) {
                expandedStates.put(gid, true);
            }
            
            for (final String gid : groups) {
                if (gid != null && !gid.isEmpty()) {
                    // Create group container
                    final LinearLayout groupContainer = new LinearLayout(context);
                    groupContainer.setOrientation(LinearLayout.VERTICAL);
                    groupContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                    
                    // Create clickable header with arrow
                    final LinearLayout headerLayout = createCollapsibleGroupHeader(
                        getLocalizedText(null, "group", lang) + " " + gid, 
                        lang, 
                        expandedStates.get(gid)
                    );
                    
                    // Create content container
                    final LinearLayout contentContainer = new LinearLayout(context);
                    contentContainer.setOrientation(LinearLayout.VERTICAL);
                    contentContainer.setVisibility(expandedStates.get(gid) ? View.VISIBLE : View.GONE);
                    
                    // Calculate and add standings table
                    java.util.List<TeamStats> standings = calculateStandingsForGroup(gid, "");
                    if (standings != null && !standings.isEmpty()) {
                        View table = buildStandingsTable(standings, lang);
                        contentContainer.addView(table);
                    } else {
                        TextView noData = new TextView(context);
                        noData.setText(getLocalizedText(null, "no_data_available", lang));
                        noData.setTextColor(this.secondaryTextColor);
                        noData.setGravity(Gravity.CENTER);
                        noData.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                        contentContainer.addView(noData);
                    }
                    
                    // Make header clickable
                    headerLayout.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean isExpanded = contentContainer.getVisibility() == View.VISIBLE;
                            if (isExpanded) {
                                contentContainer.setVisibility(View.GONE);
                                updateGroupHeaderArrow(headerLayout, false);
                                expandedStates.put(gid, false);
                            } else {
                                contentContainer.setVisibility(View.VISIBLE);
                                updateGroupHeaderArrow(headerLayout, true);
                                expandedStates.put(gid, true);
                            }
                        }
                    });
                    
                    groupContainer.addView(headerLayout);
                    groupContainer.addView(contentContainer);
                    ml.addView(groupContainer);
                }
            }
        }
        
        sv.addView(ml);
        vg.addView(sv);
    } catch (Exception e) {
        AfterParsingFail("Error creating all standings: " + e.getMessage());
    }
}

            @SimpleFunction(description = "Creates a view showing comprehensive statistics using modern cards.")
    public void CreateAllStatisticsView(final HVArrangement container, final String language) {
        if (this.jsonData == null) { AfterParsingFail("JSON data is not set."); return; }
        
        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray allMatches = jsonData.optJSONArray("matches");
                    final JSONArray allTeams = jsonData.optJSONArray("teams");
                    if (allMatches == null || allTeams == null) { 
                        activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("JSON missing matches/teams."); }});
                        return; 
                    }

                    final java.util.Map<String, List<JSONObject>> matchesByBucket = smartGroupCompletedMatches(allMatches, allTeams);
                    
                    final List<String> sortedBucketNames = new ArrayList<>(matchesByBucket.keySet());
                    Collections.sort(sortedBucketNames, new Comparator<String>() {
                        @Override public int compare(String s1, String s2) {
                            if (s1.equals("المرحلة الاولي")) return -1;
                            if (s2.equals("المرحلة الاولي")) return 1;
                            if (s1.equals(getStatLocalizedText("overall_stats", "en"))) return -1;
                            if (s2.equals(getStatLocalizedText("overall_stats", "en"))) return 1;
                            return s1.compareTo(s2);
                        }
                    });

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ViewGroup vg = (ViewGroup) container.getView();
                                vg.removeAllViews();
                                ScrollView sv = new ScrollView(context);
                                LinearLayout mainLayout = new LinearLayout(context);
                                mainLayout.setOrientation(LinearLayout.VERTICAL);
                                mainLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                                
                                // Dark theme background using shared palette
                                sv.setBackgroundColor(FootballSuite.this.headerBackgroundColor);

                                if (matchesByBucket.isEmpty()) {
                                    TextView noData = new TextView(context);
                                    noData.setText(getStatLocalizedText("no_completed_matches", language));
                                    noData.setTextSize(16);
                                    noData.setTextColor(secondaryTextColor);
                                    noData.setGravity(Gravity.CENTER);
                                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1);
                                    params.setMargins(dpToPx(16), dpToPx(100), dpToPx(16), dpToPx(100));
                                    noData.setLayoutParams(params);
                                    mainLayout.addView(noData);
                                    
                                    sv.addView(mainLayout);
                                    vg.addView(sv);
                                    return;
                                }

                                // UPDATED ICONS (Replaced Snowflake/Hole with Down Trend/Arrow)
                                String[] statTypes = {
                                    "total_matches", "total_goals", "goal_rate", 
                                    "winner_matches", "draw_matches", 
                                    "strongest_attack", "strongest_defense", 
                                    "weakest_attack", "weakest_defense"
                                };
                                
                                String[] statIcons = {
                                    "⚽", "🥅", "📈", 
                                    "🏆", "🤝", 
                                    "🔥", "🧱", 
                                    "📉", "🔻" 
                                };

                                for (String bucketName : sortedBucketNames) {
                                    List<JSONObject> bucketMatches = matchesByBucket.get(bucketName);
                                    if (bucketMatches == null || bucketMatches.isEmpty()) continue;

                                    if (!bucketName.equals(getStatLocalizedText("overall_stats", "en"))) {
                                        mainLayout.addView(createStatsGroupHeaderView(bucketName, language));
                                    }

                                    List<View> cardList = new ArrayList<>();
                                    
                                    for (int i = 0; i < statTypes.length; i++) {
                                        String statType = statTypes[i];
                                        String icon = statIcons[i];
                                        
                                        String value = calculateStatForGroup(bucketMatches, allTeams, statType, language);
                                        String title = getStatLocalizedText(statType + "_title", language);
                                        
                                        View card = createModernStatCard(title, value, icon);
                                        cardList.add(card);
                                    }
                                    
                                    // Add cards to layout in rows of 2
                                    for (int i = 0; i < cardList.size(); i += 2) {
                                        View row = createStatsGridRow(cardList.get(i), (i + 1 < cardList.size()) ? cardList.get(i + 1) : null);
                                        mainLayout.addView(row);
                                    }
                                    
                                    // Spacing between groups
                                    View spacer = new View(context);
                                    spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(24)));
                                    mainLayout.addView(spacer);
                                }
                                
                                sv.addView(mainLayout);
                                vg.addView(sv);
                            } catch (Exception e) {
                                AfterParsingFail("Error building UI: " + e.getMessage());
                            }
                        }
                    });

                } catch (final Exception e) {
                    activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + e.getMessage()); }});
                }
            }
        });
    }
    
    // =======================
    // UPDATED HELPER METHODS
    // =======================

        /**
     * Creates a single modern stat card.
     */
    private View createModernStatCard(String title, String value, String icon) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dpToPx(14), dpToPx(16), dpToPx(14), dpToPx(16));
        card.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dpToPx(12));
        bg.setStroke(dpToPx(1), CLR_BORDER);
        if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg); else card.setBackgroundDrawable(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(5));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, 0, 0);
        card.setLayoutParams(params);

        // Icon badge
        LinearLayout iconBadge = new LinearLayout(context);
        iconBadge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#0D3050"));
        badgeBg.setShape(GradientDrawable.OVAL);
        if (Build.VERSION.SDK_INT >= 16) iconBadge.setBackground(badgeBg); else iconBadge.setBackgroundDrawable(badgeBg);
        int badgeSize = dpToPx(44);
        iconBadge.setLayoutParams(new LinearLayout.LayoutParams(badgeSize, badgeSize));

        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextSize(22);
        iconView.setGravity(Gravity.CENTER);
        iconBadge.addView(iconView);

        // Text column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(0, -2, 1);
        tcp.setMargins(dpToPx(12), 0, 0, 0);
        textCol.setLayoutParams(tcp);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(18);
        valueView.setTypeface(null, Typeface.BOLD);
        valueView.setTextColor(CLR_AQUA);
        valueView.setGravity(Gravity.START);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(11);
        titleView.setTextColor(Color.parseColor("#AACCDD"));
        titleView.setGravity(Gravity.START);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(0, dpToPx(3), 0, 0);
        titleView.setLayoutParams(tlp);

        textCol.addView(valueView);
        textCol.addView(titleView);

        card.addView(iconBadge);
        card.addView(textCol);

        return card;
    }

    /**
     * Creates a horizontal row for 2 cards.
     */
    private View createStatsGridRow(View card1, View card2) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
        row.setLayoutParams(rowParams);

        if (card1 != null) {
            LinearLayout.LayoutParams p1 = (LinearLayout.LayoutParams) card1.getLayoutParams();
            p1.setMargins(dpToPx(8), 0, dpToPx(4), 0); 
            row.addView(card1, p1);
        }
        
        if (card2 != null) {
            LinearLayout.LayoutParams p2 = (LinearLayout.LayoutParams) card2.getLayoutParams();
            p2.setMargins(dpToPx(4), 0, dpToPx(8), 0); 
            row.addView(card2, p2);
        }
        
        // --- NEW LOGIC HERE ---
        // After adding views, run this to equalize heights
        setHeightToTallest(card1, card2);

        return row;
    }

    /**
     * NEW HELPER METHOD
     * Compares two views and sets the shorter one's height to match the taller one.
     * Uses a ViewTreeObserver to wait for layout to complete.
     */
    private void setHeightToTallest(final View view1, final View view2) {
        if (view1 == null || view2 == null) return;

        // We need to wait for the layout to pass so getMeasuredHeight() returns a real value
        view1.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove the listener immediately so we don't run this logic multiple times
                view1.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                // Get the actual heights of both cards
                int height1 = view1.getMeasuredHeight();
                int height2 = view2.getMeasuredHeight();

                // Find the maximum height
                int maxHeight = Math.max(height1, height2);

                // Apply the maximum height to both cards
                ViewGroup.LayoutParams params1 = view1.getLayoutParams();
                params1.height = maxHeight;
                view1.setLayoutParams(params1);

                ViewGroup.LayoutParams params2 = view2.getLayoutParams();
                params2.height = maxHeight;
                view2.setLayoutParams(params2);
            }
        });
    }

    // RecyclerView adapter for matches
    private class MatchRecyclerAdapter extends RecyclerView.Adapter<MatchRecyclerAdapter.MatchViewHolder> {
        private List<JSONObject> items;
        private JSONArray teamsArray;
        private String lang;

        MatchRecyclerAdapter(List<JSONObject> initial, JSONArray teams, String lang) {
            this.items = initial == null ? new ArrayList<JSONObject>() : new ArrayList<>(initial);
            this.teamsArray = teams;
            this.lang = lang;
            setHasStableIds(true);
        }

        void updateData(List<JSONObject> newItems) {
            this.items = newItems == null ? new ArrayList<JSONObject>() : new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            String id = items.get(position).optString("match_id", String.valueOf(position));
            return id.hashCode();
        }

        @Override
        public MatchViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new MatchViewHolder(container);
        }

        @Override
        public void onBindViewHolder(MatchViewHolder holder, int position) {
            JSONObject match = items.get(position);
            String matchId = match.optString("match_id", "");
            // Skip rebuild if this holder is already showing the same match
            if (matchId.equals(holder.boundMatchId) && holder.container.getChildCount() > 0) return;
            holder.container.removeAllViews();
            holder.boundMatchId = matchId;
            try {
                View itemView = createMatchItemView(match, teamsArray, lang);
                holder.container.addView(itemView);
            } catch (Exception e) {
                Log.e("FootballSuite", "Error rendering match", e);
                TextView tv = new TextView(context);
                tv.setText("Error rendering match");
                tv.setTextColor(secondaryTextColor);
                holder.container.addView(tv);
            }
        }

        @Override
        public int getItemCount() { return items == null ? 0 : items.size(); }

        class MatchViewHolder extends RecyclerView.ViewHolder {
            FrameLayout container;
            String boundMatchId = "";
            MatchViewHolder(View itemView) {
                super(itemView);
                container = (FrameLayout) itemView;
            }
        }
    }

    // RecyclerView adapter for news
    private class NewsRecyclerAdapter extends RecyclerView.Adapter<NewsRecyclerAdapter.NewsViewHolder> {
        private List<JSONObject> items;
        private String lang;
        private TextView emptyView;

        NewsRecyclerAdapter(List<JSONObject> initial, String lang) {
            this.items = initial == null ? new ArrayList<JSONObject>() : new ArrayList<>(initial);
            this.lang = lang;
            setHasStableIds(true);
        }

        void setEmptyView(TextView emptyView) {
            this.emptyView = emptyView;
            refreshEmptyView();
        }

        private void refreshEmptyView() {
            if (emptyView == null) return;
            emptyView.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        }

        void updateData(List<JSONObject> newItems) {
            this.items = newItems == null ? new ArrayList<JSONObject>() : new ArrayList<>(newItems);
            notifyDataSetChanged();
            refreshEmptyView();
        }

        @Override
        public long getItemId(int position) {
            JSONObject item = items.get(position);
            String key = item.optString("date", "") + item.optString("title", item.optString("title_ar", String.valueOf(position)));
            return key.hashCode();
        }

        @Override
        public NewsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new NewsViewHolder(container);
        }

        @Override
        public void onBindViewHolder(NewsViewHolder holder, int position) {
            JSONObject news = items.get(position);
            long newsId = getItemId(position);
            // Skip rebuild if this holder is already showing the same news item
            if (newsId == holder.boundItemId && holder.container.getChildCount() > 0) return;
            holder.container.removeAllViews();
            holder.boundItemId = newsId;
            try {
                View v = createNewsCardView(news, lang);
                holder.container.addView(v);
            } catch (Exception e) {
                Log.e("FootballSuite", "Error rendering news", e);
                TextView tv = new TextView(context);
                tv.setText("Error rendering news");
                tv.setTextColor(secondaryTextColor);
                holder.container.addView(tv);
            }
        }

        @Override
        public int getItemCount() { return items == null ? 0 : items.size(); }

        class NewsViewHolder extends RecyclerView.ViewHolder {
            FrameLayout container;
            long boundItemId = Long.MIN_VALUE;
            NewsViewHolder(View itemView) {
                super(itemView);
                container = (FrameLayout) itemView;
            }
        }
    }

    private class SearchableListAdapter extends RecyclerView.Adapter<SearchableListAdapter.SearchableViewHolder> {
        private List<JSONObject> originalItems;
        private List<JSONObject> filteredItems;
        private String language;
        private String type;
        private String competitionId;
        private String age;
        private TextView emptyView;

        SearchableListAdapter(JSONArray dataArray, String language, String type, String competitionId, String age, TextView emptyView) {
            this.originalItems = new ArrayList<>();
            this.filteredItems = new ArrayList<>();
            this.language = language;
            this.type = type;
            this.competitionId = competitionId;
            this.age = age;
            this.emptyView = emptyView;

            if (dataArray != null) {
                for (int i = 0; i < dataArray.length(); i++) {
                    try {
                        this.originalItems.add(dataArray.getJSONObject(i));
                    } catch (JSONException ignored) {}
                }
            }
            this.filteredItems.addAll(this.originalItems);
            refreshEmptyView();
        }

        void filter(String query) {
            String lower = query == null ? "" : query.toLowerCase();
            filteredItems.clear();
            for (JSONObject item : originalItems) {
                String name = getSearchItemName(item, type, language);
                if (name.toLowerCase().contains(lower)) {
                    filteredItems.add(item);
                }
            }
            notifyDataSetChanged();
            refreshEmptyView();
        }

        private void refreshEmptyView() {
            if (emptyView == null) return;
            emptyView.setVisibility(filteredItems.isEmpty() ? View.VISIBLE : View.GONE);
        }

        private String getSearchItemName(JSONObject item, String type, String language) {
            if (item == null) return "";
            if ("age".equals(type)) return item.optString("age", "");
            if ("sector".equals(type)) return item.optString("name", "");
            if ("season".equals(type)) return item.optString("season", "");
            if ("venue".equals(type)) {
                String venueName = item.optString("name", "");
                if (venueName == null || venueName.isEmpty()) {
                    return getLocalizedText(item, "venue", language);
                }
                return venueName;
            }
            return getLocalizedText(item, "name", language);
        }

        @Override
        public SearchableViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // Build the card layout once — onBindViewHolder only updates text + click listener
            LinearLayout itemCard = new LinearLayout(context);
            itemCard.setOrientation(LinearLayout.VERTICAL);
            itemCard.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            itemCard.setGravity(Gravity.CENTER);
            itemCard.setMinimumHeight(dpToPx(80));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
            itemCard.setLayoutParams(itemParams);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                itemCard.setBackground(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
            } else {
                itemCard.setBackgroundDrawable(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                itemCard.setElevation(dpToPx(4));
            }
            TextView nameView = new TextView(context);
            nameView.setTextColor(CLR_AQUA);
            nameView.setTextSize(16);
            nameView.setTypeface(null, Typeface.BOLD);
            nameView.setGravity(Gravity.CENTER);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            itemCard.addView(nameView);
            return new SearchableViewHolder(itemCard, nameView);
        }

        @Override
        public void onBindViewHolder(SearchableViewHolder holder, int position) {
            final JSONObject item = filteredItems.get(position);
            final String name = getSearchItemName(item, type, language);
            holder.nameView.setText(name);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if ("season".equals(type)) {
                            SeasonClicked(name);
                        } else if ("competition".equals(type)) {
                            String id = item.optString("competition_id", "");
                            boolean hasAges = item.optJSONArray("ages") != null;
                            CompetitionClicked(id, name, hasAges);
                        } else if ("age".equals(type)) {
                            String ageValue = item.optString("age", "");
                            String url = item.optString("matchesurl", "");
                            boolean hasSectors = item.optJSONArray("matchesurl") != null;
                            if (hasSectors) url = "";
                            AgeClicked(competitionId, ageValue, hasSectors, url);
                        } else if ("sector".equals(type)) {
                            SectorClicked(name, item.optString("url", ""));
                        } else if ("venue".equals(type)) {
                            String url = item.optString("url", "");
                            if (isValid(url)) {
                                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            }
                        }
                    } catch (Exception e) {
                        Log.w("FootballSuite", "Item click error", e);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredItems == null ? 0 : filteredItems.size();
        }

        class SearchableViewHolder extends RecyclerView.ViewHolder {
            final TextView nameView;
            SearchableViewHolder(LinearLayout card, TextView nameView) {
                super(card);
                this.nameView = nameView;
            }
        }
    }

    @SimpleFunction(description = "Creates and displays a searchable list of competitions for a given season.")
    public void CreateCompetitionList(HVArrangement container, String seasonName, final String language) {
        if (jsonData == null) return;
        try {
            JSONObject season = findObjectInSeasons(jsonData, "season", seasonName);
            if (season == null) return;
            final JSONArray competitions = season.optJSONArray("competitions");
            if (competitions == null || competitions.length() == 0) return;
            createSearchableListView(container, language, "competition", competitions, null, null);
        } catch (Exception e) {
            AfterParsingFail("Error creating competition list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view with the detailed header for a specific match, including teams, logos, and score/time.")
public void CreateMatchDetailHeader(HVArrangement container, String matchId, String lang) {
    if (jsonData == null) return;
    try {
        JSONObject matchObject = findMatchById(matchId);
        if (matchObject == null) return;
        ViewGroup vg = (ViewGroup) container.getView();
        vg.removeAllViews();
        LinearLayout ml = new LinearLayout(context);
        ml.setOrientation(LinearLayout.VERTICAL);
        
        // Create styled date header (dark blue rounded card with aqua text)
        View dateHeader = createStyledDateHeader(matchObject.getString("date"), 1, lang, true, matchObject.getString("week"));
        ml.addView(dateHeader);
        
        String groupName = getMatchGroupName(matchObject);
        if (groupName != null) ml.addView(createStyledGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));
        
        // ✅ Use the new clickable header view instead of regular match item
        ml.addView(createMatchDetailHeaderView(matchObject, jsonData.optJSONArray("teams"), lang));
        
        vg.addView(ml);
    } catch (Exception e) {
        AfterParsingFail("Error creating match header: " + e.getMessage());
    }
}

    @SimpleFunction(description = "Creates a view showing all events (goals and assists) for a specific match.")
    public void CreateMatchEventsList(HVArrangement container, String matchId, final String lang) {
        if (jsonData == null) return;
        try {
            final JSONObject match = findMatchById(matchId);
            if (match == null) return;

            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();

            ScrollView mainScrollView = new ScrollView(context);
            final LinearLayout mainLayout = new LinearLayout(context);
            mainLayout.setOrientation(LinearLayout.VERTICAL);
            mainLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

            final ArrayList<String> allScorers = new ArrayList<>();
            final ArrayList<String> allAssists = new ArrayList<>();

            try {
                JSONArray homeEvents = getLocalizedArray(match, "home_scorers", lang);
                if (homeEvents != null) {
                    boolean isAssistSection = false;
                    for (int i = 0; i < homeEvents.length(); i++) {
                        String event = homeEvents.getString(i);
                        if (event.equals(ASSISTS_DELIMITER_AR) || event.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) { isAssistSection = true; continue; }
                        if (!event.isEmpty()) { if (isAssistSection) allAssists.add(event); else allScorers.add(event); }
                    }
                }
                JSONArray awayEvents = getLocalizedArray(match, "away_scorers", lang);
                if (awayEvents != null) {
                    boolean isAssistSection = false;
                    for (int i = 0; i < awayEvents.length(); i++) {
                        String event = awayEvents.getString(i);
                        if (event.equals(ASSISTS_DELIMITER_AR) || event.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) { isAssistSection = true; continue; }
                        if (!event.isEmpty()) { if (isAssistSection) allAssists.add(event); else allScorers.add(event); }
                    }
                }
            } catch (JSONException e) { Log.w("FootballSuite", "Error parsing match events", e); }

            if (!allScorers.isEmpty()) {
                mainLayout.addView(createEventHeader(getLocalizedText(null, "goals", lang), "soccer_ball.png", lang));
                for (String scorer : allScorers) {
                    mainLayout.addView(createPlayerCardView(scorer));
                }
            }

            if (!allAssists.isEmpty()) {
                mainLayout.addView(createEventHeader(getLocalizedText(null, "assists", lang), "goal_icon.png", lang));
                for (String assist : allAssists) {
                    mainLayout.addView(createPlayerCardView(assist));
                }
            }

            mainScrollView.addView(mainLayout);
            vg.addView(mainScrollView);

        } catch (Exception e) {
            AfterParsingFail("Error creating match events list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view showing the starting lineup for both teams in a specific match.")
    public void CreateMatchLineup(HVArrangement container, String matchId, String lang) { 
        createTwoColumnDetailView(container, matchId, lang, "lineup", "home_squade", "away_squade"); 
    }

    @SimpleFunction(description = "Creates a scrollable list of all matches, grouped by date, week, and group.")
public void CreateMatchList(HVArrangement container, final String lang) {
    if (jsonData == null) return;
    
    backgroundExecutor.submit(new Runnable() {
        @Override
        public void run() {
            try {
                JSONArray matches = jsonData.optJSONArray("matches");
                final JSONArray teams = jsonData.optJSONArray("teams");
                java.util.List<JSONObject> mList = new java.util.ArrayList<>();
                if (matches != null) {
                    for (int i = 0; i < matches.length(); i++) {
                        mList.add(matches.getJSONObject(i));
                    }
                }
                Collections.sort(mList, new Comparator<JSONObject>() {
                    @Override public int compare(JSONObject o1, JSONObject o2) {
                        try {
                            int d = o1.getString("date").compareTo(o2.getString("date"));
                            if (d != 0) return d;
                            return o1.optString("time", "").compareTo(o2.optString("time", ""));
                        } catch (JSONException e) { return 0; }
                    }
                });
                
                final boolean noMatches = mList.isEmpty();

                java.util.Map<String, java.util.Map<String, java.util.List<JSONObject>>> byDateAndWeek = new java.util.LinkedHashMap<>();
                for (JSONObject match : mList) {
                    String date = match.getString("date");
                    String week = match.getString("week");
                    if (!byDateAndWeek.containsKey(date)) byDateAndWeek.put(date, new java.util.LinkedHashMap<String, java.util.List<JSONObject>>());
                    java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                    if (byWeek == null) continue;
                    if (!byWeek.containsKey(week)) byWeek.put(week, new java.util.ArrayList<JSONObject>());
                    java.util.List<JSONObject> weekList = byWeek.get(week);
                    if(weekList != null) weekList.add(match);
                }

                // Determine which date section to auto-expand and scroll to:
                // 1. Today (if matches exist today)
                // 2. Nearest future date (if no matches today)
                // 3. Last date in data (season over — show most recent results)
                String todayStr = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
                String scrollTarget = null;
                if (byDateAndWeek.containsKey(todayStr)) {
                    scrollTarget = todayStr;
                } else {
                    for (String d : byDateAndWeek.keySet()) {
                        if (d.compareTo(todayStr) > 0) { scrollTarget = d; break; }
                    }
                    if (scrollTarget == null) {
                        for (String d : byDateAndWeek.keySet()) scrollTarget = d; // last key = latest past date
                    }
                }
                final String finalScrollTarget = scrollTarget;

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ViewGroup vg = (ViewGroup) container.getView();
                            vg.removeAllViews();

                            if (noMatches) {
                                vg.addView(buildEmptyTextView(getLocalizedEmptyText("match_data", lang)));
                                return;
                            }

                            final ScrollView sv = new ScrollView(context);
                            final LinearLayout ml = new LinearLayout(context);
                            ml.setOrientation(LinearLayout.VERTICAL);

                            lastCreatedMatchListScrollView = sv;
                            firstUpcomingMatchView = null;

                            for (String date : byDateAndWeek.keySet()) {
                                java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                                if (byWeek == null) continue;

                                int totalDayMatches = 0;
                                for (java.util.List<JSONObject> weekMatchesList : byWeek.values()) {
                                    totalDayMatches += weekMatchesList.size();
                                }
                                boolean multiWeek = byWeek.size() > 1;
                                String firstWeek = byWeek.keySet().iterator().next();
                                boolean isTarget = date.equals(finalScrollTarget);

                                try {
                                    if (byWeek.size() <= 1) {
                                        View section = createCollapsibleDateSection(date, totalDayMatches, lang, !multiWeek, firstWeek, byWeek, teams, isTarget);
                                        if (isTarget && firstUpcomingMatchView == null) firstUpcomingMatchView = section;
                                        ml.addView(section);
                                    } else {
                                        // Multiple different week numbers on same date -> split per week
                                        boolean firstWeekSection = true;
                                        for (String wk : byWeek.keySet()) {
                                            java.util.List<JSONObject> weekMatches = byWeek.get(wk);
                                            int wkCount = weekMatches == null ? 0 : weekMatches.size();
                                            java.util.Map<String, java.util.List<JSONObject>> singleWeekMap = new java.util.LinkedHashMap<String, java.util.List<JSONObject>>();
                                            singleWeekMap.put(wk, weekMatches == null ? new java.util.ArrayList<JSONObject>() : weekMatches);
                                            boolean expandThisWeek = isTarget && firstWeekSection;
                                            View section = createCollapsibleDateSection(date, wkCount, lang, true, wk, singleWeekMap, teams, expandThisWeek);
                                            if (expandThisWeek && firstUpcomingMatchView == null) firstUpcomingMatchView = section;
                                            ml.addView(section);
                                            firstWeekSection = false;
                                        }
                                    }
                                } catch (ParseException e) {
                                    // Ignore invalid date formats
                                }
                            }

                            sv.addView(ml);
                            vg.addView(sv);
                        } catch (RuntimeException re) {
                            Log.e("FootballSuite", "Runtime error creating match list", re);
                            AfterParsingFail("Error creating match list: " + re.getMessage());
                        }
                    }
                });
            } catch (JSONException je) {
                Log.e("FootballSuite", "JSON error creating match list (background)", je);
                activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + je.getMessage()); }});
            } catch (RuntimeException re) {
                Log.e("FootballSuite", "Runtime error creating match list (background)", re);
                activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + re.getMessage()); }});
            }
        }
    });
}

    @SimpleFunction(description = "Creates a view showing the red cards for both teams in a specific match.")
    public void CreateMatchRedCards(HVArrangement container, String matchId, String lang) { 
        createTwoColumnDetailView(container, matchId, lang, "red_cards", "home_rc", "away_rc"); 
    }

    @SimpleFunction(description = "Creates a view showing the goal scorers for both teams in a specific match.")
    public void CreateMatchScorers(HVArrangement container, String matchId, String lang) { 
        createTwoColumnDetailView(container, matchId, lang, "scorers_list", "home_scorers", "away_scorers"); 
    }

    @SimpleFunction(description = "Creates a view showing the substitutions for both teams in a specific match.")
    public void CreateMatchSubstitutes(HVArrangement container, String matchId, String lang) { 
        createTwoColumnDetailView(container, matchId, lang, "substitutions", "home_sub", "away_sub"); 
    }

    @SimpleFunction(description = "Creates a view showing the yellow cards for both teams in a specific match.")
    public void CreateMatchYellowCards(HVArrangement container, String matchId, String lang) { 
        createTwoColumnDetailView(container, matchId, lang, "yellow_cards", "home_yc", "away_yc"); 
    }

    @SimpleFunction(description = "Creates and displays a searchable list of news articles with newest first.")
public void CreateNewsList(HVArrangement container, final String language) {
    if (jsonData == null) return;
    ViewGroup vg = (ViewGroup) container.getView();
    vg.removeAllViews();
    try {
        JSONArray newsArray = jsonData.optJSONArray("news");
            if (newsArray == null || newsArray.length() == 0) {
                vg.addView(buildEmptyTextView(getLocalizedEmptyText("news_empty", language)));
                return;
            }
        final List<JSONObject> newsList = new ArrayList<>();
        for (int i = 0; i < newsArray.length(); i++) {
            newsList.add(newsArray.getJSONObject(i));
        }
        
        // Sort news by date - newest first (2024-01-15 format)
        Collections.sort(newsList, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject o1, JSONObject o2) {
                try {
                    String date1 = o1.optString("date", "0000-00-00");
                    String date2 = o2.optString("date", "0000-00-00");
                    return date2.compareTo(date1); // Newest first (reverse order)
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        
        final int lastSeenCount = prefs.getInt(LAST_NEWS_COUNT_KEY, 0);
        final int currentCount = newsList.size();
        
        // Calculate new news count based on sorted order
        final int newCount;
        if (currentCount > lastSeenCount) {
            newCount = currentCount - lastSeenCount;
            String message = "ar".equalsIgnoreCase(language) ? 
                newCount + " خبر جديد غير مقروء" : 
                newCount + " new unread news";
            NewNewsFound(newCount, message);
        } else {
            newCount = 0;
        }
        
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        
        // RecyclerView for news with dynamic filtering
        final RecyclerView newsRecyclerView = new RecyclerView(context);
        LinearLayout.LayoutParams rvParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0);
        rvParams.weight = 1;
        newsRecyclerView.setLayoutParams(rvParams);
        newsRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        
        final NewsRecyclerAdapter newsAdapter = new NewsRecyclerAdapter(newsList, language);
        newsRecyclerView.setAdapter(newsAdapter);
        
        final TextView emptyNewsView = buildEmptyTextView(getLocalizedEmptyText("news_no_match", language));
        emptyNewsView.setVisibility(newsList.isEmpty() ? View.VISIBLE : View.GONE);
        newsAdapter.setEmptyView(emptyNewsView);
        
        EditText searchBar = new EditText(context);
        searchBar.setHint(getLocalizedText(null, "search", language));
        searchBar.setTextColor(CLR_AQUA);
        searchBar.setHintTextColor(CLR_HINT);
        GradientDrawable newsSearchBg = new GradientDrawable();
        newsSearchBg.setColor(CLR_CARD);
        newsSearchBg.setCornerRadius(dpToPx(12));
        newsSearchBg.setStroke(dpToPx(1), CLR_AQUA);
        if (Build.VERSION.SDK_INT >= 16) searchBar.setBackground(newsSearchBg); else searchBar.setBackgroundDrawable(newsSearchBg);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        searchParams.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8));
        searchBar.setLayoutParams(searchParams);
        searchBar.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Filter list based on search
                String lowerFilter = s == null ? "" : s.toString().toLowerCase().trim();
                List<JSONObject> filteredList = new ArrayList<>();
                for (JSONObject item : newsList) {
                    try {
                        String title = getLocalizedText(item, "title", language).toLowerCase();
                        String searchText = buildNewsSearchText(item, language).toLowerCase();
                        if (lowerFilter.isEmpty() || title.contains(lowerFilter) || searchText.contains(lowerFilter)) {
                            filteredList.add(item);
                        }
                    } catch (Exception e) {
                        Log.w("FootballSuite", "News filter error", e);
                    }
                }
                newsAdapter.updateData(filteredList);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        mainLayout.addView(searchBar);
        mainLayout.addView(newsRecyclerView);
        mainLayout.addView(emptyNewsView);
        vg.addView(mainLayout);
        
        } catch (JSONException je) {
            Log.e("FootballSuite", "JSON error creating news list", je);
            AfterParsingFail("Error creating news list: " + je.getMessage());
        } catch (RuntimeException re) {
            Log.e("FootballSuite", "Runtime error creating news list", re);
            AfterParsingFail("Error creating news list: " + re.getMessage());
        }
}

    private void buildFilteredNewsList(LinearLayout container, List<JSONObject> newsList, String language, String filter, int newCount, View[] firstNewItemView) {
        container.removeAllViews();
        String lowerFilter = filter == null ? "" : filter.toLowerCase().trim();
        int visibleCount = 0;
        try {
            for (int i = 0; i < newsList.size(); i++) {
                JSONObject newsItem = newsList.get(i);
                if (!lowerFilter.isEmpty()) {
                    String title = getLocalizedText(newsItem, "title", language).toLowerCase();
                    String searchText = buildNewsSearchText(newsItem, language).toLowerCase();
                    if (!title.contains(lowerFilter) && !searchText.contains(lowerFilter)) {
                        continue;
                    }
                }
                View newsCard = createNewsCardView(newsItem, language);
                container.addView(newsCard);
                if (firstNewItemView != null && lowerFilter.isEmpty() && visibleCount < newCount && firstNewItemView[0] == null) {
                    firstNewItemView[0] = newsCard;
                }
                visibleCount++;
            }
            if (visibleCount == 0) {
                TextView emptyView = buildEmptyTextView(getLocalizedEmptyText("news_no_match", language));
                container.addView(emptyView);
            }
        } catch (JSONException je) {
            Log.e("FootballSuite", "JSON error filtering news list", je);
            AfterParsingFail("Error filtering news list: " + je.getMessage());
        } catch (RuntimeException re) {
            Log.e("FootballSuite", "Runtime error filtering news list", re);
            AfterParsingFail("Error filtering news list: " + re.getMessage());
        }
    }

    private String buildNewsSearchText(JSONObject newsItem, String language) {
        StringBuilder builder = new StringBuilder();
        builder.append(getLocalizedText(newsItem, "title", language)).append(" ");
        builder.append(getLocalizedText(newsItem, "date", language)).append(" ");
        Object detailsObj = newsItem.opt("details");
        try {
            if (detailsObj instanceof JSONArray) {
                JSONArray detailsArray = (JSONArray) detailsObj;
                for (int j = 0; j < detailsArray.length(); j++) {
                    builder.append(detailsArray.optString(j)).append(" ");
                }
            } else {
                builder.append(getLocalizedText(newsItem, "details", language));
            }
        } catch (RuntimeException re) {
            Log.w("FootballSuite", "Error building news search text", re);
        }
        return builder.toString();
    }

    @SimpleFunction(description = "Creates and displays a random ad from the 'Ads' section of the JSON data.")
    public void CreateRandomAd(HVArrangement container, final long timeInMilliseconds) {
        if (jsonData == null) return;
        final ViewGroup vg = (ViewGroup) container.getView();
        vg.removeAllViews();
        try {
            JSONArray adsArray = jsonData.optJSONArray("Ads");
            if (adsArray == null || adsArray.length() == 0) return;
            List<JSONObject> validAds = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            Date today = new Date();
            for (int i = 0; i < adsArray.length(); i++) {
                JSONObject ad = adsArray.getJSONObject(i);
                String expireDateStr = ad.optString("expire_date", null);
                if (expireDateStr != null) {
                    try {
                        if (!sdf.parse(expireDateStr).before(today)) validAds.add(ad);
                    } catch (Exception e) {
                        validAds.add(ad);
                    }
                } else {
                    validAds.add(ad);
                }
            }
            if (validAds.isEmpty()) return;
            List<String> shownAds = new ArrayList<>(Arrays.asList(prefs.getString(SHOWN_ADS_KEY, "").split(",")));
            List<JSONObject> unseenAds = new ArrayList<>();
            for (JSONObject ad : validAds) {
                if (!shownAds.contains(ad.optString("name"))) {
                    unseenAds.add(ad);
                }
            }
            if (unseenAds.isEmpty() && !validAds.isEmpty()) {
                prefs.edit().putString(SHOWN_ADS_KEY, "").apply();
                unseenAds = validAds;
            }
            if (unseenAds.isEmpty()) return;
            final JSONObject randomAd = unseenAds.get(new Random().nextInt(unseenAds.size()));

            final RelativeLayout adContainer = createAdCardView(randomAd);
            vg.addView(adContainer);

            if (timeInMilliseconds > 0) {
                final RelativeLayout countdownLayout = new RelativeLayout(context);
                final RelativeLayout.LayoutParams countdownParams = new RelativeLayout.LayoutParams(dpToPx(50), dpToPx(50));
                countdownParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                countdownParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                countdownParams.setMargins(0, dpToPx(16), dpToPx(16), 0);
                countdownLayout.setLayoutParams(countdownParams);
                final ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
                progressBar.setMax((int) timeInMilliseconds);
                progressBar.setProgress(0);
                GradientDrawable backgroundCircle = new GradientDrawable();
                backgroundCircle.setShape(GradientDrawable.OVAL);
                backgroundCircle.setColor(Color.argb(0x4D, Color.red(FootballSuite.this.listCardBackgroundColor), Color.green(FootballSuite.this.listCardBackgroundColor), Color.blue(FootballSuite.this.listCardBackgroundColor)));
                GradientDrawable progressCircle = new GradientDrawable();
                progressCircle.setShape(GradientDrawable.OVAL);
                progressCircle.setColor(FootballSuite.this.listCardBackgroundColor);
                Drawable[] layers = {
                        backgroundCircle,
                        new android.graphics.drawable.ClipDrawable(progressCircle, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL)
                };
                LayerDrawable layerDrawable = new LayerDrawable(layers);
                progressBar.setProgressDrawable(layerDrawable);
                final TextView countdownText = new TextView(context);
                countdownText.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
                countdownText.setTextColor(FootballSuite.this.primaryTextColor);
                countdownText.setTextSize(18);
                countdownText.setTypeface(null, Typeface.BOLD);
                countdownText.setGravity(Gravity.CENTER);
                countdownLayout.addView(progressBar);
                countdownLayout.addView(countdownText);
                adContainer.addView(countdownLayout);

                new CountDownTimer(timeInMilliseconds, 50) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        final int secondsRemaining = (int) Math.ceil((double) millisUntilFinished / 1000.0);
                        final int progress = (int)(timeInMilliseconds - millisUntilFinished);
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                countdownText.setText(String.valueOf(secondsRemaining));
                                progressBar.setProgress(progress);
                            }
                        });
                    }
                    @Override
                    public void onFinish() {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (adContainer != null) adContainer.removeView(countdownLayout);
                                TextView closeButton = new TextView(context);
                                closeButton.setLayoutParams(countdownParams);
                                closeButton.setText("X");
                                closeButton.setTextColor(FootballSuite.this.primaryTextColor);
                                closeButton.setTextSize(20);
                                closeButton.setTypeface(null, Typeface.BOLD);
                                closeButton.setGravity(Gravity.CENTER);
                                GradientDrawable closeBg = new GradientDrawable();
                                closeBg.setShape(GradientDrawable.OVAL);
                                closeBg.setColor(FootballSuite.this.cardBackgroundColor);
                                closeBg.setStroke(dpToPx(2), FootballSuite.this.listCardBackgroundColor);
                                if (Build.VERSION.SDK_INT >= 16) {
                                    closeButton.setBackground(closeBg);
                                } else {
                                    closeButton.setBackgroundDrawable(closeBg);
                                }

                                closeButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (vg != null && adContainer.getParent() == vg) {
                                            vg.removeView(adContainer);
                                        }
                                        AdClosed();
                                    }
                                });

                                if (adContainer != null) {
                                    adContainer.addView(closeButton);
                                }
                            }
                        });
                    }
                }.start();
            }

            String newShownAds = prefs.getString(SHOWN_ADS_KEY, "") + randomAd.optString("name") + ",";
            prefs.edit().putString(SHOWN_ADS_KEY, newShownAds).apply();
        } catch (Exception e) {
            AfterParsingFail("Error creating ad view: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates and displays a searchable list of seasons.")
    public void CreateSeasonList(HVArrangement container, final String language) {
        if (jsonData == null) return;
        try {
            JSONArray seasons = null;
            if (jsonData.has("seasons")) {
                Object seasonsObj = jsonData.get("seasons");
                if (seasonsObj instanceof JSONArray) {
                    seasons = (JSONArray) seasonsObj;
                } else if (seasonsObj instanceof JSONObject) {
                    seasons = new JSONArray();
                    seasons.put(seasonsObj);
                }
            }
            if (seasons == null || seasons.length() == 0) return;
            createSearchableListView(container, language, "season", seasons, null, null);
        } catch (Exception e) {
            AfterParsingFail("Error creating season list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates and displays a searchable list of sectors for a given competition and age group.")
    public void CreateSectorList(HVArrangement container, String competitionId, String age, final String language) {
        if (jsonData == null) return;
        try {
            JSONObject competition = findCompetitionById(competitionId);
            if (competition == null) return;
            JSONObject ageObject = findObjectById(competition.optJSONArray("ages"), "age", age);
            if (ageObject == null) return;
            final JSONArray sectors = getLocalizedArray(ageObject, "sector", language);
            final JSONArray urls = ageObject.optJSONArray("matchesurl");
            if (sectors == null || urls == null || sectors.length() == 0 || urls.length() == 0) return;
            JSONArray customList = new JSONArray();
            for (int i = 0; i < sectors.length(); i++) {
                if (i < urls.length()) {
                    JSONObject item = new JSONObject();
                    item.put("name", sectors.getString(i));
                    item.put("url", urls.getString(i));
                    customList.put(item);
                }
            }
            createSearchableListView(container, language, "sector", customList, competitionId, age);
        } catch (Exception e) {
            AfterParsingFail("Error creating sector list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a list of top assisters for a specific team.")
    public void CreateTeamAssistsList(HVArrangement container, String teamId, String lang) { 
        calculateAndDisplayTeamStats(container, teamId, lang, "assists"); 
    }

    @SimpleFunction(description = "Creates a view with the detailed header for a specific team, showing its logo and name.")
    public void CreateTeamHeader(HVArrangement container, String teamId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject tInfo = getTeamInfoById(teamId, jsonData.optJSONArray("teams"));
            if (tInfo == null) return;
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            LinearLayout header = createTeamLayout(tInfo, lang);
            header.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            header.setBackground(createCardBackground(CLR_CARD, CLR_AQUA, dpToPx(18)));
            header.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            for (int ci = 0; ci < header.getChildCount(); ci++) {
                View child = header.getChildAt(ci);
                if (child instanceof TextView) ((TextView) child).setTextColor(CLR_AQUA);
            }
            vg.addView(header);
        } catch (Exception e) {
            AfterParsingFail("Error creating team header: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view showing a team's information, such as city and field.")
    public void CreateTeamInfo(HVArrangement container, String teamId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject tInfo = getTeamInfoById(teamId, jsonData.optJSONArray("teams"));
            if (tInfo == null) return;
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            LinearLayout cardLayout = new LinearLayout(context);
            cardLayout.setOrientation(LinearLayout.VERTICAL);
            cardLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            cardLayout.setBackground(createCardBackground(CLR_CARD, this.dividerColor, dpToPx(18)));
            cardLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

            LinearLayout il = new LinearLayout(context);
            il.setOrientation(LinearLayout.VERTICAL);
            addInfoRow(il, getLocalizedText(null, "city", lang), getLocalizedText(tInfo, "city", lang), null, lang);
            addInfoRow(il, getLocalizedText(null, "field", lang), getLocalizedText(tInfo, "field", lang), tInfo.optString("fieldurl"), lang);
            addInfoRow(il, getLocalizedText(null, "information", lang), getLocalizedText(tInfo, "information", lang), null, lang);

            cardLayout.addView(il);
            vg.addView(cardLayout);
        } catch (Exception e) {
            AfterParsingFail("Error creating team info: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates and displays a searchable, grouped list of all teams.")
    public void CreateTeamList(HVArrangement container, final String lang) {
        if (jsonData == null) return;
        try {
            final JSONArray teams = jsonData.optJSONArray("teams");
            final java.util.List<JSONObject> teamList = new java.util.ArrayList<>();
            for (int i = 0; i < teams.length(); i++) teamList.add(teams.getJSONObject(i));
            Collections.sort(teamList, new Comparator<JSONObject>() { @Override public int compare(JSONObject o1, JSONObject o2) { return getLocalizedText(o1, "name", lang).compareTo(getLocalizedText(o2, "name", lang)); } });
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            LinearLayout ml = new LinearLayout(context);
            ml.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            buildTeamListView(content, teamList, lang);
            EditText search = new EditText(context);
            search.setHint(getLocalizedText(null, "search", lang));
            search.setTextColor(CLR_AQUA);
            search.setHintTextColor(CLR_HINT);
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setColor(CLR_CARD);
            searchBg.setCornerRadius(dpToPx(12));
            searchBg.setStroke(dpToPx(1), CLR_AQUA);
            if (Build.VERSION.SDK_INT >= 16) search.setBackground(searchBg); else search.setBackgroundDrawable(searchBg);
            search.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(12));
            search.setLayoutParams(sp);
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try {
                        String filter = s.toString().toLowerCase();
                        java.util.List<JSONObject> filtered = new java.util.ArrayList<>();
                        for (JSONObject team : teamList) {
                            if (getLocalizedText(team, "name", lang).toLowerCase().contains(filter))
                                filtered.add(team);
                        }
                        buildTeamListView(content, filtered, lang);
                    } catch (Exception e) {}
                }
                public void afterTextChanged(Editable s) {}
            });
            ScrollView sv = new ScrollView(context);
            sv.addView(content);
            ml.addView(search);
            ml.addView(sv);
            vg.addView(ml);
        } catch (Exception e) {
            AfterParsingFail("Error creating team list: " + e.getMessage());
        }
    }

        @SimpleFunction(description = "Creates a list of all matches for a specific team.")
    public void CreateTeamMatchList(HVArrangement container, String teamId, String lang) {
    if (jsonData == null) return;
    
    backgroundExecutor.submit(new Runnable() {
        @Override
        public void run() {
            try {
                JSONArray allMatches = jsonData.optJSONArray("matches");
                if (allMatches == null) return;
                
                final JSONArray teams = jsonData.optJSONArray("teams");

                // Step 1: Filter matches for the specific team
                final java.util.List<JSONObject> teamMatchesList = new java.util.ArrayList<>();
                for (int i = 0; i < allMatches.length(); i++) {
                    JSONObject match = allMatches.getJSONObject(i);
                    String homeTeamId = match.optString("home_team_id", "");
                    String awayTeamId = match.optString("away_team_id", "");
                    
                    if (teamId.equals(homeTeamId) || teamId.equals(awayTeamId)) {
                        teamMatchesList.add(match);
                    }
                }

                // Step 2: Sort matches by date and time
                Collections.sort(teamMatchesList, new Comparator<JSONObject>() { 
                    @Override 
                    public int compare(JSONObject o1, JSONObject o2) { 
                        try { 
                            int d = o1.getString("date").compareTo(o2.getString("date")); 
                            if (d != 0) return d; 
                            return o1.optString("time", "").compareTo(o2.optString("time", "")); 
                        } catch (JSONException e) { return 0; } 
                    } 
                });
                
                // Step 3: Group by date and week
                final java.util.Map<String, java.util.Map<String, java.util.List<JSONObject>>> byDateAndWeek = new java.util.LinkedHashMap<>();
                for (JSONObject match : teamMatchesList) {
                    String date = match.getString("date");
                    String week = match.getString("week");
                    if (!byDateAndWeek.containsKey(date)) {
                        byDateAndWeek.put(date, new java.util.LinkedHashMap<String, java.util.List<JSONObject>>());
                    }
                    java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                    if (byWeek == null) continue;
                    if (!byWeek.containsKey(week)) {
                        byWeek.put(week, new java.util.ArrayList<JSONObject>());
                    }
                    java.util.List<JSONObject> weekList = byWeek.get(week);
                    if (weekList != null) weekList.add(match);
                }
                
                // Step 4: Build UI on main thread
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ViewGroup vg = (ViewGroup) container.getView();
                            vg.removeAllViews();
                            
                            final ScrollView sv = new ScrollView(context);
                            final LinearLayout ml = new LinearLayout(context);
                            ml.setOrientation(LinearLayout.VERTICAL);
                            
                            // Store references for scrolling to upcoming match
                            lastCreatedTeamMatchListScrollView = sv;
                            firstUpcomingTeamMatchView = null; // Initialize to null
                            
                            // We do NOT need the 'today' date logic here anymore
                            // because the check inside the view creation handles it.
                            
                            for (String date : byDateAndWeek.keySet()) {
                                java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                                int totalDayMatches = 0;
                                if (byWeek == null) continue;
                                for (java.util.List<JSONObject> weekMatchesList : byWeek.values()) {
                                    totalDayMatches += weekMatchesList.size();
                                }
                                boolean multiWeek = byWeek.size() > 1;
                                String firstWeek = byWeek.keySet().iterator().next();
                                
                                try {
                                    ml.addView(createDateHeaderView(date, totalDayMatches, lang, !multiWeek, firstWeek));
                                } catch (ParseException e) {
                                    // Ignore
                                }
                                
                                // --- REMOVED THE BROKEN DETECTION LOOP HERE ---
                                
                                // Add matches for each week
                                for (String week : byWeek.keySet()) {
                                    java.util.List<JSONObject> weekMatches = byWeek.get(week);
                                    if (weekMatches == null || weekMatches.isEmpty()) continue;
                                    
                                    if (multiWeek) {
                                        ml.addView(createWeekHeaderView(week, lang));
                                    }
                                    
                                    java.util.Map<String, java.util.List<JSONObject>> byGroup = new java.util.LinkedHashMap<>();
                                    for (JSONObject match : weekMatches) {
                                        String group = match.optString("group", "_no_group_");
                                        if (group.isEmpty() || group.equals("null")) {
                                            group = "_no_group_";
                                        }
                                        if (!byGroup.containsKey(group)) {
                                            byGroup.put(group, new java.util.ArrayList<JSONObject>());
                                        }
                                        java.util.List<JSONObject> groupList = byGroup.get(group);
                                        if (groupList != null) groupList.add(match);
                                    }
                                    
                                    for (String gName : byGroup.keySet()) {
                                        if (!gName.equals("_no_group_")) {
                                            ml.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + gName, lang));
                                        }
                                        java.util.List<JSONObject> groupMatches = byGroup.get(gName);
                                        if (groupMatches != null) {
                                            for (JSONObject match : groupMatches) {
                                                try {
                                                    View matchView = createMatchItemView(match, teams, lang);
                                                    ml.addView(matchView);
                                                    
                                                    // Check if this is the first upcoming match
                                                    // This logic is sufficient and works as the list builds
                                                    if (firstUpcomingTeamMatchView == null) {
                                                        String status = match.optString("status", "upcoming").toLowerCase();
                                                        if (!"completed".equals(status)) {
                                                            firstUpcomingTeamMatchView = matchView;
                                                        }
                                                    }
                                                } catch (Exception e) {
                                                    // Ignore individual match errors
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            sv.addView(ml);
                            vg.addView(sv);
                            
                        } catch (Exception e) {
                            AfterParsingFail("Error building team match list UI: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(new Runnable() { 
                    public void run() { 
                        AfterParsingFail("Error creating team match list: " + e.getMessage());
                    }
                });
            }
        }
    });
}

    @SimpleFunction(description = "Creates a view showing a team's players, grouped by position.")
    public void CreateTeamPlayers(HVArrangement container, String teamId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject tInfo = getTeamInfoById(teamId, jsonData.optJSONArray("teams"));
            if (tInfo == null || !tInfo.has("players")) return;
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            ScrollView sv = new ScrollView(context);
            LinearLayout cardLayout = new LinearLayout(context);
            cardLayout.setOrientation(LinearLayout.VERTICAL);
            cardLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            cardLayout.setBackground(createCardBackground(CLR_CARD, this.dividerColor, dpToPx(18)));
            cardLayout.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

            LinearLayout pl = new LinearLayout(context);
            pl.setOrientation(LinearLayout.VERTICAL);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "coach", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "goalkeepers", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "defenders", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "midfielders", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "attackers", lang);

            cardLayout.addView(pl);
            sv.addView(cardLayout);
            vg.addView(sv);
        } catch (Exception e) {
            AfterParsingFail("Error creating player list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a list of top scorers for a specific team.")
    public void CreateTeamScorersList(HVArrangement container, String teamId, String lang) { 
        calculateAndDisplayTeamStats(container, teamId, lang, "goals"); 
    }

    @SimpleFunction(description = "Creates a list of players with the most assists in the tournament.")
    public void CreateTournamentAssistsList(HVArrangement container, String lang) { 
        calculateAndDisplayTournamentStats(container, lang, "assists"); 
    }

    @SimpleFunction(description = "Creates a list of goalkeepers with the most clean sheets in the tournament.")
    public void CreateTournamentCleanSheetsList(HVArrangement container, String lang) { 
        calculateAndDisplayCleanSheets(container, lang); 
    }

    @SimpleFunction(description = "Creates a list of the top goal scorers in the tournament.")
    public void CreateTournamentScorersList(HVArrangement container, String lang) { 
        calculateAndDisplayTournamentStats(container, lang, "goals"); 
    }

    @SimpleFunction(description = "Creates and displays a clickable list of venues (stadiums).")
    public void CreateVenueList(HVArrangement container, final String language) {
        if (jsonData == null) return;
        try {
            final JSONArray venuesArray = jsonData.optJSONArray("venues");
            if (venuesArray == null || venuesArray.length() == 0) return;
            createSearchableListView(container, language, "venue", venuesArray, null, null);
        } catch (Exception e) {
            AfterParsingFail("Error creating venue list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Returns the currently loaded JSON data as a raw string.")
    public String GetJsonDataAsString() {
        return (jsonData != null) ? jsonData.toString() : "{}";
    }

    @SimpleFunction(description = "Returns a list of all unique group names found in the data.")
    public YailList GetGroupList() {
        if (jsonData == null) return YailList.makeEmptyList();
        return YailList.makeList(getJavaGroupList());
    }

    @SimpleFunction(description = "Returns standings data as a list of lists without displaying it.")
    public YailList GetStandingsData(String groupId, String stageId, String lang) {
        if (jsonData == null) return YailList.makeEmptyList();
        try {
            java.util.List<TeamStats> standings = calculateStandingsForGroup(groupId, stageId);
            if (standings == null || standings.isEmpty()) return YailList.makeEmptyList();
            java.util.List<YailList> resultList = new java.util.ArrayList<>();
            JSONArray teams = jsonData.optJSONArray("teams");
            for (TeamStats stats : standings) {
                JSONObject teamInfo = getTeamInfoById(stats.teamId, teams);
                String teamName = getLocalizedText(teamInfo, "name", lang);
                java.util.List<Object> row = new java.util.ArrayList<>();
                row.add(stats.position);
                row.add(stats.teamId);
                row.add(teamName);
                row.add(stats.points);
                row.add(stats.matchesPlayed);
                row.add(stats.wins);
                row.add(stats.draws);
                row.add(stats.losses);
                row.add(stats.goalsFor);
                row.add(stats.goalsAgainst);
                row.add(stats.getGoalDifference());
                row.add(stats.penaltyPoints);
                resultList.add(YailList.makeList(row));
            }
            return YailList.makeList(resultList);
        } catch (Exception e) {
            AfterParsingFail("Error getting standings data: " + e.getMessage());
            return YailList.makeEmptyList();
        }
    }
    
    @SimpleFunction(description = "Returns a list of all unique stage IDs found in the match data.")
    public YailList GetStageList() {
        if (jsonData == null) return YailList.makeEmptyList();
        Set<String> stageSet = new HashSet<>();
        try {
            JSONArray matches = jsonData.optJSONArray("matches");
            if (matches == null) return YailList.makeEmptyList();

            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                String stage = match.optString("stage", null);
                if (stage != null && !stage.isEmpty() && !stage.equals("null")) {
                    stageSet.add(stage);
                }
            }
        } catch (JSONException e) {
            AfterParsingFail("Error getting stage list: " + e.getMessage());
            return YailList.makeEmptyList();
        }
        return YailList.makeList(new ArrayList<>(stageSet));
    }

    @SimpleFunction(description = "Scrolls a previously created match list to the first upcoming match.")
    public void ScrollMatchListToUpcoming() {
        if (lastCreatedMatchListScrollView != null && firstUpcomingMatchView != null) {
            lastCreatedMatchListScrollView.post(new Runnable() {
                @Override
                public void run() {
                    // FIX: Add a second, safer null check inside the runnable
                    if (firstUpcomingMatchView != null) {
                        lastCreatedMatchListScrollView.smoothScrollTo(0, firstUpcomingMatchView.getTop());
                    }
                }
            });
        }
        // NOTE: The confusing "else if" for the team list has been removed from this method
        // to ensure it only handles the main match list.
    }
     
        @SimpleFunction(description = "Scrolls the Team Match List to the first upcoming match.")
    public void ScrollTeamMatchListToUpcoming() {
        if (lastCreatedTeamMatchListScrollView != null && firstUpcomingTeamMatchView != null) {
            lastCreatedTeamMatchListScrollView.post(new Runnable() {
                @Override
                public void run() {
                    // FIX: Add a second, safer null check inside the runnable
                    if (firstUpcomingTeamMatchView != null) {
                        lastCreatedTeamMatchListScrollView.smoothScrollTo(0, firstUpcomingTeamMatchView.getTop());
                    }
                }
            });
        } else {
            // Optional: Notify the user if the list isn't ready or no upcoming match is found.
            // This is good for debugging. You can remove it if you don't want the message.
            // Toast.makeText(context, "No upcoming team match to scroll to.", Toast.LENGTH_SHORT).show();
        }
    }



    @SimpleFunction(description = "Updates the persistent record of the current news count.")
    public void UpdateLastNewsCount() {
        if (jsonData == null || !jsonData.has("news")) return;
        JSONArray newsArray = jsonData.optJSONArray("news");
        if (newsArray == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(LAST_NEWS_COUNT_KEY, newsArray.length());
        editor.apply();
    }

    @SimpleFunction(description = "Checks if the installed app version matches the version in the JSON file.")
    public void CheckAppVersion() {
        if (jsonData == null) {
            AfterParsingFail("JSON data is not loaded.");
            return;
        }

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo = pm.getPackageInfo(context.getPackageName(), 0);
            int installedVersionCode = pInfo.versionCode;
            
            JSONObject appVersionObj = jsonData.optJSONObject("app_version");
            if (appVersionObj == null) return;
            
            int remoteVersionCode = Integer.parseInt(appVersionObj.optString("version_code", "0"));
            String remoteVersionName = appVersionObj.optString("version_name", "Unknown");

            if (remoteVersionCode > installedVersionCode) {
                UpdateRequired(remoteVersionName, String.valueOf(remoteVersionCode));
            } else {
                AppIsUpToDate();
            }
        } catch (Exception e) {
            AfterParsingFail("Error checking app version: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Opens the Google Play Store page for this application.")
    public void OpenGooglePlay() {
        try {
            String appId = context.getPackageName();
            Intent intent = new Intent(Intent.ACTION_VIEW, 
                Uri.parse("https://play.google.com/store/apps/details?id=" + appId));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            AfterParsingFail("Could not open Google Play: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Calculates statistics for a specific team. filterType accepts: 'all', 'home', or 'away'.")
    public void CreateTeamAllStatistics(HVArrangement container, final String teamId, String filterType, final String lang) {
        if (jsonData == null || teamId == null || teamId.isEmpty()) return;

        final String mode = (filterType != null) ? filterType.toLowerCase().trim() : "all";

        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONArray matches = jsonData.optJSONArray("matches");
                    if (matches == null) return;

                    int played = 0;
                    int wins = 0;
                    int draws = 0;
                    int losses = 0;
                    int goalsFor = 0;
                    int goalsAgainst = 0;

                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject match = matches.getJSONObject(i);
                        
                        if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;

                        String homeId = match.getString("home_team_id");
                        String awayId = match.getString("away_team_id");

                        boolean isHomeTeam = teamId.equals(homeId);
                        boolean isAwayTeam = teamId.equals(awayId);

                        if (isHomeTeam && (mode.equals("all") || mode.equals("home"))) {
                            played++;
                            int hScore = match.getInt("home_score");
                            int aScore = match.getInt("away_score");
                            
                            goalsFor += hScore;
                            goalsAgainst += aScore;

                            if (hScore > aScore) wins++;
                            else if (hScore < aScore) losses++;
                            else draws++;
                        } 
                        else if (isAwayTeam && (mode.equals("all") || mode.equals("away"))) {
                            played++;
                            int hScore = match.getInt("home_score");
                            int aScore = match.getInt("away_score");
                            
                            goalsFor += aScore;
                            goalsAgainst += hScore;

                            if (aScore > hScore) wins++;
                            else if (aScore < hScore) losses++;
                            else draws++;
                        }
                    }

                    final int fPlayed = played;
                    final int fWins = wins;
                    final int fDraws = draws;
                    final int fLosses = losses;
                    final int fGF = goalsFor;
                    final int fGA = goalsAgainst;

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ViewGroup vg = (ViewGroup) container.getView();
                                vg.removeAllViews();
                                ScrollView sv = new ScrollView(context);
                                LinearLayout mainLayout = new LinearLayout(context);
                                mainLayout.setOrientation(LinearLayout.VERTICAL);
                                mainLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

                                if (fPlayed == 0) {
                                    mainLayout.addView(createSingleStatCard(getStatLocalizedText("no_completed_matches", lang), "-", lang));
                                    sv.addView(mainLayout);
                                    vg.addView(sv);
                                    return;
                                }

                                boolean isRTL = "ar".equalsIgnoreCase(lang);

                                // ── Results section ──────────────────────────────────────
                                mainLayout.addView(createTeamStatSectionHeader(isRTL ? "نتائج المباريات" : "Match Results"));

                                TextView totalLabel = new TextView(context);
                                totalLabel.setText((isRTL ? "إجمالي المباريات: " : "Total Games: ") + fPlayed);
                                totalLabel.setTextSize(16);
                                totalLabel.setTextColor(CLR_AQUA);
                                totalLabel.setTypeface(null, Typeface.BOLD);
                                totalLabel.setGravity(Gravity.CENTER);
                                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
                                tlp.setMargins(0, dpToPx(4), 0, dpToPx(4));
                                totalLabel.setLayoutParams(tlp);
                                mainLayout.addView(totalLabel);

                                mainLayout.addView(buildPieChartView(fWins, fDraws, fLosses, fPlayed));
                                mainLayout.addView(buildPieLegendRow(fWins, fDraws, fLosses, fPlayed, lang));

                                // ── Goals section ─────────────────────────────────────────
                                View goalsSpacer = new View(context);
                                goalsSpacer.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(24)));
                                mainLayout.addView(goalsSpacer);

                                mainLayout.addView(createTeamStatSectionHeader(isRTL ? "الأهداف" : "Goals"));
                                mainLayout.addView(buildGoalsBarChart(fGF, fGA, fPlayed, lang));

                                sv.addView(mainLayout);
                                vg.addView(sv);

                            } catch (Exception e) {
                                AfterParsingFail("Error building team stats UI: " + e.getMessage());
                            }
                        }
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error calculating team stats: " + e.getMessage()); }});
                }
            }
        });
    }
    
    @SimpleFunction(description = "Scrolls all standings tables inside the container to the far right. Useful for RTL languages.")
    public void ScrollStandingsToRight(HVArrangement container) {
        if (container == null) return;
        View view = container.getView();
        
        final java.util.List<HorizontalScrollView> allScrollViews = new java.util.ArrayList<>();
        
        findAllHorizontalScrollViews(view, allScrollViews);
        
        for (final HorizontalScrollView hsv : allScrollViews) {
            hsv.post(new Runnable() {
                @Override
                public void run() {
                    hsv.fullScroll(View.FOCUS_RIGHT);
                }
            });
        }
    }
    
    // --- Deprecated Blocks ---
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateDrawMatchesView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateGoalRateView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateMatchesWithWinnerView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateStrongestAttackView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateStrongestDefenseView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateTotalGoalsScoredView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateTotalMatchesPlayedView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateWeakestAttackView(HVArrangement c, String l) {}
    @Deprecated @SimpleFunction(description = "This block is deprecated.") public void CreateWeakestDefenseView(HVArrangement c, String l) {}

    // ==================== PRIVATE HELPER METHODS ====================
    
    private int dpToPx(int dp) {
        return (int)(dp * context.getResources().getDisplayMetrics().density);
    }

    private View createDivider() {
        View d = new View(context);
        d.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(1)));
        d.setBackgroundColor(this.dividerColor);
        return d;
    }

        

    // For list views - uses optimized fields
private JSONObject findMatchByIdOptimized(String mId) throws JSONException {
    JSONArray matches = jsonData.optJSONArray("matches");
    if (matches == null) return null;
    for (int i = 0; i < matches.length(); i++) {
        JSONObject match = matches.getJSONObject(i);
        if (mId.equals(match.optString("match_id"))) {
            if (useCache) {
                JSONObject optimizedMatch = new JSONObject();
                for (String field : MATCH_FIELDS) {
                    if (match.has(field)) {
                        optimizedMatch.put(field, match.get(field));
                    }
                }
                return optimizedMatch;
            }
            return match;
        }
    }
    return null;
}

// For detail views - returns complete match object
private JSONObject findMatchById(String mId) throws JSONException {
    JSONArray matches = jsonData.optJSONArray("matches");
    if (matches == null) return null;
    for (int i = 0; i < matches.length(); i++) {
        JSONObject match = matches.getJSONObject(i);
        if (mId.equals(match.optString("match_id"))) {
            return match; // Return FULL match object
        }
    }
    return null;
}


/**
 * Extracts only the essential fields from a JSON object for better performance.
 */
private JSONObject extractEssentialFields(JSONObject source, Set<String> fields) {
    if (source == null || fields == null) return source;
    try {
        JSONObject result = new JSONObject();
        for (String field : fields) {
            if (source.has(field) && !source.isNull(field)) {
                result.put(field, source.get(field));
            }
        }
        return result;
    } catch (JSONException e) {
        return source; // Return original on error
    }
}

    private JSONArray getLocalizedArray(JSONObject source, String key, String lang) {
        if (source == null || !source.has(key) || source.isNull(key)) return null;
        try {
            Object data = source.get(key);
            if (data instanceof JSONArray) return (JSONArray) data;
            if (data instanceof JSONObject) {
                JSONObject lObj = (JSONObject) data;
                if (lObj.has(lang) && !lObj.isNull(lang)) return lObj.getJSONArray(lang);
                if (lObj.has("en") && !lObj.isNull("en")) return lObj.getJSONArray("en");
                if (lObj.has("ar") && !lObj.isNull("ar")) return lObj.getJSONArray("ar");
            }
        } catch (JSONException e) {
            return null;
        }
        return null;
    }
// Parse scorers from match for stats popup
private void parseScorersFromMatchForStats(JSONObject match, JSONArray scorers, boolean isHome, 
                                   String teamName, String teamId, String playerName, 
                                   List<MatchStatInfo> playerStats, String lang) throws JSONException {
    String matchId = match.getString("match_id");
    String matchDate = match.getString("date");
    
    // Get opponent team
    String opponentTeamId = isHome ? match.getString("away_team_id") : match.getString("home_team_id");
    JSONArray teams = jsonData.optJSONArray("teams");
    JSONObject opponentTeamObj = teams != null ? getTeamInfoById(opponentTeamId, teams) : null;
    String opponentTeamName = getLocalizedText(opponentTeamObj, "name", lang);
    
    // Always store score as "Home : Away"
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    String score = isRTL ? (match.getInt("away_score") + " : " + match.getInt("home_score")) : (match.getInt("home_score") + " : " + match.getInt("away_score"));
    
    boolean isParsingAssists = false;
    int goalCount = 0;
    
    for (int i = 0; i < scorers.length(); i++) {
        String eventString = scorers.getString(i);
        
        // Skip assist section
        if (eventString.equals(ASSISTS_DELIMITER_AR) || eventString.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) {
            isParsingAssists = true;
            continue;
        }
        
        if (isParsingAssists) continue;
        
        String[] parsed = parsePlayerEventString(eventString);
        String scorerName = parsed[0];
        
        if (scorerName.equals(playerName)) {
            goalCount += Integer.parseInt(parsed[1]);
        }
    }
    
    if (goalCount > 0) {
        playerStats.add(new MatchStatInfo(matchId, matchDate, opponentTeamName, score, goalCount, isHome));
    }
}
    
// Create the full-screen image dialog
private void showFullScreenImageDialog(String imageUrl) {
    final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    
    // Create the ImageView first
    final ImageView fullScreenImage = new ImageView(context);
    fullScreenImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
    
    // Add on dismiss cleanup with the final variable
    dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
    @Override
    public void onDismiss(android.content.DialogInterface dialogInterface) {
        fullScreenImage.setImageDrawable(null);
    }
});
    
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    
    RelativeLayout layout = new RelativeLayout(context);
    layout.setBackgroundColor(this.headerBackgroundColor);
    
    
    final ImageView finalFullScreenImage = fullScreenImage; 
    
    fullScreenImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
    RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    );
    fullScreenImage.setLayoutParams(imageParams);
    
    // Progress bar
    ProgressBar progressBar = new ProgressBar(context);
    progressBar.setIndeterminate(true);
    RelativeLayout.LayoutParams progressParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    progressParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    progressBar.setLayoutParams(progressParams);
    
    // Close button
    TextView closeButton = new TextView(context);
    closeButton.setText("✕");
    closeButton.setTextColor(this.primaryTextColor);
    closeButton.setTextSize(24);
    closeButton.setTypeface(null, Typeface.BOLD);
    closeButton.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
    RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    closeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    closeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    closeButton.setLayoutParams(closeParams);
    
    // Bottom bar
    LinearLayout bottomBar = new LinearLayout(context);
    bottomBar.setOrientation(LinearLayout.HORIZONTAL);
    bottomBar.setGravity(Gravity.CENTER);
    bottomBar.setBackgroundColor(this.overlayBackgroundColor);
    bottomBar.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
    RelativeLayout.LayoutParams bottomParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    bottomParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    bottomBar.setLayoutParams(bottomParams);
    
    // Share button
    Button shareBtn = new Button(context);
    shareBtn.setText("📤 Share");
    shareBtn.setTextColor(CLR_AQUA);
    GradientDrawable shareBtnBg = new GradientDrawable();
    shareBtnBg.setColor(CLR_CARD);
    shareBtnBg.setCornerRadius(dpToPx(8));
    shareBtnBg.setStroke(dpToPx(1), CLR_AQUA);
    if (Build.VERSION.SDK_INT >= 16) shareBtn.setBackground(shareBtnBg); else shareBtn.setBackgroundDrawable(shareBtnBg);
    shareBtn.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
    shareBtn.setAllCaps(false);
    LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    shareParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);
    shareBtn.setLayoutParams(shareParams);
    
    // Download button
    Button downloadBtn = new Button(context);
    downloadBtn.setText("💾 Download");
    downloadBtn.setTextColor(this.primaryTextColor);
    downloadBtn.setBackgroundColor(this.cardBackgroundColor);
    downloadBtn.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
    downloadBtn.setAllCaps(false);
    LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    downloadParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);
    downloadBtn.setLayoutParams(downloadParams);
    
    bottomBar.addView(shareBtn);
    bottomBar.addView(downloadBtn);
    
    layout.addView(fullScreenImage);
    layout.addView(progressBar);
    layout.addView(closeButton);
    layout.addView(bottomBar);
    
    dialog.setContentView(layout);
    
    // Load image
    progressBar.setVisibility(View.VISIBLE);
    Picasso.with(context).load(imageUrl).into(fullScreenImage, new com.squareup.picasso.Callback() {
        @Override
        public void onSuccess() {
            progressBar.setVisibility(View.GONE);
        }
        
        @Override
        public void onError() {
            progressBar.setVisibility(View.GONE);
            fullScreenImage.setImageResource(android.R.drawable.ic_menu_gallery);
            Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    });
    
    // Close button click
    closeButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            dialog.dismiss();
        }
    });
    
    shareBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Use MediaStore method (no FileProvider needed!)
            shareImageWithMediaStore(imageUrl, dialog);
        }
    });
    
    // Download button click
    downloadBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            DownloadImage(imageUrl);
        }
    });
    
    // Click outside to close
    fullScreenImage.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            dialog.dismiss();
        }
    });
    
    dialog.show();
}

// Complete working share method - Uses MediaStore (works on all Android versions)
private void shareImageWithMediaStore(String imageUrl, Dialog dialog) {
    // Show progress
    final ProgressDialog progressDialog = new ProgressDialog(context);
    progressDialog.setMessage("Preparing image for sharing...");
    progressDialog.setCancelable(false);
    
    activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
            progressDialog.show();
        }
    });
    
    backgroundExecutor.submit(new Runnable() {
        @Override
        public void run() {
            try {
                // 1. Download the image
                URL url = new URL(imageUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();
                
                // Check if connection is successful
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Failed to download: " + connection.getResponseCode());
                }
                
                InputStream inputStream = connection.getInputStream();
                
                // 2. Save to MediaStore (Android's built-in content provider)
                ContentValues contentValues = new ContentValues();
                String fileName = "football_" + System.currentTimeMillis() + ".jpg";
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                
                // For Android 10+ add relative path
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FootballApp");
                }
                
                ContentResolver resolver = context.getContentResolver();
                Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                
                if (imageUri == null) {
                    throw new Exception("Failed to create image in MediaStore");
                }
                
                // 3. Write image data
                OutputStream outputStream = resolver.openOutputStream(imageUri);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();
                connection.disconnect();
                
                final Uri finalImageUri = imageUri;
                
                // 4. Share on UI thread
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            progressDialog.dismiss();
                            
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType("image/jpeg");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, finalImageUri);
                            
                            // Add grant permission for Android 10+
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            
                            // Create chooser
                            Intent chooser = Intent.createChooser(shareIntent, "Share Image");
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(chooser);
                            
                            // Fire event
                            ImageShared(imageUrl);
                            
                            // Dismiss full screen dialog
                            if (dialog != null && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                            
                            // Show success message
                            Toast.makeText(context, "Image ready for sharing!", Toast.LENGTH_SHORT).show();
                            
                        } catch (Exception e) {
                            progressDialog.dismiss();
                            e.printStackTrace();
                            Toast.makeText(context, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            // Fallback to link sharing
                            shareImageSimple(imageUrl);
                            if (dialog != null && dialog.isShowing()) {
                                dialog.dismiss();
                            }
                        }
                    }
                });
                
            } catch (final Exception e) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        e.printStackTrace();
                        Toast.makeText(context, "Failed to prepare image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // Fallback to link sharing
                        shareImageSimple(imageUrl);
                        if (dialog != null && dialog.isShowing()) {
                            dialog.dismiss();
                        }
                    }
                });
            }
        }
    });
}



// Keep this as fallback
private void shareImageSimple(String imageUrl) {
    try {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "📸 Check out this football image: " + imageUrl);
        
        Intent chooser = Intent.createChooser(shareIntent, "Share Image Link");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
        
        ImageShared(imageUrl);
        
    } catch (Exception e) {
        Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show();
    }
}

// Download image to device with permission check
@SimpleFunction(description = "Downloads an image to the Pictures folder.")
public void DownloadImage(String imageUrl) {
    if (imageUrl == null || imageUrl.isEmpty()) {
        AfterParsingFail("Image URL is empty");
        return;
    }
    
    new Thread(new Runnable() {
        @Override
        public void run() {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            
            try {
                URL url = new URL(imageUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();
                
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    final String errorMsg = "Failed to download: HTTP " + connection.getResponseCode();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            AfterParsingFail(errorMsg);
                        }
                    });
                    return;
                }
                
                input = connection.getInputStream();
                
                ContentValues contentValues = new ContentValues();
                final String fileName = "football_" + System.currentTimeMillis() + ".jpg";
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Save to Pictures/FootballApp folder
                    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FootballApp");
                } else {
                    String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
                    File footballDir = new File(path, "FootballApp");
                    if (!footballDir.exists()) {
                        footballDir.mkdirs();
                    }
                    contentValues.put(MediaStore.Images.Media.DATA, footballDir.getAbsolutePath() + "/" + fileName);
                }
                
                ContentResolver resolver = context.getContentResolver();
                final Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                
                if (imageUri == null) {
                    throw new Exception("Failed to create file");
                }
                
                output = resolver.openOutputStream(imageUri);
                
                if (output == null) {
                    throw new Exception("Failed to open output stream");
                }
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                final long finalFileSize = totalBytes;
                
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String successMessage = "Image saved to Pictures/FootballApp/";
                        Toast.makeText(context, successMessage + fileName + " (" + (finalFileSize / 1024) + " KB)", Toast.LENGTH_LONG).show();
                        ImageDownloadComplete(imageUri.toString());
                        AfterParsingSuccess();
                    }
                });
                
            } catch (final Exception e) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AfterParsingFail("Download failed: " + e.getMessage());
                    }
                });
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        }
    }).start();
}

// Keep the old private method for internal use (renamed)
private void downloadImageInternal(String imageUrl) {
    // This is now handled by the public method above
    DownloadImage(imageUrl);
}


    // Show player goals popup
    private void showPlayerGoalsPopup(String playerName, String teamId, String teamName, String lang) {
    final Dialog dialog = new Dialog(context);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    // We'll create programmatically
    
    // Create layout programmatically
    LinearLayout mainLayout = new LinearLayout(context);
    mainLayout.setOrientation(LinearLayout.VERTICAL);
    mainLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
    GradientDrawable goalDialogBg = new GradientDrawable();
    goalDialogBg.setColor(CLR_DIALOG);
    goalDialogBg.setCornerRadius(dpToPx(16));
    goalDialogBg.setStroke(dpToPx(1), CLR_AQUA);
    if (Build.VERSION.SDK_INT >= 16) mainLayout.setBackground(goalDialogBg); else mainLayout.setBackgroundDrawable(goalDialogBg);

    // Title
    TextView title = new TextView(context);
    title.setText("⚽ " + playerName);
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    title.setGravity(Gravity.CENTER);
    title.setPadding(0, 0, 0, dpToPx(4));
    title.setTextColor(CLR_AQUA);
    mainLayout.addView(title);

    // Team info
    TextView teamInfo = new TextView(context);
    teamInfo.setText(getLocalizedText(null, "team", lang) + ": " + teamName);
    teamInfo.setTextSize(13);
    teamInfo.setGravity(Gravity.CENTER);
    teamInfo.setPadding(0, 0, 0, dpToPx(16));
    teamInfo.setTextColor(CLR_TEAL);
    mainLayout.addView(teamInfo);
    
    // Divider
    View divider = new View(context);
    divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(1)));
    divider.setBackgroundColor(this.dividerColor);
    mainLayout.addView(divider);
    
    // ScrollView for matches list (capped at 55% screen height)
    ScrollView scrollView = new ScrollView(context);
    int goalsScrollMaxH = (int)(context.getResources().getDisplayMetrics().heightPixels * 0.55);
    scrollView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, goalsScrollMaxH));
    LinearLayout matchesContainer = new LinearLayout(context);
    matchesContainer.setOrientation(LinearLayout.VERTICAL);
    matchesContainer.setPadding(0, dpToPx(8), 0, dpToPx(8));

    // Find all matches where this player scored
    try {
        JSONArray matches = jsonData.optJSONArray("matches");
        JSONArray teams = jsonData.optJSONArray("teams");
        if (matches == null || teams == null) return;

        // Get team info for opponent names
        JSONObject teamInfoObj = getTeamInfoById(teamId, teams);
        String teamNameLocal = getLocalizedText(teamInfoObj, "name", lang);
        
        // Collect all goals by this player
        List<MatchGoalInfo> playerGoals = new ArrayList<>();
        
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.getJSONObject(i);
            if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
            
            // Check home team scorers
JSONArray homeScorers = getLocalizedArray(match, "home_scorers", lang);
if (homeScorers != null) {
    parseScorersFromMatch(match, homeScorers, true, teamNameLocal, teamId, playerName, playerGoals, lang);
}

// Check away team scorers
JSONArray awayScorers = getLocalizedArray(match, "away_scorers", lang);
if (awayScorers != null) {
    parseScorersFromMatch(match, awayScorers, false, teamNameLocal, teamId, playerName, playerGoals, lang);
}
        }
        
        // Sort by date (newest first)
        Collections.sort(playerGoals, new Comparator<MatchGoalInfo>() {
            @Override
            public int compare(MatchGoalInfo o1, MatchGoalInfo o2) {
                return o2.matchDate.compareTo(o1.matchDate);
            }
        });
        
        if (playerGoals.isEmpty()) {
            TextView noGoals = new TextView(context);
            noGoals.setText(getLocalizedText(null, "no_goals_found", lang));
            noGoals.setTextSize(14);
            noGoals.setGravity(Gravity.CENTER);
            noGoals.setTextColor(this.secondaryTextColor);
            noGoals.setPadding(0, dpToPx(20), 0, dpToPx(20));
            matchesContainer.addView(noGoals);
        } else {
            int totalGoals = 0;
                for (MatchGoalInfo goal : playerGoals) {
                totalGoals += goal.goalCount;
                matchesContainer.addView(createMatchGoalCard(goal, lang, dialog));
            }
            
            // Add total goals footer
            View footerDivider = new View(context);
            footerDivider.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(1)));
            footerDivider.setBackgroundColor(this.dividerColor);
            matchesContainer.addView(footerDivider);
            
            TextView totalGoalsView = new TextView(context);
            totalGoalsView.setText(getLocalizedText(null, "total_goals", lang) + ": " + totalGoals);
            totalGoalsView.setTextSize(14);
            totalGoalsView.setTypeface(null, Typeface.BOLD);
            totalGoalsView.setGravity(Gravity.CENTER);
            totalGoalsView.setPadding(0, dpToPx(16), 0, dpToPx(8));
            totalGoalsView.setTextColor(CLR_AQUA);
            matchesContainer.addView(totalGoalsView);
        }

        scrollView.addView(matchesContainer);
        mainLayout.addView(scrollView);

        // Close button
        Button closeButton = new Button(context);
        closeButton.setText(getLocalizedText(null, "close", lang));
        closeButton.setTextColor(CLR_CARD);
        GradientDrawable goalCloseBg = new GradientDrawable();
        goalCloseBg.setColor(CLR_AQUA);
        goalCloseBg.setCornerRadius(dpToPx(8));
        if (Build.VERSION.SDK_INT >= 16) closeButton.setBackground(goalCloseBg); else closeButton.setBackgroundDrawable(goalCloseBg);
        closeButton.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
        closeButton.setAllCaps(false);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.setMargins(0, dpToPx(16), 0, 0);
        closeButton.setLayoutParams(buttonParams);
        mainLayout.addView(closeButton);
        
        dialog.setContentView(mainLayout);
        
        // Set dialog width and height
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout((int)(context.getResources().getDisplayMetrics().widthPixels * 0.9), -2);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        
        dialog.show();
        
    } catch (Exception e) {
        AfterParsingFail("Error loading player goals: " + e.getMessage());
        Toast.makeText(context, "Error loading player data", Toast.LENGTH_SHORT).show();
    }
}

// Helper class for match goal info
private class MatchGoalInfo {
    String matchId;
    String matchDate;
    String opponentTeam;
    String score;
    int goalCount;
    boolean isHome;
    
    MatchGoalInfo(String matchId, String matchDate, String opponentTeam, String score, int goalCount, boolean isHome) {
        this.matchId = matchId;
        this.matchDate = matchDate;
        this.opponentTeam = opponentTeam;
        this.score = score;
        this.goalCount = goalCount;
        this.isHome = isHome;
    }
}

// Parse scorers from match
private void parseScorersFromMatch(JSONObject match, JSONArray scorers, boolean isHome, 
                                   String teamName, String teamId, String playerName, 
                                   List<MatchGoalInfo> playerGoals, String lang) throws JSONException {
    String matchId = match.getString("match_id");
    String matchDate = match.getString("date");
    
    // Get opponent team
    String opponentTeamId = isHome ? match.getString("away_team_id") : match.getString("home_team_id");
    JSONArray teams = jsonData.optJSONArray("teams");
    JSONObject opponentTeamObj = teams != null ? getTeamInfoById(opponentTeamId, teams) : null;
    String opponentTeamName = getLocalizedText(opponentTeamObj, "name", lang);
    
    // Always store score as "Home : Away"
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    String score = isRTL ? (match.getInt("away_score") + " : " + match.getInt("home_score")) : (match.getInt("home_score") + " : " + match.getInt("away_score"));
    
    boolean isParsingAssists = false;
    int goalCount = 0;
    
    for (int i = 0; i < scorers.length(); i++) {
        String eventString = scorers.getString(i);
        
        // Skip assist section
        if (eventString.equals(ASSISTS_DELIMITER_AR) || eventString.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) {
            isParsingAssists = true;
            continue;
        }
        
        if (isParsingAssists) continue;
        
        String[] parsed = parsePlayerEventString(eventString);
        String scorerName = parsed[0];
        
        if (scorerName.equals(playerName)) {
            goalCount += Integer.parseInt(parsed[1]);
        }
    }
    
    if (goalCount > 0) {
        playerGoals.add(new MatchGoalInfo(matchId, matchDate, opponentTeamName, score, goalCount, isHome));
    }
}

// Create match goal card view
private View createMatchGoalCard(MatchGoalInfo goal, String lang, final Dialog parentDialog) {
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams goalCardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    goalCardParams.setMargins(0, dpToPx(4), 0, dpToPx(4));
    card.setLayoutParams(goalCardParams);
    card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
    GradientDrawable goalCardBg = new GradientDrawable();
    goalCardBg.setColor(CLR_CARD);
    goalCardBg.setCornerRadius(dpToPx(12));
    goalCardBg.setStroke(dpToPx(1), CLR_BORDER);
    if (Build.VERSION.SDK_INT >= 16) card.setBackground(goalCardBg); else card.setBackgroundDrawable(goalCardBg);
    if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(3));
    
    // Date and opponent row
    LinearLayout topRow = new LinearLayout(context);
    topRow.setOrientation(LinearLayout.HORIZONTAL);
    topRow.setGravity(Gravity.CENTER_VERTICAL);
    
    TextView dateView = new TextView(context);
    dateView.setText(formatDate(goal.matchDate, lang));
    dateView.setTextSize(12);
    dateView.setTextColor(CLR_TEAL);
    dateView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

    TextView opponentView = new TextView(context);
    String vsText = getLocalizedText(null, "vs", lang);
    opponentView.setText(vsText + " " + goal.opponentTeam);
    opponentView.setTextSize(12);
    opponentView.setTextColor(CLR_TEAL);
    opponentView.setGravity(isRTL ? Gravity.START : Gravity.END);
    opponentView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    
    if (isRTL) {
        topRow.addView(opponentView);
        topRow.addView(dateView);
    } else {
        topRow.addView(dateView);
        topRow.addView(opponentView);
    }
    
    // Score and goals row
    LinearLayout bottomRow = new LinearLayout(context);
    bottomRow.setOrientation(LinearLayout.HORIZONTAL);
    bottomRow.setGravity(Gravity.CENTER_VERTICAL);
    bottomRow.setPadding(0, dpToPx(8), 0, 0);
    
    TextView scoreView = new TextView(context);
    scoreView.setText(goal.score);
    scoreView.setTextSize(16);
    scoreView.setTypeface(null, Typeface.BOLD);
    scoreView.setTextColor(this.primaryTextColor);
    scoreView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    
    String goalsText = "⚽ " + goal.goalCount + " " + getLocalizedText(null, "goals", lang);
    TextView goalsView = new TextView(context);
    goalsView.setText(goalsText);
    goalsView.setTextSize(14);
    goalsView.setTextColor(CLR_AQUA);
    goalsView.setTypeface(null, Typeface.BOLD);
    goalsView.setGravity(isRTL ? Gravity.START : Gravity.END);
    goalsView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    
    if (isRTL) {
        bottomRow.addView(goalsView);
        bottomRow.addView(scoreView);
    } else {
        bottomRow.addView(scoreView);
        bottomRow.addView(goalsView);
    }
    
    card.addView(topRow);
    card.addView(bottomRow);
    
    /// Make card clickable to show match details AND dismiss the popup dialog
    final String matchId = goal.matchId;
    card.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Dismiss the parent dialog first
            if (parentDialog != null && parentDialog.isShowing()) {
                parentDialog.dismiss();
            }
            // Then fire the match clicked event
            MatchClicked(matchId);
        }
    });
    
    return card;
}

// Format date for display
private String formatDate(String dateStr, String lang) {
    if (dateStr == null || dateStr.isEmpty()) return "";
    try {
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Date date = inputFormat.parse(dateStr);
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", isRTL ? new Locale("ar") : Locale.US);
        return outputFormat.format(date); // ← ADD THIS LINE
    } catch (ParseException e) {
        Log.e("FootballSuite", "Date parse error: " + dateStr, e);
        return dateStr;
    }
}

    private void createTwoColumnDetailView(HVArrangement c, String mId, String lang, String titleKey, String homeKey, String awayKey) {
        if (jsonData == null) return;
        try {
            ViewGroup vg = (ViewGroup) c.getView();
            vg.removeAllViews();
            JSONObject match = findMatchById(mId);
            if (match == null) return;
            JSONArray homeData = getLocalizedArray(match, homeKey, lang);
            JSONArray awayData = getLocalizedArray(match, awayKey, lang);
            if ((homeData == null || homeData.length() == 0) && (awayData == null || awayData.length() == 0)) return;
            boolean isRTL = "ar".equalsIgnoreCase(lang);
            LinearLayout ml = new LinearLayout(context);
            LinearLayout.LayoutParams mlParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16));
            ml.setLayoutParams(mlParams);
            ml.setOrientation(LinearLayout.VERTICAL);
            ml.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            GradientDrawable mlBg = createCardBackground(CLR_CARD, this.dividerColor, dpToPx(18));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ml.setBackground(mlBg);
            else ml.setBackgroundDrawable(mlBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ml.setElevation(dpToPx(6));
            if (!"scorers_list".equals(titleKey)) {
                TextView titleView = createTextView(getLocalizedText(null, titleKey, lang), -1, 0, true);
                titleView.setTextColor(CLR_AQUA);
                titleView.setPadding(0, 0, 0, dpToPx(12));
                ml.addView(titleView);
            }
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
            LinearLayout homeCol = new LinearLayout(context);
            homeCol.setOrientation(LinearLayout.VERTICAL);
            homeCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            homeCol.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout awayCol = new LinearLayout(context);
            awayCol.setOrientation(LinearLayout.VERTICAL);
            awayCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            awayCol.setGravity(Gravity.CENTER_HORIZONTAL);
            JSONObject homeTInfo = getTeamInfoById(match.getString("home_team_id"), jsonData.optJSONArray("teams"));
            TextView homeHeader = createTextView(getLocalizedText(homeTInfo, "name", lang), -1, 0, true);
            homeHeader.setTextColor(CLR_AQUA);
            homeHeader.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(16));
            homeCol.addView(homeHeader);
            JSONObject awayTInfo = getTeamInfoById(match.getString("away_team_id"), jsonData.optJSONArray("teams"));
            TextView awayHeader = createTextView(getLocalizedText(awayTInfo, "name", lang), -1, 0, true);
            awayHeader.setTextColor(CLR_AQUA);
            awayHeader.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(16));
            awayCol.addView(awayHeader);
            populateDetailColumn(homeCol, homeData, lang, titleKey);
            populateDetailColumn(awayCol, awayData, lang, titleKey);
            View separator = new View(context);
            separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(1), -1));
            separator.setBackgroundColor(this.dividerColor);
            if (isRTL) {
                columns.addView(awayCol);
                columns.addView(separator);
                columns.addView(homeCol);
            } else {
                columns.addView(homeCol);
                columns.addView(separator);
                columns.addView(awayCol);
            }
            ml.addView(columns);
            vg.addView(ml);
        } catch (Exception e) {
            AfterParsingFail("Error creating detail view: " + e.getMessage());
        }
    }

    private void populateDetailColumn(LinearLayout col, JSONArray data, String lang, String titleKey) throws JSONException {
        if (data == null || data.length() == 0) return;
        boolean isScorersList = "scorers_list".equals(titleKey);
        String assistsDelimiterAr = ASSISTS_DELIMITER_AR;
        String assistsDelimiterEn = ASSISTS_DELIMITER_EN;
        boolean hasGoals = false;
        if (isScorersList) {
            for (int i = 0; i < data.length(); i++) {
                String item = data.getString(i);
                if (item.equals(assistsDelimiterAr) || item.equals(assistsDelimiterEn)) break;
                if (!item.isEmpty()) {
                    hasGoals = true;
                    break;
                }
            }
            if (hasGoals) {
                col.addView(createEventHeader(getLocalizedText(null, "goals", lang), "soccer_ball.png", lang), col.getChildCount());
            }
        }
        for (int i = 0; i < data.length(); i++) {
            String item = data.getString(i);
            if (item.isEmpty()) {
                View spacer = new View(context);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 16));
                col.addView(spacer);
            } else if (isScorersList && (item.equals(assistsDelimiterAr) || item.equals(assistsDelimiterEn))) {
                col.addView(createEventHeader(getLocalizedText(null, "assists", lang), "goal_icon.png", lang));
            } else {
                boolean isBold = item.equalsIgnoreCase(SUBSTITUTES_AR) || item.equalsIgnoreCase(SUBSTITUTES_EN);
                TextView tv = createTextView(item, -2, 0, isBold);
                tv.setTextColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                col.addView(tv);
            }
        }
    }

        

        private void calculateAndDisplayTournamentStats(HVArrangement c, String lang, final String statType) {
        if (jsonData == null) return;
        
        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Process all matches to get ALL players (like 'Calculate Standings' pre-calc)
                    java.util.Map<String, PlayerStat> allPlayerStats = new java.util.HashMap<>();
                    JSONArray matches = jsonData.optJSONArray("matches");
                    if (matches == null) return;
                    JSONArray teams = jsonData.optJSONArray("teams");
                    
                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject match = matches.getJSONObject(i);
                        if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                        
                        JSONObject homeTInfo = getTeamInfoById(match.getString("home_team_id"), teams);
                        JSONObject awayTInfo = getTeamInfoById(match.getString("away_team_id"), teams);
                        
                        processTeamEvents(allPlayerStats, match, "home_scorers", homeTInfo, lang);
                        processTeamEvents(allPlayerStats, match, "away_scorers", awayTInfo, lang);
                    }

                    // 2. Identify valid groups from the TEAMS data
                    Set<String> uniqueGroupNames = new HashSet<>();
                    for (int i = 0; i < teams.length(); i++) {
                        String group = teams.getJSONObject(i).optString("group", "");
                        
                        // Clean up the string
                        if (group == null) group = "";
                        group = group.trim();
                        
                        // FILTER LOGIC:
                        // 1. Cannot be empty
                        // 2. Cannot be the word "null", "none", or "others"
                        // 3. REMOVED the check for pure numbers (\d+) to allow groups like "1", "2", "1-2-3"
                        
                        boolean isValid = !group.isEmpty() && 
                                          !group.equalsIgnoreCase("null") && 
                                          !group.equalsIgnoreCase("others") &&
                                          !group.equalsIgnoreCase("none");
                        
                        if (isValid) {
                            uniqueGroupNames.add(group);
                        }
                    }
                    
                    final List<String> groups = new ArrayList<>(uniqueGroupNames);
                    Collections.sort(groups); // Sort A, B, C...

                    // 3. Prepare the data map: Group Name -> List of Players
                    // (This acts as our 'calculated data' ready to be displayed)
                    final Map<String, List<PlayerStat>> dataByGroup = new LinkedHashMap<>();
                    
                    // Add valid groups
                    for (String g : groups) {
                        dataByGroup.put(g, new ArrayList<PlayerStat>());
                    }
                    // Add a catch-all for invalid groups
                    dataByGroup.put("_invalid_", new ArrayList<PlayerStat>());

                    // Distribute players into these buckets
                    for (PlayerStat stat : allPlayerStats.values()) {
                        JSONObject tObj = getTeamInfoById(stat.teamId, teams);
                        String pGroup = (tObj != null) ? tObj.optString("group", "") : "";
                        
                        boolean foundGroup = false;
                        if (pGroup != null && !pGroup.trim().isEmpty() && dataByGroup.containsKey(pGroup)) {
                            dataByGroup.get(pGroup).add(stat);
                            foundGroup = true;
                        }
                        
                        if (!foundGroup) {
                            dataByGroup.get("_invalid_").add(stat);
                        }
                    }
                    
                    // Sort players inside each group and limit to 20
                    for (List<PlayerStat> list : dataByGroup.values()) {
                        Collections.sort(list, new Comparator<PlayerStat>() { 
                            @Override public int compare(PlayerStat o1, PlayerStat o2) {
                                int s1 = "goals".equals(statType) ? o1.goals : o1.assists;
                                int s2 = "goals".equals(statType) ? o2.goals : o2.assists;
                                return Integer.valueOf(s2).compareTo(s1);
                            }
                        });
                        if (list.size() > 20) {
                            // Keep only top 20 (create new list to avoid UnsupportedOperationException)
                            List<PlayerStat> top20 = new ArrayList<>(list.subList(0, 20));
                            list.clear();
                            list.addAll(top20);
                        }
                    }

                    // ==========================================
                    // UI THREAD - YOUR EXACT PATTERN STARTS HERE
                    // ==========================================
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ViewGroup vg = (ViewGroup) c.getView();
                                vg.removeAllViews();
                                ScrollView sv = new ScrollView(context);
                                LinearLayout ml = new LinearLayout(context);
                                ml.setOrientation(LinearLayout.VERTICAL);
                                ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, statType, lang)));

                                // If no groups or only one group, show stats without collapse header
                                if (groups.isEmpty() || groups.size() == 1) {
                                    // Determine which list to show
                                    List<PlayerStat> listToShow;
                                    String titleText = "";

                                    if (groups.size() == 1) {
                                        // Show the single valid group
                                        String gid = groups.get(0);
                                        listToShow = dataByGroup.get(gid);
                                        titleText = getLocalizedText(null, "group", lang) + " " + gid;

                                        // Add header
                                        View header = createListGroupHeaderView(titleText, lang);
                                        ml.addView(header);
                                    } else {
                                        // No groups found. Show the "invalid" bucket (which contains everyone)
                                        // OR show nothing if empty.
                                        listToShow = dataByGroup.get("_invalid_");
                                        // No header added here, just the list directly
                                    }

                                    if (listToShow != null && !listToShow.isEmpty()) {
                                        for (PlayerStat stat : listToShow) {
                                            int count = "goals".equals(statType) ? stat.goals : stat.assists;
                                            if (count > 0) {
                                                View row = createTournamentStatRowWithType(stat, count, lang, statType);
                                                ml.addView(row);
                                            }
                                        }
                                    } else {
                                        // Fallback for no data
                                        TextView noData = new TextView(context);
                                        noData.setText(getLocalizedText(null, "no_data_available", lang));
                                        noData.setTextColor(CLR_TEAL);
                                        noData.setGravity(Gravity.CENTER);
                                        noData.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                                        ml.addView(noData);
                                    }
                                } else {
                                    // Multiple groups - show with collapse/expand functionality
                                    final java.util.Map<String, Boolean> expandedStates = new java.util.HashMap<>();
                                    for (String gid : groups) {
                                        expandedStates.put(gid, true);
                                    }
                                    
                                    for (final String gid : groups) {
                                        if (gid != null && !gid.isEmpty()) {
                                            // Create group container
                                            final LinearLayout groupContainer = new LinearLayout(context);
                                            groupContainer.setOrientation(LinearLayout.VERTICAL);
                                            groupContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                                            
                                            // Create clickable header with arrow
                                            final LinearLayout headerLayout = createCollapsibleGroupHeader(
                                                getLocalizedText(null, "group", lang) + " " + gid, 
                                                lang, 
                                                expandedStates.get(gid)
                                            );

                                            // Create content container
                                            final LinearLayout contentContainer = new LinearLayout(context);
                                            contentContainer.setOrientation(LinearLayout.VERTICAL);
                                            contentContainer.setVisibility(expandedStates.get(gid) ? View.VISIBLE : View.GONE);

                                            List<PlayerStat> groupStats = dataByGroup.get(gid);
                                            if (groupStats != null) {
                                                for (PlayerStat stat : groupStats) {
                                                    int count = "goals".equals(statType) ? stat.goals : stat.assists;
                                                    if (count > 0) {
                                                        View row = createTournamentStatRowWithType(stat, count, lang, statType);
                                                        contentContainer.addView(row);
                                                    }
                                                }
                                            }

                                            // Make header clickable
                                            headerLayout.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    boolean isExpanded = contentContainer.getVisibility() == View.VISIBLE;
                                                    if (isExpanded) {
                                                        contentContainer.setVisibility(View.GONE);
                                                        updateGroupHeaderArrow(headerLayout, false);
                                                        expandedStates.put(gid, false);
                                                    } else {
                                                        contentContainer.setVisibility(View.VISIBLE);
                                                        updateGroupHeaderArrow(headerLayout, true);
                                                        expandedStates.put(gid, true);
                                                    }
                                                }
                                            });
                                            
                                            groupContainer.addView(headerLayout);
                                            groupContainer.addView(contentContainer);
                                            ml.addView(groupContainer);
                                        }
                                    }
                                }

                                sv.addView(ml);
                                vg.addView(sv);
                            } catch (Exception e) {
                                AfterParsingFail("Error building UI: " + e.getMessage());
                            }
                        }
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + e.getMessage()); }});
                }
            }
        });
    }

    // Add this helper method if you don't have it (Required for the arrow to update)
    private void updateGroupHeaderArrow(View header, boolean isExpanded) {
        TextView arrowView = (TextView) header.getTag();
        if (arrowView != null) {
            arrowView.setText(isExpanded ? "▼" : "▶");
            arrowView.setTag(isExpanded ? "expanded" : "collapsed");
        }
    }            

    private String[] parsePlayerEventString(String raw) {
        Pattern p = Pattern.compile("(.*?) *\\((\\d+)\\)");
        Matcher m = p.matcher(raw);
        if (m.find()) return new String[]{m.group(1).trim(), m.group(2)};
        return new String[]{raw.trim(), "1"};
    }

    private void processTeamEvents(java.util.Map<String, PlayerStat> stats, JSONObject match, String key, JSONObject tInfo, String lang) throws JSONException {
        JSONArray events = getLocalizedArray(match, key, lang);
        if (events == null || tInfo == null) return;
        boolean isParsingAssists = false;
        String teamId = tInfo.getString("team_id");
        String teamName = getLocalizedText(tInfo, "name", lang);
        for (int i = 0; i < events.length(); i++) {
            String eventString = events.getString(i);
            if (eventString.equals(ASSISTS_DELIMITER_AR) || eventString.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) {
                isParsingAssists = true;
                continue;
            }
            String[] parsed = parsePlayerEventString(eventString);
            String pName = parsed[0];
            if (pName.equals("لا يوجد بيانات")) continue;
            int count = Integer.parseInt(parsed[1]);
            String uniqueKey = pName + "_" + teamId;
            PlayerStat pStat = stats.get(uniqueKey);
            if (pStat == null) {
                pStat = new PlayerStat(pName, teamId, teamName);
                stats.put(uniqueKey, pStat);
            }
            if (isParsingAssists) pStat.assists += count;
            else pStat.goals += count;
        }
    }

    // Process team assists separately
private void processTeamAssists(java.util.Map<String, PlayerStat> stats, JSONObject match, String key, JSONObject tInfo, String lang) throws JSONException {
    JSONArray events = getLocalizedArray(match, key, lang);
    if (events == null || tInfo == null) return;
    
    boolean isParsingAssists = false;
    String teamId = tInfo.getString("team_id");
    String teamName = getLocalizedText(tInfo, "name", lang);
    
    for (int i = 0; i < events.length(); i++) {
        String eventString = events.getString(i);
        
        // Check for assist section
        if (eventString.equals(ASSISTS_DELIMITER_AR) || eventString.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) {
            isParsingAssists = true;
            continue;
        }
        
        if (isParsingAssists) {
            String[] parsed = parsePlayerEventString(eventString);
            String playerName = parsed[0];
            if (playerName.equals("لا يوجد بيانات")) continue;
            int count = Integer.parseInt(parsed[1]);
            String uniqueKey = playerName + "_" + teamId;
            
            PlayerStat pStat = stats.get(uniqueKey);
            if (pStat == null) {
                pStat = new PlayerStat(playerName, teamId, teamName);
                stats.put(uniqueKey, pStat);
            }
            pStat.assists += count;
        }
    }
}

            private void calculateAndDisplayCleanSheets(HVArrangement c, String lang) {
        if (jsonData == null) return;
        
        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    java.util.Map<String, PlayerStat> keeperStats = new java.util.HashMap<>();
                    JSONArray matches = jsonData.optJSONArray("matches");
                    if (matches == null) return;
                    JSONArray teams = jsonData.optJSONArray("teams");
                    
                    // 1. Process all matches to get ALL keepers
                    for (int i = 0; i < matches.length(); i++) {
                        JSONObject match = matches.getJSONObject(i);
                        if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                        
                        if (match.getInt("away_score") == 0) {
                            JSONObject teamInfo = getTeamInfoById(match.getString("home_team_id"), teams);
                            updateCleanSheetStat(keeperStats, teamInfo, getLocalizedArray(match, "home_squade", lang), lang);
                        }
                        if (match.getInt("home_score") == 0) {
                            JSONObject teamInfo = getTeamInfoById(match.getString("away_team_id"), teams);
                            updateCleanSheetStat(keeperStats, teamInfo, getLocalizedArray(match, "away_squade", lang), lang);
                        }
                    }

                    // 2. Identify valid groups from the TEAMS data
                    Set<String> uniqueGroupNames = new HashSet<>();
                    for (int i = 0; i < teams.length(); i++) {
                        String group = teams.getJSONObject(i).optString("group", "");
                        
                        // Clean up the string
                        if (group == null) group = "";
                        group = group.trim();
                        
                        // FILTER LOGIC:
                        // 1. Cannot be empty
                        // 2. Cannot be the word "null", "none", or "others"
                        // 3. REMOVED the check for pure numbers (\d+) to allow groups like "1", "2", "1-2-3"
                        
                        boolean isValid = !group.isEmpty() && 
                                          !group.equalsIgnoreCase("null") && 
                                          !group.equalsIgnoreCase("others") &&
                                          !group.equalsIgnoreCase("none");
                        
                        if (isValid) {
                            uniqueGroupNames.add(group);
                        }
                    }
                    
                    final List<String> groups = new ArrayList<>(uniqueGroupNames);
                    Collections.sort(groups); // Sort A, B, C...

                    // 3. Prepare the data map: Group Name -> List of Keepers
                    final Map<String, List<PlayerStat>> dataByGroup = new LinkedHashMap<>();
                    
                    // Add valid groups
                    for (String g : groups) {
                        dataByGroup.put(g, new ArrayList<PlayerStat>());
                    }
                    // Add a catch-all for invalid groups
                    dataByGroup.put("_invalid_", new ArrayList<PlayerStat>());

                    // Distribute keepers into these buckets
                    for (PlayerStat stat : keeperStats.values()) {
                        JSONObject tObj = getTeamInfoById(stat.teamId, teams);
                        String pGroup = (tObj != null) ? tObj.optString("group", "") : "";
                        
                        boolean foundGroup = false;
                        if (pGroup != null && !pGroup.trim().isEmpty() && dataByGroup.containsKey(pGroup)) {
                            dataByGroup.get(pGroup).add(stat);
                            foundGroup = true;
                        }
                        
                        if (!foundGroup) {
                            dataByGroup.get("_invalid_").add(stat);
                        }
                    }

                    // Sort keepers inside each group and limit to 20
                    for (List<PlayerStat> list : dataByGroup.values()) {
                        Collections.sort(list, new Comparator<PlayerStat>() {
                            @Override public int compare(PlayerStat o1, PlayerStat o2) {
                                return Integer.valueOf(o2.cleanSheets).compareTo(o1.cleanSheets);
                            }
                        });
                        if (list.size() > 20) {
                            List<PlayerStat> top20 = new ArrayList<>(list.subList(0, 20));
                            list.clear();
                            list.addAll(top20);
                        }
                    }

                    // ==========================================
                    // UI THREAD - MATCHING YOUR PATTERN
                    // ==========================================
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ViewGroup vg = (ViewGroup) c.getView();
                                vg.removeAllViews();
                                ScrollView sv = new ScrollView(context);
                                LinearLayout ml = new LinearLayout(context);
                                ml.setOrientation(LinearLayout.VERTICAL);
                                ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, "clean_sheets", lang)));

                                // If no groups or only one group, show stats without collapse header
                                if (groups.isEmpty() || groups.size() == 1) {
                                    // Determine which list to show
                                    List<PlayerStat> listToShow;
                                    String titleText = "";

                                    if (groups.size() == 1) {
                                        // Show the single valid group
                                        String gid = groups.get(0);
                                        listToShow = dataByGroup.get(gid);
                                        titleText = getLocalizedText(null, "group", lang) + " " + gid;

                                        // Add header
                                        View header = createListGroupHeaderView(titleText, lang);
                                        ml.addView(header);
                                    } else {
                                        // No groups found. Show the "invalid" bucket (which contains everyone)
                                        listToShow = dataByGroup.get("_invalid_");
                                        // No header added
                                    }

                                    if (listToShow != null && !listToShow.isEmpty()) {
                                        for (PlayerStat stat : listToShow) {
                                            if (stat.cleanSheets > 0) {
                                                View row = createTournamentStatRowWithType(stat, stat.cleanSheets, lang, "clean_sheets");
                                                ml.addView(row);
                                            }
                                        }
                                    } else {
                                        // Fallback for no data
                                        TextView noData = new TextView(context);
                                        noData.setText(getLocalizedText(null, "no_data_available", lang));
                                        noData.setTextColor(CLR_TEAL);
                                        noData.setGravity(Gravity.CENTER);
                                        noData.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                                        ml.addView(noData);
                                    }
                                } else {
                                    // Multiple groups - show with collapse/expand functionality
                                    final java.util.Map<String, Boolean> expandedStates = new java.util.HashMap<>();
                                    for (String gid : groups) {
                                        expandedStates.put(gid, true);
                                    }
                                    
                                    for (final String gid : groups) {
                                        if (gid != null && !gid.isEmpty()) {
                                            // Create group container
                                            final LinearLayout groupContainer = new LinearLayout(context);
                                            groupContainer.setOrientation(LinearLayout.VERTICAL);
                                            groupContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                                            
                                            // Create clickable header with arrow
                                            final LinearLayout headerLayout = createCollapsibleGroupHeader(
                                                getLocalizedText(null, "group", lang) + " " + gid, 
                                                lang, 
                                                expandedStates.get(gid)
                                            );

                                            // Create content container
                                            final LinearLayout contentContainer = new LinearLayout(context);
                                            contentContainer.setOrientation(LinearLayout.VERTICAL);
                                            contentContainer.setVisibility(expandedStates.get(gid) ? View.VISIBLE : View.GONE);

                                            List<PlayerStat> groupStats = dataByGroup.get(gid);
                                            if (groupStats != null) {
                                                for (PlayerStat stat : groupStats) {
                                                    if (stat.cleanSheets > 0) {
                                                        View row = createTournamentStatRowWithType(stat, stat.cleanSheets, lang, "clean_sheets");
                                                        contentContainer.addView(row);
                                                    }
                                                }
                                            }

                                            // Make header clickable
                                            headerLayout.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    boolean isExpanded = contentContainer.getVisibility() == View.VISIBLE;
                                                    if (isExpanded) {
                                                        contentContainer.setVisibility(View.GONE);
                                                        updateGroupHeaderArrow(headerLayout, false);
                                                        expandedStates.put(gid, false);
                                                    } else {
                                                        contentContainer.setVisibility(View.VISIBLE);
                                                        updateGroupHeaderArrow(headerLayout, true);
                                                        expandedStates.put(gid, true);
                                                    }
                                                }
                                            });
                                            
                                            groupContainer.addView(headerLayout);
                                            groupContainer.addView(contentContainer);
                                            ml.addView(groupContainer);
                                        }
                                    }
                                }

                                sv.addView(ml);
                                vg.addView(sv);
                            } catch (Exception e) {
                                AfterParsingFail("Error building UI: " + e.getMessage());
                            }
                        }
                    });

                } catch (Exception e) {
                    activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + e.getMessage()); }});
                }
            }
        });
    }

    private void updateCleanSheetStat(java.util.Map<String, PlayerStat> stats, JSONObject teamInfo, JSONArray squad, String lang) throws JSONException {
        if (teamInfo == null) return;
        String teamId = teamInfo.getString("team_id");
        String teamName = getLocalizedText(teamInfo, "name", lang);
        String keeperName = findGoalkeeperInSquad(squad);
        String uniqueKey = (keeperName != null) ? (keeperName + "_" + teamId) : teamId;
        String displayName = (keeperName != null) ? keeperName : teamName;
        PlayerStat stat = stats.get(uniqueKey);
        if (stat == null) {
            stat = new PlayerStat(displayName, teamId, teamName);
            stats.put(uniqueKey, stat);
        }
        stat.cleanSheets++;
    }

    private String findGoalkeeperInSquad(JSONArray squad) throws JSONException {
        if (squad == null) return null;
        Pattern p = Pattern.compile("(.*?) *\\((?:ح\\.م|gk)\\)", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < squad.length(); i++) {
            Matcher m = p.matcher(squad.getString(i));
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }

    private View createStatsHeaderRow(String lang, String title) {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
        r.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable shBg = new GradientDrawable();
        shBg.setColor(CLR_CARD);
        shBg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) r.setBackground(shBg); else r.setBackgroundDrawable(shBg);
        if (Build.VERSION.SDK_INT >= 21) r.setElevation(dpToPx(4));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, 0, 0, dpToPx(8));
        r.setLayoutParams(rp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TextView pth = createTextView(getLocalizedText(null, "player", lang) + " / " + getLocalizedText(null, "team", lang), 0, 1, true);
        pth.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pth.setTextColor(Color.WHITE);
        TextView sh = createTextView(title, -2, 0, true);
        sh.setGravity(Gravity.CENTER);
        sh.setTextColor(CLR_AQUA);
        r.addView(pth);
        r.addView(sh);
        return r;
    }

// Helper method to determine stat type
private String determineStatType(PlayerStat stat) {
    if (stat.goals > 0 && stat.assists == 0) return "goals";
    if (stat.assists > 0 && stat.goals == 0) return "assists";
    if (stat.cleanSheets > 0) return "clean_sheets";
    // If both have values, we need to know which list this is from
    // We'll add a parameter to the method call
    return "goals"; // default
}

    // Create tournament stat row with explicit stat type
private View createTournamentStatRowWithType(PlayerStat stat, int count, String lang, final String statType) {
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    final String playerName = stat.playerName;
    final String teamId = stat.teamId;
    final String teamName = stat.teamName;

    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
    r.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
    rp.setMargins(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
    r.setLayoutParams(rp);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        r.setBackground(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
    } else {
        r.setBackgroundDrawable(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        r.setElevation(dpToPx(6));
    }

    LinearLayout ptl = new LinearLayout(context);
    ptl.setOrientation(LinearLayout.VERTICAL);
    ptl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    ptl.setPadding(0, 0, dpToPx(12), 0);

    // Make player name clickable
    TextView pName = createTextView(stat.playerName, -1, 0, true);
    pName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    pName.setTextSize(16);
    pName.setTextColor(CLR_AQUA);
    pName.setTypeface(null, Typeface.BOLD);

    pName.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            PlayerClicked(playerName, teamId, teamName, statType);
            showPlayerStatsPopup(playerName, teamId, teamName, statType, lang);
        }
    });

    TextView tName = createTextView(stat.teamName, -1, 0, false);
    tName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    tName.setTextColor(CLR_TEAL);
    tName.setTextSize(12);

    ptl.addView(pName);
    ptl.addView(tName);

    // Show the count with appropriate icon
    String icon = "";
    if ("goals".equals(statType)) {
        icon = "⚽ ";
    } else if ("assists".equals(statType)) {
        icon = "🎯 ";
    } else if ("clean_sheets".equals(statType)) {
        icon = "🧤 ";
    }

    TextView sv = createTextView(icon + count, -2, 0, true);
    sv.setTextSize(20);
    sv.setTextColor(Color.WHITE);
    sv.setMinWidth(dpToPx(64));
    sv.setGravity(Gravity.CENTER);

    r.addView(ptl);
    r.addView(sv);

    return r;
}

    private void buildTeamListView(LinearLayout c, java.util.List<JSONObject> teamList, final String lang) throws JSONException {
        c.removeAllViews();
        java.util.List<String> groups = getJavaGroupList();
        if (groups.isEmpty()) {
                for (JSONObject team : teamList) {
                c.addView(createTeamItemView(team, lang));
            }
        } else {
            for (String group : groups) {
                boolean headerAdded = false;
                for (JSONObject team : teamList) {
                    if (group.equals(team.optString("group"))) {
                        if (!headerAdded) {
                            c.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + group, lang));
                            headerAdded = true;
                        }
                        c.addView(createTeamItemView(team, lang));
                    }
                }
            }
        }
    }

    private View createTeamItemView(final JSONObject team, String lang) throws JSONException {
    final boolean isRTL = "ar".equalsIgnoreCase(lang);
    final String teamId = team.getString("team_id");
    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setPadding(32, 24, 32, 24);
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
    rp.setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(10));
    r.setLayoutParams(rp);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        r.setBackground(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
    } else {
        r.setBackgroundDrawable(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        r.setElevation(dpToPx(4));
    }

    // This is already correct - makes entire row clickable
    r.setOnClickListener(new View.OnClickListener() { 
        @Override 
        public void onClick(View v) { 
            TeamClicked(teamId); 
        } 
    });
    
    ImageView logo = new ImageView(context);
    logo.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
    Picasso.with(context).load(team.optString("logo")).into(logo);
    TextView name = createTextView(getLocalizedText(team, "name", lang), 0, 1, true);
    name.setTextColor(CLR_AQUA);
    name.setTextSize(16);
    name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    name.setPadding(16, 0, 16, 0);
    r.addView(logo);
    r.addView(name);
    return r;
}

    private java.util.List<String> getJavaGroupList() {
        java.util.List<String> gl = new java.util.ArrayList<>();
        try {
            if (jsonData == null || !jsonData.has("teams")) return gl;
            JSONArray teams = jsonData.optJSONArray("teams");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject item = teams.getJSONObject(i);
                if (item.has("group") && !item.isNull("group") && !item.getString("group").isEmpty()) {
                    String group = item.getString("group");
                    if (!gl.contains(group)) gl.add(group);
                }
            }
        } catch (Exception e) {
            Log.w("FootballSuite", "getJavaGroupList error", e);
        }
        Collections.sort(gl);
        return gl;
    }

    private void calculateAndDisplayTeamStats(HVArrangement c, String teamId, String lang, final String statType) {
    if (jsonData == null || teamId == null || teamId.isEmpty()) {
        AfterParsingFail("Invalid data or team ID");
        return;
    }
    
    backgroundExecutor.submit(new Runnable() {
        @Override
        public void run() {
            try {
                java.util.Map<String, PlayerStat> playerStats = new java.util.HashMap<>();
                JSONArray matches = jsonData.optJSONArray("matches");
                if (matches == null) return;
                JSONArray teams = jsonData.optJSONArray("teams");
                
                for (int i = 0; i < matches.length(); i++) {
                    JSONObject match = matches.getJSONObject(i);
                    if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                    
                    if (teamId.equals(match.getString("home_team_id"))) {
                        JSONObject teamInfo = getTeamInfoById(teamId, teams);
                        if ("goals".equals(statType)) {
                            processTeamEvents(playerStats, match, "home_scorers", teamInfo, lang);
                        } else if ("assists".equals(statType)) {
                            processTeamAssists(playerStats, match, "home_scorers", teamInfo, lang);
                        }
                    }
                    
                    if (teamId.equals(match.getString("away_team_id"))) {
                        JSONObject teamInfo = getTeamInfoById(teamId, teams);
                        if ("goals".equals(statType)) {
                            processTeamEvents(playerStats, match, "away_scorers", teamInfo, lang);
                        } else if ("assists".equals(statType)) {
                            processTeamAssists(playerStats, match, "away_scorers", teamInfo, lang);
                        }
                    }
                }
                
                final List<PlayerStat> sortedStats = new ArrayList<>(playerStats.values());
                Collections.sort(sortedStats, new Comparator<PlayerStat>() {
                    @Override public int compare(PlayerStat o1, PlayerStat o2) {
                        if ("goals".equals(statType)) {
                            return Integer.valueOf(o2.goals).compareTo(o1.goals);
                        } else if ("assists".equals(statType)) {
                            return Integer.valueOf(o2.assists).compareTo(o1.assists);
                        } else {
                            return Integer.valueOf(o2.cleanSheets).compareTo(o1.cleanSheets);
                        }
                    }
                });
                
                // ✅ Create final variable for use in inner class
                final String finalStatType = statType;
                
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ViewGroup vg = (ViewGroup) c.getView();
                            vg.removeAllViews();
                            ScrollView sv = new ScrollView(context);
                            LinearLayout ml = new LinearLayout(context);
                            ml.setOrientation(LinearLayout.VERTICAL);
                            ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, finalStatType, lang)));
                            
                            for (PlayerStat stat : sortedStats) {
                                int count = 0;
                                if ("goals".equals(finalStatType)) {
                                    count = stat.goals;
                                } else if ("assists".equals(finalStatType)) {
                                    count = stat.assists;
                                } else {
                                    count = stat.cleanSheets;
                                }
                                
                                if (count > 0) {
                                    // ✅ Now finalStatType is accessible
                                    ml.addView(createTeamStatRow(stat, count, lang, finalStatType));
                                }
                            }
                            sv.addView(ml);
                            vg.addView(sv);
                        } catch (Exception e) {
                            AfterParsingFail("Error building UI: " + e.getMessage());
                        }
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("Error: " + e.getMessage()); }});
            }
        }
    });
}

    private java.util.List<TeamStats> calculateStandingsForGroup(String gId, String stageId) throws JSONException {
        final JSONArray teams = jsonData.optJSONArray("teams");
        if (teams == null) return new java.util.ArrayList<>();
        final JSONArray matches = jsonData.optJSONArray("matches");
        java.util.Map<String, TeamStats> statsMap = new java.util.HashMap<>();
        boolean hasGroupFilter = gId != null && !gId.isEmpty();
        boolean hasStageFilter = stageId != null && !stageId.isEmpty();

        // 1. Initialize Teams
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.getJSONObject(i);
            if (!hasGroupFilter || gId.equals(team.optString("group"))) {
                TeamStats stats = new TeamStats(team.getString("team_id"));

                String deductionStr = team.optString("point_deduction", "0");
                try {
                    if (deductionStr != null && !deductionStr.isEmpty()) {
                        int deduction = Integer.parseInt(deductionStr);
                        stats.points -= deduction; 
                    }
                } catch (NumberFormatException e) {
                    // Ignore if value is not a number
                }

                statsMap.put(team.getString("team_id"), stats);
            }
        }

        // 2. Process Matches
        if (matches != null) {
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);

                // Ignore knockout matches for standings
                if ("knockout".equalsIgnoreCase(match.optString("stage"))) continue;

                if (hasStageFilter && !stageId.equals(match.optString("stage"))) continue;
                if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;

                String homeId = match.getString("home_team_id");
                String awayId = match.getString("away_team_id");
                boolean homeInMap = statsMap.containsKey(homeId);
                boolean awayInMap = statsMap.containsKey(awayId);

                if (homeInMap || awayInMap) {
                    int hs = match.getInt("home_score");
                    int as = match.getInt("away_score");
                    String pWinner = match.optString("penalty_winner_team_id");

                    if (homeInMap) {
                        TeamStats homeStats = statsMap.get(homeId);
                        homeStats.matchesPlayed++; homeStats.goalsFor += hs; homeStats.goalsAgainst += as;
                        if (hs > as) { homeStats.wins++; homeStats.points += 3; }
                        else if (as > hs) { homeStats.losses++; }
                        else {
                            homeStats.draws++; homeStats.points++;
                            if (pWinner.equals(homeId)) { homeStats.points++; homeStats.penaltyPoints++; }
                        }
                    }
                    if (awayInMap) {
                        TeamStats awayStats = statsMap.get(awayId);
                        awayStats.matchesPlayed++; awayStats.goalsFor += as; awayStats.goalsAgainst += hs;
                        if (as > hs) { awayStats.wins++; awayStats.points += 3; }
                        else if (hs > as) { awayStats.losses++; }
                        else {
                            awayStats.draws++; awayStats.points++;
                            if (pWinner.equals(awayId)) { awayStats.points++; awayStats.penaltyPoints++; }
                        }
                    }
                }
            }
        }
        if (statsMap.isEmpty()) return null;

        final boolean hasCompletedMatches;
        if (matches != null) {
            boolean tempHasCompletedMatches = false;
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                if ("completed".equalsIgnoreCase(match.optString("status")) && !"knockout".equalsIgnoreCase(match.optString("stage"))) {
                    if (!hasStageFilter || stageId.equals(match.optString("stage"))) {
                        tempHasCompletedMatches = true;
                        break;
                    }
                }
            }
            hasCompletedMatches = tempHasCompletedMatches;
        } else {
            hasCompletedMatches = false;
        }

        java.util.List<TeamStats> sorted = new java.util.ArrayList<>(statsMap.values());
        Collections.sort(sorted, new Comparator<TeamStats>() {
            @Override public int compare(TeamStats t1, TeamStats t2) {
                // 1. Total Points
                int pointsCompare = Integer.valueOf(t2.points).compareTo(t1.points);
                if (pointsCompare != 0) return pointsCompare;

                if (!hasCompletedMatches) {
                    return t1.teamId.compareTo(t2.teamId);
                }

                // 2. Head-to-Head (Only if BOTH matches played)
                int h2hPoints1 = 0, h2hPoints2 = 0;
                int h2hGoalsFor1 = 0, h2hGoalsAgainst1 = 0;
                int h2hMatchesPlayed = 0;

                try {
                    if (matches != null) {
                        for (int i = 0; i < matches.length(); i++) {
                            JSONObject match = matches.getJSONObject(i);
                            if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                            if ("knockout".equalsIgnoreCase(match.optString("stage"))) continue;

                            String homeId = match.getString("home_team_id");
                            String awayId = match.getString("away_team_id");

                            if ((homeId.equals(t1.teamId) && awayId.equals(t2.teamId)) || (homeId.equals(t2.teamId) && awayId.equals(t1.teamId))) {
                                h2hMatchesPlayed++;
                                int hs = match.getInt("home_score");
                                int as = match.getInt("away_score");

                                if (homeId.equals(t1.teamId)) {
                                    h2hGoalsFor1 += hs; h2hGoalsAgainst1 += as;
                                    if (hs > as) h2hPoints1 += 3; else if (as > hs) h2hPoints2 += 3; else { h2hPoints1++; h2hPoints2++; }
                                } else {
                                    h2hGoalsFor1 += as; h2hGoalsAgainst1 += hs;
                                    if (as > hs) h2hPoints1 += 3; else if (hs > as) { h2hPoints2 += 3; } else { h2hPoints1++; h2hPoints2++; }
                                }
                            }
                        }
                    }
                } catch (JSONException e) { /* ignore */ }

                if (h2hMatchesPlayed >= 2) {
                    int h2hPointsCompare = Integer.valueOf(h2hPoints2).compareTo(h2hPoints1);
                    if (h2hPointsCompare != 0) return h2hPointsCompare;

                    int h2hGd1 = h2hGoalsFor1 - h2hGoalsAgainst1;
                    int h2hGd2 = -h2hGd1;
                    int h2hGdCompare = Integer.valueOf(h2hGd2).compareTo(h2hGd1);
                    if (h2hGdCompare != 0) return h2hGdCompare;
                }

                // 3. Goal Difference (General)
                int gdc = Integer.valueOf(t2.getGoalDifference()).compareTo(t1.getGoalDifference());
                if (gdc != 0) return gdc;

                // 4. Goals Scored (General)
                int gfc = Integer.valueOf(t2.goalsFor).compareTo(t1.goalsFor);
                if (gfc != 0) return gfc;

                // 5. Alphabetical
                try {
                    return getLocalizedText(getTeamInfoById(t1.teamId, teams), "name", "en").compareTo(
                           getLocalizedText(getTeamInfoById(t2.teamId, teams), "name", "en"));
                } catch (JSONException e) { return 0; }
            }
        });
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).position = i + 1;
        return sorted;
    }


// Show player stats popup (goals, assists, or clean sheets)
    private void showPlayerStatsPopup(String playerName, String teamId, String teamName, String statType, String lang) {
    final Dialog dialog = new Dialog(context);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    
    // Create layout programmatically
    LinearLayout mainLayout = new LinearLayout(context);
    mainLayout.setOrientation(LinearLayout.VERTICAL);
    mainLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
    GradientDrawable dialogBg = new GradientDrawable();
    dialogBg.setColor(CLR_DIALOG);
    dialogBg.setCornerRadius(dpToPx(16));
    dialogBg.setStroke(dpToPx(1), CLR_AQUA);
    if (Build.VERSION.SDK_INT >= 16) mainLayout.setBackground(dialogBg); else mainLayout.setBackgroundDrawable(dialogBg);

    // Get localized stat title
    String statTitle = "";
    String statIcon = "";
    if ("goals".equals(statType)) {
        statTitle = getLocalizedText(null, "goals", lang);
        statIcon = "⚽  ";
    } else if ("assists".equals(statType)) {
        statTitle = getLocalizedText(null, "assists", lang);
        statIcon = "🎯  ";
    } else if ("clean_sheets".equals(statType)) {
        statTitle = getLocalizedText(null, "clean_sheets", lang);
        statIcon = "🧤  ";
    }

    // Title row: icon + player name
    TextView title = new TextView(context);
    title.setText(statIcon + playerName);
    title.setTextSize(18);
    title.setTypeface(null, Typeface.BOLD);
    title.setGravity(Gravity.CENTER);
    title.setPadding(0, 0, 0, dpToPx(6));
    title.setTextColor(CLR_AQUA);
    mainLayout.addView(title);

    // Stat type subtitle
    TextView statSubtitle = new TextView(context);
    statSubtitle.setText(statTitle);
    statSubtitle.setTextSize(13);
    statSubtitle.setGravity(Gravity.CENTER);
    statSubtitle.setPadding(0, 0, 0, dpToPx(6));
    statSubtitle.setTextColor(CLR_TEAL);
    mainLayout.addView(statSubtitle);

    // Team info
    TextView teamInfo = new TextView(context);
    teamInfo.setText(getLocalizedText(null, "team", lang) + ": " + teamName);
    teamInfo.setTextSize(13);
    teamInfo.setGravity(Gravity.CENTER);
    teamInfo.setPadding(0, 0, 0, dpToPx(14));
    teamInfo.setTextColor(CLR_TEAL);
    mainLayout.addView(teamInfo);

    // Divider
    View divider = new View(context);
    divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(1)));
    divider.setBackgroundColor(CLR_BORDER);
    mainLayout.addView(divider);
    
    // ScrollView for matches list
    ScrollView scrollView = new ScrollView(context);
    LinearLayout matchesContainer = new LinearLayout(context);
    matchesContainer.setOrientation(LinearLayout.VERTICAL);
    matchesContainer.setPadding(0, dpToPx(16), 0, dpToPx(16));
    
    // Find all matches where this player achieved the stat
    try {
        JSONArray matches = jsonData.optJSONArray("matches");
        JSONArray teams = jsonData.optJSONArray("teams");
        if (matches == null || teams == null) return;

        // Get team info for opponent names
        JSONObject teamInfoObj = getTeamInfoById(teamId, teams);
        String teamNameLocal = getLocalizedText(teamInfoObj, "name", lang);
        
        // Collect all stats by this player
        List<MatchStatInfo> playerStats = new ArrayList<>();
        
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.getJSONObject(i);
            if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
            
            if ("goals".equals(statType)) {
                // Check home team scorers
                JSONArray homeScorers = getLocalizedArray(match, "home_scorers", lang);
                if (homeScorers != null) {
                    parseScorersFromMatchForStats(match, homeScorers, true, teamNameLocal, teamId, playerName, playerStats, lang);
                }
                
                // Check away team scorers
                JSONArray awayScorers = getLocalizedArray(match, "away_scorers", lang);
                if (awayScorers != null) {
                    parseScorersFromMatchForStats(match, awayScorers, false, teamNameLocal, teamId, playerName, playerStats, lang);
                }
            } else if ("assists".equals(statType)) {
                // Check home team assists
                JSONArray homeAssists = getLocalizedArray(match, "home_scorers", lang);
                if (homeAssists != null) {
                    parseAssistsFromMatch(match, homeAssists, true, teamNameLocal, teamId, playerName, playerStats, lang);
                }
                
                // Check away team assists
                JSONArray awayAssists = getLocalizedArray(match, "away_scorers", lang);
                if (awayAssists != null) {
                    parseAssistsFromMatch(match, awayAssists, false, teamNameLocal, teamId, playerName, playerStats, lang);
                }
            } else if ("clean_sheets".equals(statType)) {
                // Check clean sheets
                parseCleanSheetsFromMatch(match, teamId, playerName, playerStats, lang);
            }
        }
        
        // Sort by date (newest first)
        Collections.sort(playerStats, new Comparator<MatchStatInfo>() {
            @Override
            public int compare(MatchStatInfo o1, MatchStatInfo o2) {
                return o2.matchDate.compareTo(o1.matchDate);
            }
        });
        
        if (playerStats.isEmpty()) {
            TextView noStats = new TextView(context);
            noStats.setText(getLocalizedText(null, "no_stats_found", lang));
            noStats.setTextSize(14);
            noStats.setGravity(Gravity.CENTER);
            noStats.setTextColor(this.secondaryTextColor);
            noStats.setPadding(0, dpToPx(20), 0, dpToPx(20));
            matchesContainer.addView(noStats);
        } else {
            int totalCount = 0;
                for (MatchStatInfo stat : playerStats) {
                totalCount += stat.count;
                matchesContainer.addView(createMatchStatCard(stat, statType, lang, dialog));
            }
            
            // Add total footer
            View footerDivider = new View(context);
            footerDivider.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(1)));
            footerDivider.setBackgroundColor(CLR_BORDER);
            matchesContainer.addView(footerDivider);

            TextView totalView = new TextView(context);
            totalView.setText(statTitle + ": " + totalCount);
            totalView.setTextSize(16);
            totalView.setTypeface(null, Typeface.BOLD);
            totalView.setGravity(Gravity.CENTER);
            totalView.setPadding(0, dpToPx(14), 0, dpToPx(6));
            totalView.setTextColor(CLR_AQUA);
            matchesContainer.addView(totalView);
        }

        // Cap scroll height to 60% of screen
        int maxScrollHeight = (int)(context.getResources().getDisplayMetrics().heightPixels * 0.55);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(-1, -2);
        svParams.height = maxScrollHeight;
        scrollView.setLayoutParams(svParams);

        scrollView.addView(matchesContainer);
        mainLayout.addView(scrollView);

        // Close button
        Button closeButton = new Button(context);
        closeButton.setText(getLocalizedText(null, "close", lang));
        closeButton.setTextColor(CLR_CARD);
        closeButton.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(CLR_AQUA);
        btnBg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) closeButton.setBackground(btnBg); else closeButton.setBackgroundDrawable(btnBg);
        closeButton.setPadding(dpToPx(20), dpToPx(12), dpToPx(20), dpToPx(12));
        closeButton.setAllCaps(false);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.setMargins(0, dpToPx(16), 0, 0);
        closeButton.setLayoutParams(buttonParams);
        mainLayout.addView(closeButton);
        
        dialog.setContentView(mainLayout);
        
        // Set dialog width and height
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout((int)(context.getResources().getDisplayMetrics().widthPixels * 0.9), -2);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        
        dialog.show();
        
    } catch (Exception e) {
        AfterParsingFail("Error loading player stats: " + e.getMessage());
        Toast.makeText(context, "Error loading player data", Toast.LENGTH_SHORT).show();
    }
}

// Helper class for match stat info
private class MatchStatInfo {
    String matchId;
    String matchDate;
    String opponentTeam;
    String score;
    int count;
    boolean isHome;
    
    MatchStatInfo(String matchId, String matchDate, String opponentTeam, String score, int count, boolean isHome) {
        this.matchId = matchId;
        this.matchDate = matchDate;
        this.opponentTeam = opponentTeam;
        this.score = score;
        this.count = count;
        this.isHome = isHome;
    }
}

// Parse assists from match
private void parseAssistsFromMatch(JSONObject match, JSONArray scorers, boolean isHome, 
                                   String teamName, String teamId, String playerName, 
                                   List<MatchStatInfo> playerStats, String lang) throws JSONException {
    String matchId = match.getString("match_id");
    String matchDate = match.getString("date");
    
    // Get opponent team
    String opponentTeamId = isHome ? match.getString("away_team_id") : match.getString("home_team_id");
    JSONArray teams = jsonData.optJSONArray("teams");
    JSONObject opponentTeamObj = teams != null ? getTeamInfoById(opponentTeamId, teams) : null;
    String opponentTeamName = getLocalizedText(opponentTeamObj, "name", lang);
    
    // Always store score as "Home : Away"
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    String score = isRTL ? (match.getInt("away_score") + " : " + match.getInt("home_score")) : (match.getInt("home_score") + " : " + match.getInt("away_score"));
    
    boolean isParsingAssists = false;
    int assistCount = 0;
    
    for (int i = 0; i < scorers.length(); i++) {
        String eventString = scorers.getString(i);
        
        // Check for assist section
        if (eventString.equals(ASSISTS_DELIMITER_AR) || eventString.equalsIgnoreCase(ASSISTS_DELIMITER_EN)) {
            isParsingAssists = true;
            continue;
        }
        
        if (isParsingAssists) {
            String[] parsed = parsePlayerEventString(eventString);
            String assistPlayer = parsed[0];
            if (assistPlayer.equals(playerName)) {
                assistCount += Integer.parseInt(parsed[1]);
            }
        }
    }
    
    if (assistCount > 0) {
        playerStats.add(new MatchStatInfo(matchId, matchDate, opponentTeamName, score, assistCount, isHome));
    }
}

// Parse clean sheets from match
private void parseCleanSheetsFromMatch(JSONObject match, String teamId, String playerName, 
                                       List<MatchStatInfo> playerStats, String lang) throws JSONException {
    String matchId = match.getString("match_id");
    String matchDate = match.getString("date");
    
    // Get opponent team
    String homeTeamId = match.getString("home_team_id");
    String awayTeamId = match.getString("away_team_id");
    boolean isHome = teamId.equals(homeTeamId);
    String opponentTeamId = isHome ? awayTeamId : homeTeamId;
    JSONArray teams = jsonData.optJSONArray("teams");
    JSONObject opponentTeamObj = getTeamInfoById(opponentTeamId, teams);
    String opponentTeamName = getLocalizedText(opponentTeamObj, "name", lang);
    
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    String score = isRTL ? (match.getInt("away_score") + " : " + match.getInt("home_score")) : (match.getInt("home_score") + " : " + match.getInt("away_score"));
    
    // Check if the goalkeeper is the player
    JSONArray squad = isHome ? getLocalizedArray(match, "home_squade", lang) : getLocalizedArray(match, "away_squade", lang);
    String keeperName = findGoalkeeperInSquad(squad);
    
    if (keeperName != null && keeperName.equals(playerName)) {
        // Check if clean sheet
        int goalsConceded = isHome ? match.getInt("away_score") : match.getInt("home_score");
        if (goalsConceded == 0) {
            playerStats.add(new MatchStatInfo(matchId, matchDate, opponentTeamName, score, 1, isHome));
        }
    }
}

// Create match stat card view - using stored score
private View createMatchStatCard(MatchStatInfo stat, String statType, String lang, final Dialog parentDialog) {
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
    GradientDrawable cardBg = new GradientDrawable();
    cardBg.setColor(CLR_CARD);
    cardBg.setCornerRadius(dpToPx(12));
    cardBg.setStroke(dpToPx(1), CLR_BORDER);
    if (Build.VERSION.SDK_INT >= 16) card.setBackground(cardBg); else card.setBackgroundDrawable(cardBg);
    if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(4));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
    cp.setMargins(0, 0, 0, dpToPx(10));
    card.setLayoutParams(cp);

    // Date row
    TextView dateView = new TextView(context);
    dateView.setText(formatDate(stat.matchDate, lang));
    dateView.setTextSize(12);
    dateView.setTextColor(CLR_TEAL);
    dateView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
    dateView.setPadding(0, 0, 0, dpToPx(8));
    card.addView(dateView);
    
    // Teams logos and result row
    LinearLayout logosRow = new LinearLayout(context);
    logosRow.setOrientation(LinearLayout.HORIZONTAL);
    logosRow.setGravity(Gravity.CENTER);
    logosRow.setPadding(0, dpToPx(8), 0, dpToPx(8));
    
    try {
        JSONObject match = findMatchById(stat.matchId);
        if (match != null) {
            JSONArray teams = jsonData.optJSONArray("teams");
            String homeTeamId = match.getString("home_team_id");
            String awayTeamId = match.getString("away_team_id");
            
            JSONObject homeTeam = getTeamInfoById(homeTeamId, teams);
            JSONObject awayTeam = getTeamInfoById(awayTeamId, teams);
            
            // Home team logo
            ImageView homeLogo = new ImageView(context);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(0, dpToPx(60), 1);
            homeLogo.setLayoutParams(logoParams);
            homeLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (homeTeam != null) {
                String homeLogoUrl = homeTeam.optString("logo");
                if (!homeLogoUrl.isEmpty()) {
                    Picasso.with(context).load(homeLogoUrl).into(homeLogo);
                }
            }
            
            // Score text - FIX APPLIED HERE
            TextView scoreText = new TextView(context);
            scoreText.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(60), -2));
            int homeScore = match.getInt("home_score");
            int awayScore = match.getInt("away_score");
            
            // ✅ FIX: Apply RTL logic to match the score display in match details
            String scoreString;
            if (isRTL) {
                scoreString = String.format(Locale.US, "%d : %d", awayScore, homeScore);
            } else {
                scoreString = String.format(Locale.US, "%d : %d", homeScore, awayScore);
            }
            scoreText.setText(scoreString);
            
            scoreText.setTextSize(16);
            scoreText.setTypeface(null, Typeface.BOLD);
            scoreText.setTextColor(Color.WHITE);
            scoreText.setGravity(Gravity.CENTER);
            
            // Away team logo
            ImageView awayLogo = new ImageView(context);
            awayLogo.setLayoutParams(logoParams);
            awayLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (awayTeam != null) {
                String awayLogoUrl = awayTeam.optString("logo");
                if (!awayLogoUrl.isEmpty()) {
                    Picasso.with(context).load(awayLogoUrl).into(awayLogo);
                }
            }
            
            // Add views in correct order
            if (isRTL) {
                logosRow.addView(awayLogo);
                logosRow.addView(scoreText);
                logosRow.addView(homeLogo);
            } else {
                logosRow.addView(homeLogo);
                logosRow.addView(scoreText);
                logosRow.addView(awayLogo);
            }
        } else {
            // If match not found, just show the stored score
            TextView scoreOnly = new TextView(context);
            scoreOnly.setText(stat.score);
            scoreOnly.setTextSize(16);
            scoreOnly.setTypeface(null, Typeface.BOLD);
            scoreOnly.setTextColor(this.primaryTextColor);
            scoreOnly.setGravity(Gravity.CENTER);
            logosRow.addView(scoreOnly);
        }
    } catch (JSONException e) {
        // If error, just show the stored score
        TextView scoreOnly = new TextView(context);
        scoreOnly.setText(stat.score);
        scoreOnly.setTextSize(16);
        scoreOnly.setTypeface(null, Typeface.BOLD);
        scoreOnly.setTextColor(this.primaryTextColor);
        scoreOnly.setGravity(Gravity.CENTER);
        logosRow.addView(scoreOnly);
    }
    
    card.addView(logosRow);
    
    // Stats row - with icon and number
    LinearLayout statsRow = new LinearLayout(context);
    statsRow.setOrientation(LinearLayout.HORIZONTAL);
    statsRow.setGravity(Gravity.CENTER);
    statsRow.setPadding(0, dpToPx(12), 0, dpToPx(4));
    
    String statIcon = "";
    String statTitle = "";
    if ("goals".equals(statType)) {
        statIcon = "⚽";
        statTitle = getLocalizedText(null, "goals", lang);
    } else if ("assists".equals(statType)) {
        statIcon = "🎯";
        statTitle = getLocalizedText(null, "assists", lang);
    } else if ("clean_sheets".equals(statType)) {
        statIcon = "🧤";
        statTitle = getLocalizedText(null, "clean_sheets", lang);
    }
    
    TextView statIconView = new TextView(context);
    statIconView.setText(statIcon);
    statIconView.setTextSize(18);
    statIconView.setPadding(0, 0, dpToPx(8), 0);
    
    TextView statCountView = new TextView(context);
    statCountView.setText(String.valueOf(stat.count));
    statCountView.setTextSize(18);
    statCountView.setTypeface(null, Typeface.BOLD);
    statCountView.setTextColor(CLR_AQUA);

    TextView statTitleView = new TextView(context);
    statTitleView.setText(statTitle);
    statTitleView.setTextSize(14);
    statTitleView.setTextColor(Color.WHITE);
    statTitleView.setPadding(dpToPx(8), 0, 0, 0);
    
    statsRow.addView(statIconView);
    statsRow.addView(statCountView);
    statsRow.addView(statTitleView);
    
    card.addView(statsRow);
    
    // Make card clickable to show match details
    final String matchId = stat.matchId;
    card.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (parentDialog != null && parentDialog.isShowing()) {
                parentDialog.dismiss();
            }
            MatchClicked(matchId);
        }
    });
    
    return card;
}

// Helper method to create team logo ImageView
private ImageView createTeamLogo(JSONObject team) throws JSONException {
    ImageView logo = new ImageView(context);
    if (team != null) {
        String logoUrl = team.optString("logo");
        if (!logoUrl.isEmpty()) {
            Picasso.with(context).load(logoUrl).into(logo);
        }
    }
    return logo;
}

    private View buildStandingsTable(java.util.List<TeamStats> sorted, final String lang) throws JSONException {
        JSONArray teams = jsonData.optJSONArray("teams");
        ScrollView vsv = new ScrollView(context);
        final HorizontalScrollView hsv = new HorizontalScrollView(context);

        if ("ar".equalsIgnoreCase(lang) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            hsv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        LinearLayout tl = new LinearLayout(context);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.setPadding(0, 0, 16, 0);
        tl.addView(createHeaderRow(lang));
        // Use card-styled rows for standings (no divider lines)
        for (TeamStats stats : sorted) {
            // wrap each data row with card-like margins so rows look like cards
            View dataRow = createDataRow(stats, teams, lang);
            LinearLayout cardWrapper = new LinearLayout(context);
            cardWrapper.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cwParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cwParams.setMargins(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
            cardWrapper.setLayoutParams(cwParams);
            cardWrapper.addView(dataRow);
            tl.addView(cardWrapper);
        }
        hsv.addView(tl);
        vsv.addView(hsv);

        if ("ar".equalsIgnoreCase(lang)) {
            hsv.post(new Runnable() {
                @Override
                public void run() {
                    hsv.fullScroll(View.FOCUS_RIGHT);
                }
            });
        }
        return vsv;
    }

    private View createHeaderRow(String lang) {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        l.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        l.setGravity(Gravity.CENTER);
        // Card-like background for header row
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(CLR_CARD);
        headerBg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) l.setBackground(headerBg); else l.setBackgroundDrawable(headerBg);
        java.util.List<View> views = new java.util.ArrayList<>();
        views.add(createTextView("", 80, 0, true));
        views.add(createTextView(getLocalizedText(null, "team", lang), 400, 0, true));
        views.add(createTextView(getLocalizedText(null, "p", lang), 80, 0, true));
        views.add(createTextView(getLocalizedText(null, "pts", lang), 90, 0, true));
        views.add(createTextView(getLocalizedText(null, "f:a", lang), 100, 0, true));
        views.add(createTextView(getLocalizedText(null, "gd", lang), 80, 0, true));
        views.add(createTextView(getLocalizedText(null, "w", lang), 80, 0, true));
        views.add(createTextView(getLocalizedText(null, "d", lang), 80, 0, true));
        views.add(createTextView(getLocalizedText(null, "l", lang), 80, 0, true));
        views.add(createTextView(getLocalizedText(null, "p.p", lang), 80, 0, true));
        if (isRTL) Collections.reverse(views);
        for (View v : views) {
            if (v instanceof TextView) ((TextView) v).setTextColor(CLR_AQUA);
            l.addView(v);
        }
        return l;
    }

    private View createDataRow(TeamStats stats, JSONArray teamsData, String lang) throws JSONException {
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    final String teamId = stats.teamId;
    
    LinearLayout l = new LinearLayout(context);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
    l.setPadding(0, 0, 0, 0);
    l.setGravity(Gravity.CENTER_VERTICAL);

    // Apply card background to the entire row so it matches other lists
    GradientDrawable rowBg = new GradientDrawable();
    rowBg.setColor(CLR_CARD);
    rowBg.setCornerRadius(dpToPx(12));
    if (Build.VERSION.SDK_INT >= 16) l.setBackground(rowBg); else l.setBackgroundDrawable(rowBg);
    // Add internal padding for card content
    l.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
    
    JSONObject tInfo = getTeamInfoById(stats.teamId, teamsData);
    ImageView iv = new ImageView(context);
    iv.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
    String tName = getLocalizedText(tInfo, "name", lang);
    if (tInfo != null) Picasso.with(context).load(tInfo.optString("logo")).into(iv);
    
    LinearLayout tl = new LinearLayout(context);
    tl.setOrientation(LinearLayout.HORIZONTAL);
    tl.setLayoutParams(new LinearLayout.LayoutParams(400, -2));
    tl.setGravity((isRTL ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
    
    // Make the team layout clickable
    tl.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            TeamClicked(teamId);
        }
    });
    
    TextView tnv = createTextView(tName, -2, 0, true);
    tnv.setTextColor(Color.WHITE);
    if (isRTL) {
        tl.addView(tnv);
        tl.addView(iv);
    } else {
        tl.addView(iv);
        tl.addView(tnv);
    }
    
    String faScore = isRTL ? String.format(Locale.US, "%d:%d", stats.goalsAgainst, stats.goalsFor) : String.format(Locale.US, "%d:%d", stats.goalsFor, stats.goalsAgainst);
    TextView pointsView = createTextView(String.valueOf(stats.points), 90, 0, true);
    // Use aqua for points to make them prominent
    pointsView.setTextColor(CLR_AQUA);
    TextView gdView = createTextView(String.format(Locale.US, "%+d", stats.getGoalDifference()), 80, 0, true);
    gdView.setTextColor(Color.WHITE);

    TextView posView = createTextView(String.valueOf(stats.position), 80, 0, false);
    posView.setTextColor(Color.WHITE);

    TextView playedView = createTextView(String.valueOf(stats.matchesPlayed), 80, 0, false);
    playedView.setTextColor(Color.WHITE);

    TextView faView = createTextView(faScore, 100, 0, false);
    faView.setTextColor(Color.WHITE);

    TextView winsView = createTextView(String.valueOf(stats.wins), 80, 0, false);
    winsView.setTextColor(Color.WHITE);

    TextView drawsView = createTextView(String.valueOf(stats.draws), 80, 0, false);
    drawsView.setTextColor(Color.WHITE);

    TextView lossesView = createTextView(String.valueOf(stats.losses), 80, 0, false);
    lossesView.setTextColor(Color.WHITE);

    TextView penView = createTextView(String.valueOf(stats.penaltyPoints), 80, 0, false);
    penView.setTextColor(Color.WHITE);

    java.util.List<View> views = new java.util.ArrayList<>();
    views.add(posView);
    views.add(tl);
    views.add(playedView);
    views.add(pointsView);
    views.add(faView);
    views.add(gdView);
    views.add(winsView);
    views.add(drawsView);
    views.add(lossesView);
    views.add(penView);

    if (isRTL) Collections.reverse(views);
    for (View v : views) l.addView(v);
    return l;
}

    private View createDateHeaderView(String dateStr, int count, String lang, boolean showWeek, String weekNum) throws ParseException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout h = new LinearLayout(context);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setPadding(24, 12, 24, 12);
        h.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable dateHeaderBg = new GradientDrawable();
        dateHeaderBg.setColor(CLR_CARD);
        dateHeaderBg.setCornerRadius(dpToPx(12));
        if (Build.VERSION.SDK_INT >= 16) h.setBackground(dateHeaderBg); else h.setBackgroundDrawable(dateHeaderBg);
        if (Build.VERSION.SDK_INT >= 21) h.setElevation(dpToPx(4));
        LinearLayout.LayoutParams dateHeaderLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateHeaderLp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        h.setLayoutParams(dateHeaderLp);
        String dateText;
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Date matchDate = parser.parse(dateStr);
        Calendar matchCal = Calendar.getInstance();
        matchCal.setTime(matchDate);
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DATE, 1);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);
        tomorrow.set(Calendar.HOUR_OF_DAY, 0);
        tomorrow.set(Calendar.MINUTE, 0);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        matchCal.set(Calendar.HOUR_OF_DAY, 0);
        matchCal.set(Calendar.MINUTE, 0);
        matchCal.set(Calendar.SECOND, 0);
        matchCal.set(Calendar.MILLISECOND, 0);
        if (today.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "today", lang);
        } else if (yesterday.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "yesterday", lang);
        } else if (tomorrow.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "tomorrow", lang);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy", isRTL ? new Locale("ar") : Locale.US);
            dateText = sdf.format(matchDate);
        }
        TextView dl = createTextView(dateText, 0, 2, true);
        dl.setTextSize(12);
        dl.setTextColor(CLR_AQUA);
        String gamesText = (count == 1) ? getLocalizedText(null, "game_one", lang) : count + " " + getLocalizedText(null, "games", lang);
        TextView cl = createTextView(gamesText, 0, 1, true);
        cl.setTextSize(12);
        cl.setTextColor(CLR_AQUA);
        if (showWeek) {
            TextView wl = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, 0, 1, false);
            wl.setGravity(isRTL ? Gravity.END : Gravity.START);
            wl.setTextColor(CLR_AQUA);
            cl.setGravity(isRTL ? Gravity.START : Gravity.END);
            if (isRTL) {
                h.addView(cl);
                h.addView(dl);
                h.addView(wl);
            } else {
                h.addView(wl);
                h.addView(dl);
                h.addView(cl);
            }
        } else {
            cl.setGravity(isRTL ? Gravity.START : Gravity.END);
            TextView datePlaceholder = createTextView("", 0, 1, false);
            datePlaceholder.setTextColor(CLR_AQUA);
            if (isRTL) {
                h.addView(cl);
                h.addView(dl);
                h.addView(datePlaceholder);
            } else {
                h.addView(datePlaceholder);
                h.addView(dl);
                h.addView(cl);
            }
        }
        return h;
    }

    private View createCollapsibleDateSection(final String dateStr, int count, final String lang, boolean showWeek, String weekNum, java.util.Map<String, java.util.List<JSONObject>> byWeek, final JSONArray teams, boolean autoExpand) throws ParseException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);

        final LinearLayout sectionLayout = new LinearLayout(context);
        sectionLayout.setOrientation(LinearLayout.VERTICAL);
        sectionLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setVisibility(View.GONE);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(24, 12, 24, 12);
        // Use rounded card-style background for header
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6));
        headerRow.setLayoutParams(headerLp);
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(CLR_CARD);
        headerBg.setCornerRadius(dpToPx(12));
        if (Build.VERSION.SDK_INT >= 16) headerRow.setBackground(headerBg); else headerRow.setBackgroundDrawable(headerBg);
        if (Build.VERSION.SDK_INT >= 21) headerRow.setElevation(dpToPx(4));

        final TextView toggleSymbol = createTextView("▸", ViewGroup.LayoutParams.WRAP_CONTENT, 0, true);
        // Aqua symbol color
        toggleSymbol.setTextColor(CLR_AQUA);
        toggleSymbol.setTextSize(18);
        toggleSymbol.setPadding(dpToPx(8), 0, 0, 0);
        toggleSymbol.setTypeface(null, Typeface.BOLD);

        String dateText;
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Date matchDate = parser.parse(dateStr);
        Calendar matchCal = Calendar.getInstance();
        matchCal.setTime(matchDate);
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DATE, 1);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);
        tomorrow.set(Calendar.HOUR_OF_DAY, 0);
        tomorrow.set(Calendar.MINUTE, 0);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        matchCal.set(Calendar.HOUR_OF_DAY, 0);
        matchCal.set(Calendar.MINUTE, 0);
        matchCal.set(Calendar.SECOND, 0);
        matchCal.set(Calendar.MILLISECOND, 0);
        if (today.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "today", lang);
        } else if (yesterday.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "yesterday", lang);
        } else if (tomorrow.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "tomorrow", lang);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy", isRTL ? new Locale("ar") : Locale.US);
            dateText = sdf.format(matchDate);
        }

        TextView dl = createTextView(dateText, 0, 2, true);
        dl.setTextSize(12);
        dl.setTextColor(CLR_AQUA);
        String gamesText = (count == 1) ? getLocalizedText(null, "game_one", lang) : count + " " + getLocalizedText(null, "games", lang);
        TextView cl = createTextView(gamesText, 0, 1, true);
        cl.setTextSize(12);
        cl.setTextColor(CLR_AQUA);

        LinearLayout headerMiddle = new LinearLayout(context);
        headerMiddle.setOrientation(LinearLayout.HORIZONTAL);
        headerMiddle.setGravity(Gravity.CENTER_VERTICAL);
        headerMiddle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Determine whether to show the week number. If byWeek contains a single non-empty week
        // value (even when multiWeek is true due to odd keys), prefer showing it.
        String weekToShow = weekNum;
        java.util.Set<String> uniqueWeeks = new java.util.HashSet<String>();
        for (String wk : byWeek.keySet()) {
            if (wk == null) continue;
            String nw = wk.trim();
            if (nw.isEmpty() || nw.equalsIgnoreCase("null")) continue;
            uniqueWeeks.add(nw);
            if (weekToShow == null || weekToShow.isEmpty() || weekToShow.equalsIgnoreCase("null")) weekToShow = nw;
        }
        boolean showWeekComputed = showWeek || (uniqueWeeks.size() == 1 && !uniqueWeeks.iterator().next().isEmpty());

        if (showWeekComputed) {
            TextView wl = createTextView(getLocalizedText(null, "week", lang) + " " + weekToShow, 0, 1, false);
            wl.setGravity(isRTL ? Gravity.END : Gravity.START);
            wl.setTextColor(CLR_AQUA);
            cl.setGravity(isRTL ? Gravity.START : Gravity.END);
            if (isRTL) {
                headerMiddle.addView(cl);
                headerMiddle.addView(dl);
                headerMiddle.addView(wl);
            } else {
                headerMiddle.addView(wl);
                headerMiddle.addView(dl);
                headerMiddle.addView(cl);
            }
        } else {
            cl.setGravity(isRTL ? Gravity.START : Gravity.END);
            TextView datePlaceholder = createTextView("", 0, 1, false);
            // Match header text color for placeholder
            datePlaceholder.setTextColor(CLR_AQUA);
            if (isRTL) {
                headerMiddle.addView(cl);
                headerMiddle.addView(dl);
                headerMiddle.addView(datePlaceholder);
            } else {
                headerMiddle.addView(datePlaceholder);
                headerMiddle.addView(dl);
                headerMiddle.addView(cl);
            }
        }

        // Place toggle symbol on the right for LTR, left for RTL
        if (isRTL) {
            headerRow.addView(toggleSymbol, 0);
            headerRow.addView(headerMiddle);
        } else {
            headerRow.addView(headerMiddle);
            headerRow.addView(toggleSymbol);
        }

        // Expand/collapse with animation
        headerRow.setClickable(true);
        headerRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final int animDuration = 200;
                if (contentLayout.getVisibility() == View.VISIBLE) {
                    // collapse
                    final int initialHeight = contentLayout.getMeasuredHeight();
                    android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(initialHeight, 0);
                    va.setDuration(animDuration);
                    va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                            int val = (Integer) animation.getAnimatedValue();
                            ViewGroup.LayoutParams lp = contentLayout.getLayoutParams();
                            lp.height = val;
                            contentLayout.setLayoutParams(lp);
                        }
                    });
                    va.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            contentLayout.setVisibility(View.GONE);
                            ViewGroup.LayoutParams lp = contentLayout.getLayoutParams();
                            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            contentLayout.setLayoutParams(lp);
                        }
                    });
                    va.start();
                    toggleSymbol.setText("▸");
                } else {
                    // expand
                    contentLayout.setVisibility(View.VISIBLE);
                    // Measure target height
                    contentLayout.measure(View.MeasureSpec.makeMeasureSpec(((View) v.getParent()).getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    final int targetHeight = contentLayout.getMeasuredHeight();
                    ViewGroup.LayoutParams lp = contentLayout.getLayoutParams();
                    lp.height = 0;
                    contentLayout.setLayoutParams(lp);
                    android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(0, targetHeight);
                    va.setDuration(animDuration);
                    va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                            int val = (Integer) animation.getAnimatedValue();
                            ViewGroup.LayoutParams lp = contentLayout.getLayoutParams();
                            lp.height = val;
                            contentLayout.setLayoutParams(lp);
                        }
                    });
                    va.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            ViewGroup.LayoutParams lp = contentLayout.getLayoutParams();
                            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            contentLayout.setLayoutParams(lp);
                        }
                    });
                    va.start();
                    toggleSymbol.setText("▾");
                }
            }
        });

        sectionLayout.addView(headerRow);

        boolean multiWeek = byWeek.size() > 1;
        for (String week : byWeek.keySet()) {
            java.util.List<JSONObject> weekMatches = byWeek.get(week);
            if (weekMatches == null || weekMatches.isEmpty()) continue;

            if (multiWeek) {
                contentLayout.addView(createWeekHeaderView(week, lang));
            }

            java.util.Map<String, java.util.List<JSONObject>> byGroup = new java.util.LinkedHashMap<>();
            for (JSONObject match : weekMatches) {
                String group = match.optString("group", "_no_group_");
                if (group.isEmpty() || group.equals("null")) {
                    group = "_no_group_";
                }
                if (!byGroup.containsKey(group)) {
                    byGroup.put(group, new java.util.ArrayList<JSONObject>());
                }
                java.util.List<JSONObject> groupList = byGroup.get(group);
                if (groupList != null) groupList.add(match);
            }

            for (String gName : byGroup.keySet()) {
                if (!gName.equals("_no_group_")) {
                    contentLayout.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + gName, lang));
                }
                java.util.List<JSONObject> groupMatches = byGroup.get(gName);
                if (groupMatches != null) {
                    for (JSONObject match : groupMatches) {
                        try {
                            View matchView = createMatchItemView(match, teams, lang);
                            contentLayout.addView(matchView);
                        } catch (Exception e) {
                            // Ignore individual match errors
                        }
                    }
                }
            }
        }

        if (autoExpand) {
            contentLayout.setVisibility(View.VISIBLE);
            toggleSymbol.setText("▾");
        }

        sectionLayout.addView(contentLayout);
        return sectionLayout;
    }

    private View createWeekHeaderView(String weekNum, String lang) {
        TextView weekLabel = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, -1, 0, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dpToPx(10));
        bg.setStroke(dpToPx(1), CLR_BORDER);
        if (Build.VERSION.SDK_INT >= 16) weekLabel.setBackground(bg); else weekLabel.setBackgroundDrawable(bg);
        weekLabel.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        weekLabel.setTextSize(14);
        weekLabel.setGravity(Gravity.CENTER);
        weekLabel.setTextColor(CLR_AQUA);
        return weekLabel;
    }

    private View createListGroupHeaderView(String name, String lang) {
        TextView label = createTextView(name, -1, 0, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) label.setBackground(bg); else label.setBackgroundDrawable(bg);
        label.setTextColor(CLR_AQUA);
        label.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        label.setTextSize(16);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dpToPx(8), 0, dpToPx(12));
        label.setLayoutParams(p);
        return label;
    }

    private View createStyledGroupHeaderView(String name, String lang) {
        TextView label = createTextView(name, -1, 0, true);
        GradientDrawable bg = new GradientDrawable();
        // Dark blue background
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) label.setBackground(bg); else label.setBackgroundDrawable(bg);
        // Aqua text
        label.setTextColor(CLR_AQUA);
        label.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        label.setTextSize(16);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        // Increased spacing below group header
        p.setMargins(0, dpToPx(8), 0, dpToPx(16));
        label.setLayoutParams(p);
        return label;
    }

    private View createStyledDateHeader(String dateStr, int count, String lang, boolean showWeek, String weekNum) throws ParseException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout h = new LinearLayout(context);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setPadding(24, 12, 24, 12);
        h.setGravity(Gravity.CENTER_VERTICAL);
        
        // Rounded dark blue card background
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(CLR_CARD);
        cardBg.setCornerRadius(dpToPx(12));
        if (Build.VERSION.SDK_INT >= 16) h.setBackground(cardBg); else h.setBackgroundDrawable(cardBg);
        if (Build.VERSION.SDK_INT >= 21) h.setElevation(dpToPx(4));
        
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        h.setLayoutParams(hlp);
        
        String dateText;
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Date matchDate = parser.parse(dateStr);
        Calendar matchCal = Calendar.getInstance();
        matchCal.setTime(matchDate);
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DATE, 1);
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        yesterday.set(Calendar.HOUR_OF_DAY, 0);
        yesterday.set(Calendar.MINUTE, 0);
        yesterday.set(Calendar.SECOND, 0);
        yesterday.set(Calendar.MILLISECOND, 0);
        tomorrow.set(Calendar.HOUR_OF_DAY, 0);
        tomorrow.set(Calendar.MINUTE, 0);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        matchCal.set(Calendar.HOUR_OF_DAY, 0);
        matchCal.set(Calendar.MINUTE, 0);
        matchCal.set(Calendar.SECOND, 0);
        matchCal.set(Calendar.MILLISECOND, 0);
        if (today.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "today", lang);
        } else if (yesterday.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "yesterday", lang);
        } else if (tomorrow.getTime().equals(matchCal.getTime())) {
            dateText = getLocalizedText(null, "tomorrow", lang);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy", isRTL ? new Locale("ar") : Locale.US);
            dateText = sdf.format(matchDate);
        }
        
        TextView dl = createTextView(dateText, 0, 2, true);
        dl.setTextSize(12);
        dl.setTextColor(CLR_AQUA);
        String gamesText = (count == 1) ? getLocalizedText(null, "game_one", lang) : count + " " + getLocalizedText(null, "games", lang);
        TextView cl = createTextView(gamesText, 0, 1, true);
        cl.setTextSize(12);
        cl.setTextColor(CLR_AQUA);
        
        if (showWeek) {
            TextView wl = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, 0, 1, false);
            wl.setGravity(isRTL ? Gravity.END : Gravity.START);
            wl.setTextColor(CLR_AQUA);
            cl.setGravity(isRTL ? Gravity.START : Gravity.END);
            if (isRTL) {
                h.addView(cl);
                h.addView(dl);
                h.addView(wl);
            } else {
                h.addView(wl);
                h.addView(dl);
                h.addView(cl);
            }
        } else {
            cl.setGravity(isRTL ? Gravity.START : Gravity.END);
            TextView datePlaceholder = createTextView("", 0, 1, false);
            datePlaceholder.setTextColor(CLR_AQUA);
            if (isRTL) {
                h.addView(cl);
                h.addView(dl);
                h.addView(datePlaceholder);
            } else {
                h.addView(datePlaceholder);
                h.addView(dl);
                h.addView(cl);
            }
        }
        return h;
    }

    private View createMatchItemView(JSONObject match, JSONArray teams, final String lang) throws JSONException, ParseException {
    // Add null checks
    if (match == null) {
        TextView errorView = new TextView(context);
        errorView.setText("Match data unavailable");
        return errorView;
    }
    
    if (teams == null || teams.length() == 0) {
        TextView errorView = new TextView(context);
        errorView.setText("Team data unavailable");
        return errorView;
    }
    
    
    final boolean isRTL = "ar".equalsIgnoreCase(lang);
    final String matchId = match.getString("match_id");
    
    // Get team IDs for reference (but not used for clicking)
    final String homeTeamId = match.getString("home_team_id");
    final String awayTeamId = match.getString("away_team_id");
    
    LinearLayout card = new LinearLayout(context);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    cp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16));
    card.setLayoutParams(cp);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
    GradientDrawable bg = createCardBackground(CLR_CARD, this.dividerColor, dpToPx(18));
    if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg);
    else card.setBackgroundDrawable(bg);
    if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(6));

    // Card click for match details (this is what you want to keep)
    card.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MatchClicked(matchId);
        }
    });

    TextView top = createTextView("", -1, 0, false);
    top.setTextSize(12);
    top.setTextColor(CLR_TEAL);
    top.setGravity(Gravity.CENTER);

    LinearLayout mid = new LinearLayout(context);
    mid.setOrientation(LinearLayout.HORIZONTAL);
    mid.setGravity(Gravity.CENTER_VERTICAL);
    mid.setPadding(0, dpToPx(8), 0, dpToPx(8));
    TextView center = createTextView("", -2, 0, true);
    center.setTextSize(20);
    center.setTextColor(Color.WHITE);
    center.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);

    // Create container for venue and note
    LinearLayout infoContainer = new LinearLayout(context);
    infoContainer.setOrientation(LinearLayout.VERTICAL);
    infoContainer.setGravity(Gravity.CENTER);
    infoContainer.setPadding(0, dpToPx(8), 0, dpToPx(4));

    TextView venueText = createTextView("", -1, 0, false);
    venueText.setTextSize(12);
    venueText.setTextColor(CLR_TEAL);
    venueText.setGravity(Gravity.CENTER);
    venueText.setPadding(0, dpToPx(2), 0, dpToPx(2));

    TextView noteText = createTextView("", -1, 0, false);
    noteText.setTextSize(12);
    noteText.setTextColor(CLR_AQUA);
    noteText.setGravity(Gravity.CENTER);
    noteText.setPadding(0, dpToPx(2), 0, dpToPx(2));

    infoContainer.addView(venueText);
    infoContainer.addView(noteText);

    // ✅ Use NON-CLICKABLE team layout (regular createTeamLayout)
    LinearLayout homeLayout = createTeamLayout(getTeamInfoById(homeTeamId, teams), lang);
    LinearLayout awayLayout = createTeamLayout(getTeamInfoById(awayTeamId, teams), lang);
    
    if (isRTL) {
        mid.addView(awayLayout);
        mid.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
        mid.addView(homeLayout);
    } else {
        mid.addView(homeLayout);
        mid.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
        mid.addView(awayLayout);
    }
    
    String status = match.optString("status", "upcoming").toLowerCase();
    String venue = getLocalizedText(match, "venue", lang);
    String note = getLocalizedText(match, "note", lang);
    
    if ("completed".equals(status)) {
        top.setText(getLocalizedText(null, "completed", lang));
        String score = isRTL ? String.format(Locale.US, "%d : %d", match.getInt("away_score"), match.getInt("home_score")) : String.format(Locale.US, "%d : %d", match.getInt("home_score"), match.getInt("away_score"));
        center.setText(score);
        
        if (!venue.isEmpty()) {
            venueText.setText(venue);
            venueText.setVisibility(View.VISIBLE);
        } else {
            venueText.setVisibility(View.GONE);
        }
        
        if (!note.isEmpty()) {
            noteText.setText(note);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
        
        if (venueText.getVisibility() == View.GONE && noteText.getVisibility() == View.GONE) {
            infoContainer.setVisibility(View.GONE);
        } else {
            infoContainer.setVisibility(View.VISIBLE);
        }
        
    } else if ("postponed".equals(status) || "delayed".equals(status)) {
        top.setText(getLocalizedText(null, status, lang));
        center.setText(match.optString("time", "-"));
        
        if (!venue.isEmpty()) {
            venueText.setText(venue);
            venueText.setVisibility(View.VISIBLE);
        } else {
            venueText.setVisibility(View.GONE);
        }
        
        if (!note.isEmpty()) {
            noteText.setText(note);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
        
        if (venueText.getVisibility() == View.GONE && noteText.getVisibility() == View.GONE) {
            infoContainer.setVisibility(View.GONE);
        } else {
            infoContainer.setVisibility(View.VISIBLE);
        }
        
    } else {
        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(match.getString("date"));
        top.setText(new SimpleDateFormat("dd MMM", isRTL ? new Locale("ar") : Locale.US).format(date));
        center.setText(match.optString("time", "-"));
        
        if (!venue.isEmpty()) {
            venueText.setText(venue);
            venueText.setTextColor(CLR_TEAL);
            venueText.setVisibility(View.VISIBLE);
        } else {
            venueText.setText(getLocalizedText(null, "venue_not_available", lang));
            venueText.setTextColor(CLR_TEAL);
            venueText.setVisibility(View.VISIBLE);
        }
        
        if (!note.isEmpty()) {
            noteText.setText(note);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
        
        infoContainer.setVisibility(View.VISIBLE);
    }
    
    card.addView(top);
    card.addView(mid);
    card.addView(infoContainer);
    
    return card;
}

private View createMatchDetailHeaderView(JSONObject match, JSONArray teams, final String lang) throws JSONException, ParseException {
    // ✅ ADD NULL CHECKS HERE - at the very top
    if (match == null) {
        android.util.Log.e("FootballSuite", "createMatchDetailHeaderView: match is null");
        TextView errorView = new TextView(context);
        errorView.setText("Error: Match data not available");
        errorView.setTextColor(this.secondaryTextColor);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        return errorView;
    }
    
    if (teams == null) {
        android.util.Log.e("FootballSuite", "createMatchDetailHeaderView: teams array is null");
        TextView errorView = new TextView(context);
        errorView.setText("Error: Team data not available");
        errorView.setTextColor(this.secondaryTextColor);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        return errorView;
    }
    
    if (lang == null) {
        android.util.Log.e("FootballSuite", "createMatchDetailHeaderView: language is null, using English");
        // Continue with English as fallback
    }
    
    final boolean isRTL = "ar".equalsIgnoreCase(lang);
    final String matchId = match.getString("match_id");
    final String homeTeamId = match.getString("home_team_id");
    final String awayTeamId = match.getString("away_team_id");
    
    LinearLayout card = new LinearLayout(context);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
    cp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16));
    card.setLayoutParams(cp);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
    GradientDrawable bg = createCardBackground(CLR_CARD, this.dividerColor, dpToPx(18));
    if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg);
    else card.setBackgroundDrawable(bg);
    if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(6));

    // No card click - only team clicks matter in header

    TextView top = createTextView("", -1, 0, false);
    top.setTextSize(12);
    top.setTextColor(CLR_TEAL);
    top.setGravity(Gravity.CENTER);

    LinearLayout mid = new LinearLayout(context);
    mid.setOrientation(LinearLayout.HORIZONTAL);
    mid.setGravity(Gravity.CENTER_VERTICAL);
    mid.setPadding(0, dpToPx(8), 0, dpToPx(8));
    TextView center = createTextView("", -2, 0, true);
    center.setTextSize(20);
    center.setTextColor(Color.WHITE);
    center.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);

    // Create container for venue and note
    LinearLayout infoContainer = new LinearLayout(context);
    infoContainer.setOrientation(LinearLayout.VERTICAL);
    infoContainer.setGravity(Gravity.CENTER);
    infoContainer.setPadding(0, dpToPx(8), 0, dpToPx(4));

    TextView venueText = createTextView("", -1, 0, false);
    venueText.setTextSize(12);
    venueText.setTextColor(CLR_TEAL);
    venueText.setGravity(Gravity.CENTER);
    venueText.setPadding(0, dpToPx(2), 0, dpToPx(2));

    TextView noteText = createTextView("", -1, 0, false);
    noteText.setTextSize(12);
    noteText.setTextColor(CLR_AQUA);
    noteText.setGravity(Gravity.CENTER);
    noteText.setPadding(0, dpToPx(2), 0, dpToPx(2));

    infoContainer.addView(venueText);
    infoContainer.addView(noteText);

    // ✅ Use CLICKABLE team layout for match detail header
    LinearLayout homeLayout = createTeamLayoutWithClick(getTeamInfoById(homeTeamId, teams), lang, homeTeamId);
    LinearLayout awayLayout = createTeamLayoutWithClick(getTeamInfoById(awayTeamId, teams), lang, awayTeamId);
    
    if (isRTL) {
        mid.addView(awayLayout);
        mid.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
        mid.addView(homeLayout);
    } else {
        mid.addView(homeLayout);
        mid.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
        mid.addView(awayLayout);
    }
    
    String status = match.optString("status", "upcoming").toLowerCase();
    String venue = getLocalizedText(match, "venue", lang);
    String note = getLocalizedText(match, "note", lang);
    
    if ("completed".equals(status)) {
        top.setText(getLocalizedText(null, "completed", lang));
        String score = isRTL ? String.format(Locale.US, "%d : %d", match.getInt("away_score"), match.getInt("home_score")) : String.format(Locale.US, "%d : %d", match.getInt("home_score"), match.getInt("away_score"));
        center.setText(score);
        
        if (!venue.isEmpty()) {
            venueText.setText(venue);
            venueText.setVisibility(View.VISIBLE);
        } else {
            venueText.setVisibility(View.GONE);
        }
        
        if (!note.isEmpty()) {
            noteText.setText(note);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
        
        if (venueText.getVisibility() == View.GONE && noteText.getVisibility() == View.GONE) {
            infoContainer.setVisibility(View.GONE);
        } else {
            infoContainer.setVisibility(View.VISIBLE);
        }
        
    } else if ("postponed".equals(status) || "delayed".equals(status)) {
        top.setText(getLocalizedText(null, status, lang));
        center.setText(match.optString("time", "-"));
        
        if (!venue.isEmpty()) {
            venueText.setText(venue);
            venueText.setVisibility(View.VISIBLE);
        } else {
            venueText.setVisibility(View.GONE);
        }
        
        if (!note.isEmpty()) {
            noteText.setText(note);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
        
        if (venueText.getVisibility() == View.GONE && noteText.getVisibility() == View.GONE) {
            infoContainer.setVisibility(View.GONE);
        } else {
            infoContainer.setVisibility(View.VISIBLE);
        }
        
    } else {
        Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(match.getString("date"));
        top.setText(new SimpleDateFormat("dd MMM", isRTL ? new Locale("ar") : Locale.US).format(date));
        center.setText(match.optString("time", "-"));
        
        if (!venue.isEmpty()) {
            venueText.setText(venue);
            venueText.setTextColor(CLR_TEAL);
            venueText.setVisibility(View.VISIBLE);
        } else {
            venueText.setText(getLocalizedText(null, "venue_not_available", lang));
            venueText.setTextColor(CLR_TEAL);
            venueText.setVisibility(View.VISIBLE);
        }
        
        if (!note.isEmpty()) {
            noteText.setText(note);
            noteText.setVisibility(View.VISIBLE);
        } else {
            noteText.setVisibility(View.GONE);
        }
        
        infoContainer.setVisibility(View.VISIBLE);
    }
    
    card.addView(top);
    card.addView(mid);
    card.addView(infoContainer);
    
    return card;
}
    
    // Create collapsible group header with arrow indicator
    private LinearLayout createCollapsibleGroupHeader(String title, String lang, boolean isExpanded) {
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    GradientDrawable headerBg = new GradientDrawable();
    headerBg.setColor(CLR_CARD);
    headerBg.setCornerRadius(dpToPx(10));
    if (Build.VERSION.SDK_INT >= 16) header.setBackground(headerBg); else header.setBackgroundDrawable(headerBg);
    header.setPadding(dpToPx(20), dpToPx(14), dpToPx(20), dpToPx(14));
    LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
    headerParams.setMargins(0, 0, 0, dpToPx(12));
    header.setLayoutParams(headerParams);

    // Group title text
    TextView titleView = new TextView(context);
    titleView.setText(title);
    titleView.setTextSize(16);
    titleView.setTypeface(null, Typeface.BOLD);
    titleView.setTextColor(CLR_AQUA);
    titleView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    titleView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);

    // Arrow indicator (▼ for expanded, ▶ for collapsed)
    TextView arrowView = new TextView(context);
    arrowView.setText(isExpanded ? "▼" : "▶");
    arrowView.setTextSize(18);
    arrowView.setTextColor(CLR_AQUA);
    arrowView.setPadding(dpToPx(8), 0, dpToPx(8), 0);
    arrowView.setTag(isExpanded ? "expanded" : "collapsed");
    
    // Add views in correct RTL order
    if (isRTL) {
        header.addView(arrowView);
        header.addView(titleView);
    } else {
        header.addView(titleView);
        header.addView(arrowView);
    }
    
    // Store arrow reference in header's tag for later updates
    header.setTag(arrowView);
    
    return header;
}




    private LinearLayout createTeamLayout(JSONObject tInfo, String lang) throws JSONException {
    LinearLayout l = new LinearLayout(context);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setGravity(Gravity.CENTER_HORIZONTAL);
    l.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 2));
    
    ImageView logo = new ImageView(context);
    logo.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
    if (tInfo != null) Picasso.with(context).load(tInfo.optString("logo")).into(logo);
    
    TextView name = createTextView(getLocalizedText(tInfo, "name", lang), -2, 0, true);
    name.setTextSize(14);
    name.setTextColor(Color.WHITE);
    name.setPadding(0, 8, 0, 0);
    
    l.addView(logo);
    l.addView(name);
    
    return l;
}

    private LinearLayout createTeamLayoutWithClick(JSONObject tInfo, String lang, final String teamId) throws JSONException {
    LinearLayout l = new LinearLayout(context);
    l.setOrientation(LinearLayout.VERTICAL);
    l.setGravity(Gravity.CENTER_HORIZONTAL);
    l.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 2));
    
    // Make the entire team layout clickable
    l.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            TeamClicked(teamId);
        }
    });
    
    ImageView logo = new ImageView(context);
    logo.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
    if (tInfo != null) {
        String logoUrl = tInfo.optString("logo");
        if (!logoUrl.isEmpty()) {
            Picasso.with(context).load(logoUrl).into(logo);
        }
    }
    
    TextView name = createTextView(getLocalizedText(tInfo, "name", lang), -2, 0, true);
    name.setTextSize(14);
    name.setTextColor(Color.WHITE);
    name.setPadding(0, 8, 0, 0);
    
    l.addView(logo);
    l.addView(name);
    
    return l;
}

    private String getLocalizedText(JSONObject o, String key, String lang) {
        // Add null check for key
    if (key == null || key.isEmpty()) {
        return "";
    }
        
        
        if (o == null) {
            boolean isAR = "ar".equalsIgnoreCase(lang);
             if ("venue_not_available".equals(key)) return isAR ? "الملعب غير محدد" : "Venue not available";
            if ("today".equals(key)) return isAR ? "اليوم" : "Today";
            if ("yesterday".equals(key)) return isAR ? "أمس" : "Yesterday";
            if ("tomorrow".equals(key)) return isAR ? "غدا" : "Tomorrow";
            if ("team".equals(key)) return isAR ? "الفريق" : "Team";
            if ("pts".equals(key)) return isAR ? "نقاط" : "Points";
            if ("p".equals(key)) return isAR ? "لعب" : "P";
            if ("w".equals(key)) return isAR ? "ف" : "W";
            if ("d".equals(key)) return isAR ? "ت" : "D";
            if ("l".equals(key)) return isAR ? "ه" : "L";
            if ("f:a".equals(key)) return isAR ? "له/عليه" : "F:A";
            if ("gd".equals(key)) return isAR ? "فارق" : "+/-";
            if ("p.p".equals(key)) return isAR ? "ر.ت" : "P.P";
            if ("week".equals(key)) return isAR ? "الجولة" : "Round";
            if ("games".equals(key)) return isAR ? "مباريات" : "Games";
            if ("game_one".equals(key)) return isAR ? "مباراة واحدة" : "1 Game";
            if ("completed".equals(key)) return isAR ? "انتهت" : "Completed";
            if ("postponed".equals(key)) return isAR ? "مؤجلة" : "Postponed";
            if ("delayed".equals(key)) return isAR ? "تأجلت" : "Delayed";
            if ("group".equals(key)) return isAR ? "المجموعة" : "Group";
            if ("search".equals(key)) return isAR ? "ابحث..." : "Search...";
            if ("city".equals(key)) return isAR ? "المنطقة" : "City";
            if ("field".equals(key)) return isAR ? "الملعب" : "Field";
            if ("information".equals(key)) return isAR ? "معلومات" : "Information";
            if ("coach".equals(key)) return isAR ? "الادارة الفنية" : "Coach";
            if ("goalkeepers".equals(key)) return isAR ? "حراس المرمي" : "Goalkeepers";
            if ("defenders".equals(key)) return isAR ? "المدافعون" : "Defenders";
            if ("midfielders".equals(key)) return isAR ? "لاعبو الوسط" : "Midfielders";
            if ("attackers".equals(key)) return isAR ? "المهاجمون" : "Attackers";
            if ("player".equals(key)) return isAR ? "اللاعب" : "Player";
            if ("goals".equals(key)) return isAR ? "الأهداف" : "Goals";
            if ("assists".equals(key)) return isAR ? "صناعة الأهداف" : "Assists";
            if ("clean_sheets".equals(key)) return isAR ? "شباك نظيفة" : "Clean Sheets";
            if ("lineup".equals(key)) return isAR ? "تشكيل الفريق" : "Lineup";
            if ("scorers_list".equals(key)) return isAR ? "مسجلي الأهداف" : "Match Scorers";
            if ("yellow_cards".equals(key)) return isAR ? "البطاقات الصفراء" : "Yellow Cards";
            if ("red_cards".equals(key)) return isAR ? "البطاقات الحمراء" : "Red Cards";
            if ("substitutions".equals(key)) return isAR ? "التبديلات" : "Substitutions";
            if ("assists_delimiter".equals(key)) return isAR ? "صناعة الاهداف" : "Assists";
            if ("game_short".equals(key)) return isAR ? "م" : "M";
            if ("close".equals(key)) return isAR ? "إغلاق" : "Close";
            if ("total_goals".equals(key)) return isAR ? "إجمالي الأهداف" : "Total Goals";
            if ("vs".equals(key)) return isAR ? "ضد" : "vs";
            if ("no_goals_found".equals(key)) return isAR ? "لم يتم تسجيل أهداف" : "No goals found";
            if ("no_stats_found".equals(key)) return isAR ? "لا توجد إحصائيات" : "No statistics found";
            if ("no_data_available".equals(key)) return isAR ? "لا توجد بيانات" : "No data available";
            return (key != null) ? key : "";
            
        }
        if (!o.has(key) || o.isNull(key)) return "";
        try {
            Object v = o.get(key);
            if (v instanceof String) return (String) v;
            if (v instanceof JSONObject) {
                JSONObject lObj = (JSONObject) v;
                if (lObj.has(lang)) return lObj.getString(lang);
                return lObj.optString("en");
            }
            if (v instanceof JSONArray) {
                JSONArray arr = (JSONArray) v;
                if (arr.length() > 0) return arr.getString(0);
            }
            return v.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    private String getEnglishKeyFor(String title, String lang) {
        if ("ar".equalsIgnoreCase(lang)) {
            if (title.equals("المنطقة")) return "city";
            if (title.equals("الملعب")) return "field";
            if (title.equals("معلومات")) return "information";
        }
        return title.toLowerCase();
    }

    private JSONObject getTeamInfoById(String id, JSONArray data) throws JSONException {
        // Add null checks
    if (id == null || id.isEmpty()) {
        android.util.Log.e("FootballSuite", "getTeamInfoById: team ID is null or empty");
        return null;
    }
    
    if (data == null || data.length() == 0) {
        android.util.Log.e("FootballSuite", "getTeamInfoById: teams data is null or empty");
        return null;
    }
    
    // Check cache first
    if (cachedTeamsById.containsKey(id)) {
        return cachedTeamsById.get(id);
    }
        
        for (int i = 0; i < data.length(); i++) {
            if (data.getJSONObject(i).getString("team_id").equals(id)) {
                JSONObject team = data.getJSONObject(i);
                cachedTeamsById.put(id, team);
                return team;
            }
        }
        return null;
    }

    private String getMatchGroupName(JSONObject match) throws JSONException {
        String group = match.optString("group", null);
        if (group != null && !group.isEmpty() && !group.equalsIgnoreCase("null")) return group;
        JSONObject teamInfo = getTeamInfoById(match.getString("home_team_id"), jsonData.optJSONArray("teams"));
        if (teamInfo != null && teamInfo.has("group") && !teamInfo.isNull("group")) {
            String teamGroup = teamInfo.getString("group");
            if (teamGroup != null && !teamGroup.isEmpty()) return teamGroup;
        }
        return null;
    }

    private void addInfoRow(LinearLayout c, String title, String value, final String url, String lang) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) return;
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout r = new LinearLayout(context);
        r.setPadding(32, 24, 32, 24);
        
        int textAlignment = isRTL ? Gravity.RIGHT : Gravity.LEFT;

        if ("information".equals(getEnglishKeyFor(title, lang))) {
            r.setOrientation(LinearLayout.VERTICAL);
            
            TextView tv = createTextView(title, -1, 0, true);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(CLR_AQUA);

            TextView vv = createTextView(value, -1, 0, false);
            vv.setGravity(textAlignment);
            vv.setPadding(0, 16, 0, 0);
            vv.setTextColor(Color.WHITE);

            r.addView(tv);
            r.addView(vv);
        } else {
            r.setOrientation(LinearLayout.HORIZONTAL);

            TextView tv = createTextView(title, 0, 1, true);
            tv.setGravity(textAlignment | Gravity.CENTER_VERTICAL);
            tv.setTextColor(CLR_AQUA);

            TextView vv = createTextView(value, 0, 2, false);
            vv.setGravity(textAlignment | Gravity.CENTER_VERTICAL);
            vv.setTextColor(Color.WHITE);
            
            if (url != null && !url.isEmpty() && !"null".equalsIgnoreCase(url)) {
                vv.setTextColor(Color.BLUE);
                r.setOnClickListener(new View.OnClickListener() { 
                    @Override 
                    public void onClick(View v) { 
                        try { 
                            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); 
                        } catch (Exception e) { /* ignore */ } 
                    } 
                });
            }

            if (isRTL) {
                r.addView(vv);
                r.addView(tv);
            } else {
                r.addView(tv);
                r.addView(vv);
            }
        }
        c.addView(r); 
    }

    private void addPlayerSection(LinearLayout c, JSONObject players, String key, String lang) {
        String title = getLocalizedText(null, key, lang);
        if (!players.has(key) || players.isNull(key)) return;

        StringBuilder playerListBuilder = new StringBuilder();
        Object rawData = players.opt(key);

        if (rawData instanceof JSONArray) {
            JSONArray arr = (JSONArray) rawData;
            for (int i = 0; i < arr.length(); i++) {
                String pName = arr.optString(i);
                if (!pName.isEmpty()) {
                    if (playerListBuilder.length() > 0) playerListBuilder.append("\n");
                    playerListBuilder.append(pName);
                }
            }
        } else {
            String s = getLocalizedText(players, key, lang);
            playerListBuilder.append(s.replaceAll("[\"\\[\\]\\\\]", "").replace(",", "\n"));
        }

        String pList = playerListBuilder.toString();
        if (pList.trim().isEmpty()) return;

        TextView tv = createTextView(title, -1, 0, true);
        tv.setTextSize(16);
        tv.setTextColor(CLR_AQUA);
        tv.setPadding(32, 24, 32, 8);
        tv.setGravity(Gravity.START);
        TextView pv = createTextView(pList, -1, 0, false);
        pv.setTextColor(Color.WHITE);
        pv.setPadding(32, 16, 32, 24);
        pv.setGravity(Gravity.START);
        c.addView(tv);
        c.addView(pv);
    }

    private TextView createTextView(String text, int width, float weight, boolean isBold) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, -2, weight);
        if (width == 0) p.width = 0;
        p.setMargins(8, 0, 8, 0);
        tv.setLayoutParams(p);
        tv.setText(text);
        tv.setTextColor(this.primaryTextColor);
        tv.setGravity(Gravity.CENTER);
        if (isBold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private View createEventHeader(String title, String iconFilename, String lang) {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER);
        headerLayout.setPadding(0, dpToPx(8), 0, dpToPx(8));
        if (isRTL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            headerLayout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView icon = new ImageView(context);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)));
        try (InputStream is = context.getAssets().open(iconFilename)) {
            Drawable d = Drawable.createFromStream(is, null);
            icon.setImageDrawable(d);
        } catch (Exception e) {
            // Ignore error
        }
        TextView titleView = createTextView(title, ViewGroup.LayoutParams.WRAP_CONTENT, 0, true);
        titleView.setTextColor(CLR_AQUA);
        titleView.setTextSize(16);
        LinearLayout.LayoutParams titleParams = (LinearLayout.LayoutParams) titleView.getLayoutParams();
        titleParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);
        headerLayout.addView(icon);
        headerLayout.addView(titleView);
        return headerLayout;
    }

    private View createPlayerCardView(String text) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        card.setLayoutParams(params);
        card.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(this.cardBackgroundColor);
        bg.setCornerRadius(dpToPx(8));
        bg.setStroke(dpToPx(1), this.dividerColor);
        card.setBackground(bg);
        TextView tv = createTextView(text, -1, 0, false);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(16);
        card.addView(tv);
        return card;
    }

    private JSONObject findObjectById(JSONArray array, String key, String value) throws JSONException {
        if (array == null) return null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj.optString(key).equals(value)) return obj;
        }
        return null;
    }

    private JSONObject findObjectInSeasons(JSONObject mainJson, String key, String value) throws JSONException {
        if (mainJson == null) return null;
        Object seasonsObj = mainJson.opt("seasons");
        if (seasonsObj instanceof JSONArray) {
            return findObjectById((JSONArray) seasonsObj, key, value);
        } else if (seasonsObj instanceof JSONObject) {
            JSONObject season = (JSONObject) seasonsObj;
            if (season.optString(key).equals(value)) return season;
        }
        return null;
    }

    private JSONObject findCompetitionById(String competitionId) throws JSONException {
        Object seasonsObj = jsonData.opt("seasons");
        if (seasonsObj instanceof JSONArray) {
            JSONArray seasons = (JSONArray) seasonsObj;
            for (int i = 0; i < seasons.length(); i++) {
                JSONArray competitions = seasons.getJSONObject(i).optJSONArray("competitions");
                if (competitions != null) {
                    JSONObject comp = findObjectById(competitions, "competition_id", competitionId);
                    if (comp != null) return comp;
                }
            }
        } else if (seasonsObj instanceof JSONObject) {
            JSONArray competitions = ((JSONObject) seasonsObj).optJSONArray("competitions");
            if (competitions != null) return findObjectById(competitions, "competition_id", competitionId);
        }
        return null;
    }

    private RelativeLayout createAdCardView(JSONObject ad) {
        RelativeLayout adLayout = new RelativeLayout(context);
        adLayout.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
        adLayout.setBackgroundColor(this.headerBackgroundColor);
        ImageView adImage = new ImageView(context);
        RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(-1, -2);
        imageParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        adImage.setLayoutParams(imageParams);
        adImage.setAdjustViewBounds(true);
        adImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Picasso.with(context).load(ad.optString("image")).into(adImage);
        adLayout.addView(adImage);
        TextView adName = new TextView(context);
        adName.setText(getLocalizedText(ad, "name", "ar"));
        adName.setTextSize(18);
        adName.setTypeface(null, Typeface.BOLD);
        adName.setTextColor(this.primaryTextColor);
        adName.setGravity(Gravity.CENTER);
        adName.setBackgroundColor(this.overlayBackgroundColor);
        adName.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        RelativeLayout.LayoutParams nameParams = new RelativeLayout.LayoutParams(-1, -2);
        nameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        adLayout.addView(adName, nameParams);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBackgroundColor(this.overlayBackgroundColor);
        actions.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        RelativeLayout.LayoutParams actionParams = new RelativeLayout.LayoutParams(-1, -2);
        actionParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        adLayout.addView(actions, actionParams);
        String locationUrl = ad.optString("location_url", null);
        if (isValid(locationUrl)) {
            LinearLayout locGroup = new LinearLayout(context);
            locGroup.setOrientation(LinearLayout.HORIZONTAL);
            locGroup.setGravity(Gravity.CENTER_VERTICAL);
            locGroup.setPadding(dpToPx(8), 0, dpToPx(8), 0);
            locGroup.setOnClickListener(createLinkClickListener(locationUrl));
            locGroup.addView(createIcon("location_icon.png"));
            TextView locText = new TextView(context);
            locText.setText(getLocalizedText(ad, "location", "ar"));
            locText.setTextColor(this.primaryTextColor);
            locText.setTextSize(14);
            locText.setPadding(dpToPx(4), 0, 0, 0);
            locGroup.addView(locText);
            actions.addView(locGroup);
        }
        LinearLayout iconGroup = new LinearLayout(context);
        iconGroup.setOrientation(LinearLayout.HORIZONTAL);
        iconGroup.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        iconGroup.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        addClickableIcon(iconGroup, ad, "youtube_video", "yt_icon.png", null);
        addClickableIcon(iconGroup, ad, "facebook_link", "fb_icon.png", null);
        addClickableIcon(iconGroup, ad, "mobile_number", "phone_icon.png", "tel:");
        addClickableIcon(iconGroup, ad, "whatsapp_number", "whatsapp_icon.png", "https://wa.me/");
        actions.addView(iconGroup);
        return adLayout;
    }

    private void addClickableIcon(ViewGroup parent, JSONObject source, String key, String iconName, String uriPrefix) {
        String value = source.optString(key, null);
        if (isValid(value)) {
            ImageView icon = createIcon(iconName);
            parent.addView(icon);
            final String finalValue = (uriPrefix != null) ? uriPrefix + value : value;
            icon.setOnClickListener(createLinkClickListener(finalValue));
        }
    }

    private boolean isValid(String value) {
        return value != null && !value.isEmpty() && !value.equalsIgnoreCase("null");
    }

    private View.OnClickListener createLinkClickListener(final String url) {
        return new View.OnClickListener() { @Override public void onClick(View v) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Log.w("FootballSuite", "URL launch failed: " + url, e);
            }
        }};
    }

    private ImageView createIcon(String assetName) {
        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        params.setMargins(dpToPx(8), 0, dpToPx(8), 0);
        icon.setLayoutParams(params);
        try (InputStream is = form.openAsset(assetName)) {
            Drawable d = Drawable.createFromStream(is, null);
            icon.setImageDrawable(d);
        } catch (java.io.IOException e) {
            // Ignore error
        }
        return icon;
    }

    private void createSearchableListView(HVArrangement container, final String language, final String type, final JSONArray dataArray, final String competitionId, final String age) {
        ViewGroup vg = (ViewGroup) container.getView();
        vg.removeAllViews();

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        EditText searchBar = new EditText(context);
        searchBar.setHint(getLocalizedText(null, "search", language));
        searchBar.setTextColor(CLR_AQUA);
        searchBar.setHintTextColor(CLR_HINT);
        searchBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        GradientDrawable slSearchBg = new GradientDrawable();
        slSearchBg.setColor(CLR_CARD);
        slSearchBg.setCornerRadius(dpToPx(12));
        slSearchBg.setStroke(dpToPx(1), CLR_AQUA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            searchBar.setBackground(slSearchBg);
        } else {
            searchBar.setBackgroundDrawable(slSearchBg);
        }
        searchBar.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8));
        searchBar.setLayoutParams(searchParams);

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setClipToPadding(false);
        recyclerView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final SearchableListAdapter adapter = new SearchableListAdapter(dataArray, language, type, competitionId, age, null);
        recyclerView.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        mainLayout.addView(searchBar);
        mainLayout.addView(recyclerView);
        vg.addView(mainLayout);
    }

    private void buildClickableListView(LinearLayout container, JSONArray dataArray, String language, String filter, final String type, final String competitionId, final String age) {
        container.removeAllViews();
        String filterLower = filter.toLowerCase();
        try {
            for (int i = 0; i < dataArray.length(); i++) {
                final JSONObject item = dataArray.getJSONObject(i);
                final String name;
                if (type.equals("age")) {
                    name = item.optString("age");
                } else if (type.equals("sector")) {
                    name = item.optString("name");
                } else if (type.equals("season")) {
                    name = item.optString("season");
                } else if (type.equals("venue")) {
                    String venueName = item.optString("name");
                    if (venueName == null || venueName.isEmpty()) {
                        venueName = getLocalizedText(item, "venue", language);
                    }
                    name = venueName;
                } else {
                    name = getLocalizedText(item, "name", language);
                }
                if (name.toLowerCase().contains(filterLower)) {
                    LinearLayout itemCard = new LinearLayout(context);
                    itemCard.setOrientation(LinearLayout.VERTICAL);
                    itemCard.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                    LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    itemParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
                    itemCard.setLayoutParams(itemParams);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        itemCard.setBackground(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
                    } else {
                        itemCard.setBackgroundDrawable(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        itemCard.setElevation(dpToPx(4));
                    }

                    TextView itemView = new TextView(context);
                    itemView.setText(name);
                    itemView.setTextColor(CLR_AQUA);
                    itemView.setTextSize(16);
                    itemView.setTypeface(null, Typeface.BOLD);
                    itemView.setGravity(Gravity.CENTER);
                    itemCard.addView(itemView);

                    itemCard.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                        try {
                            if (type.equals("season")) {
                                SeasonClicked(name);
                            } else if (type.equals("competition")) {
                                String id = item.getString("competition_id");
                                boolean hasAges = item.optJSONArray("ages") != null;
                                CompetitionClicked(id, name, hasAges);
                            } else if (type.equals("age")) {
                                String ageValue = item.getString("age");
                                String url = item.optString("matchesurl", "");
                                boolean hasSectors = item.optJSONArray("matchesurl") != null;
                                if (hasSectors) url = "";
                                AgeClicked(competitionId, ageValue, hasSectors, url);
                            } else if (type.equals("sector")) {
                                SectorClicked(name, item.getString("url"));
                            } else if (type.equals("venue")) {
                                String url = item.optString("url");
                                if (isValid(url))
                                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            }
                        } catch (Exception e) {
                            // Ignore error
                        }
                    }});
                    container.addView(itemCard);
                }
            }
        } catch (Exception e) {
            Log.w("FootballSuite", "createSearchableListView error", e);
        }
    }

    private GradientDrawable createCardBackground(int backgroundColor, int borderColor, int cornerRadius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(backgroundColor);
        bg.setCornerRadius(cornerRadius);
        bg.setStroke(dpToPx(1), borderColor);
        return bg;
    }

        private View createNewsCardView(JSONObject newsItem, String lang) throws JSONException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout card = new LinearLayout(context);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        
        if (Build.VERSION.SDK_INT >= 17) {
            card.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(this.cardBackgroundColor);
        bg.setCornerRadius(dpToPx(8));
        bg.setStroke(dpToPx(1), this.dividerColor);
        if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg);
        else card.setBackgroundDrawable(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(2));

        // --- Image Handling ---
        String imageUrl = newsItem.optString("image", null);
        if (isValid(imageUrl)) {
            ImageView imageView = new ImageView(context);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT);
            imageParams.setMargins(0, 0, 0, dpToPx(12));
            imageView.setLayoutParams(imageParams);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            
            // Load image with Picasso
            Picasso.with(context).load(imageUrl)
                .error(android.R.drawable.ic_menu_gallery)
                .into(imageView);
            
            // Make image clickable
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Fire event
                    NewsImageClicked(imageUrl);
                    // Show full screen image
                    showFullScreenImageDialog(imageUrl);
                }
            });
            
            card.addView(imageView);
        }

        // --- Title ---
        TextView titleView = new TextView(context);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT)); 
        titleView.setText(getLocalizedText(newsItem, "title", lang));
        titleView.setTextSize(18);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(CLR_CARD);
        titleView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
        card.addView(titleView);

        // --- Date ---
        TextView dateView = new TextView(context);
        dateView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dateView.setText(getLocalizedText(newsItem, "date", lang));
        dateView.setTextSize(12);
        dateView.setTextColor(CLR_BORDER);
        dateView.setPadding(0, dpToPx(4), 0, dpToPx(8));
        dateView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
        card.addView(dateView);

        // --- Details (With Links and Copy Feature) ---
        Object detailsObj = newsItem.opt("details");
        
        if (detailsObj instanceof JSONArray) {
            JSONArray detailsArray = (JSONArray) detailsObj;
            for (int i = 0; i < detailsArray.length(); i++) {
                String paragraph = detailsArray.getString(i);
                if (!paragraph.isEmpty()) {
                    TextView detailsView = createSelectableTextViewWithLinks(paragraph.trim());
                    detailsView.setTextSize(14);
                    detailsView.setTextColor(0xFF163354);
                    detailsView.setLineSpacing(0, 1.4f);
                    detailsView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
                    detailsView.setPadding(0, dpToPx(8), 0, dpToPx(4));
                    card.addView(detailsView);
                }
            }
        } else {
            String details = getLocalizedText(newsItem, "details", lang).trim();
            if (!details.isEmpty()) {
                TextView detailsView = createSelectableTextViewWithLinks(details);
                detailsView.setTextSize(14);
                detailsView.setTextColor(0xFF163354);
                detailsView.setLineSpacing(0, 1.2f);
                detailsView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
                card.addView(detailsView);
            }
        }

        return card;
    }

        /**
     * ✅ NEW HELPER METHOD
     * Creates a TextView that is selectable (copyable) and automatically detects links.
     */
    private TextView createSelectableTextViewWithLinks(String text) {
        TextView tv = new TextView(context);
        
        // 1. Enable Selection (Long press to copy)
        tv.setTextIsSelectable(true);
        
        // 2. Set Text
        tv.setText(text);
        
        // 3. Enable Link Movement (Clicking links opens browser)
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        
        // 4. Auto-find web links and make them clickable
        tv.setLinksClickable(true);
        Linkify.addLinks(tv, Linkify.WEB_URLS);
        
        // 5. Set a visual color for links to indicate they are clickable
        tv.setLinkTextColor(0xFF1565A0);

        // 6. Styling
        tv.setTextSize(14);
        tv.setTextColor(0xFF163354);
        // Ensure text wrapping works correctly
        tv.setHorizontallyScrolling(false);
        tv.setMaxWidth(Integer.MAX_VALUE); // Allow full width

        return tv;
    }

    private java.util.Map<String, List<JSONObject>> smartGroupCompletedMatches(JSONArray allMatches, JSONArray allTeams) throws JSONException {
        java.util.Map<String, List<JSONObject>> matchesByBucket = new HashMap<>();
        java.util.Map<String, String> teamIdToGroupMap = new HashMap<>();
        for (int i = 0; i < allTeams.length(); i++) {
            JSONObject team = allTeams.getJSONObject(i);
            teamIdToGroupMap.put(team.getString("team_id"), team.optString("group", null));
        }
        for (int i = 0; i < allMatches.length(); i++) {
            JSONObject match = allMatches.getJSONObject(i);
            if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
            String matchGroup = match.optString("group", null);
            String homeTeamId = match.getString("home_team_id");
            String teamGroup = teamIdToGroupMap.get(homeTeamId);
            String bucketKey;
            if (matchGroup != null && !matchGroup.isEmpty() && !matchGroup.equals("null") && !matchGroup.equals(teamGroup)) {
                bucketKey = matchGroup;
            } else if (teamGroup != null && !teamGroup.isEmpty() && !teamGroup.equals("null")) {
                bucketKey = getStatLocalizedText("group", "en") + " " + teamGroup;
            } else {
                bucketKey = getStatLocalizedText("overall_stats", "en");
            }
            if (!matchesByBucket.containsKey(bucketKey)) {
                matchesByBucket.put(bucketKey, new ArrayList<>());
            }
            List<JSONObject> bucket = matchesByBucket.get(bucketKey);
            if(bucket != null) bucket.add(match);
        }
        return matchesByBucket;
    }

    private String calculateStatForGroup(List<JSONObject> groupMatches, JSONArray allTeams, String statType, String language) throws JSONException {
        int totalMatches = groupMatches.size();
        int totalGoals = 0;
        int winnerMatches = 0;
        int drawMatches = 0;
        java.util.Map<String, TeamStats> statsMap = new HashMap<>();

        for (JSONObject match : groupMatches) {
            int homeScore = match.getInt("home_score");
            int awayScore = match.getInt("away_score");
            totalGoals += homeScore + awayScore;
            if (homeScore != awayScore) winnerMatches++;
            else drawMatches++;
            String homeTeamId = match.getString("home_team_id");
            String awayTeamId = match.getString("away_team_id");
            if (!statsMap.containsKey(homeTeamId)) statsMap.put(homeTeamId, new TeamStats(homeTeamId));
            if (!statsMap.containsKey(awayTeamId)) statsMap.put(awayTeamId, new TeamStats(awayTeamId));
            
            TeamStats homeStats = statsMap.get(homeTeamId);
            if (homeStats != null) {
                homeStats.goalsFor += homeScore;
                homeStats.goalsAgainst += awayScore;
            }
            
            TeamStats awayStats = statsMap.get(awayTeamId);
            if(awayStats != null) {
                awayStats.goalsFor += awayScore;
                awayStats.goalsAgainst += homeScore;
            }
        }

        switch (statType) {
            case "total_matches":
                return String.valueOf(totalMatches);
            case "total_goals":
                return String.valueOf(totalGoals);
            case "goal_rate":
                return String.format(Locale.US, "%.2f", (totalMatches > 0) ? (double) totalGoals / totalMatches : 0);
            case "winner_matches":
                return String.format(Locale.US, "%d (%.1f%%)", winnerMatches, (totalMatches > 0) ? (double) winnerMatches / totalMatches * 100 : 0);
            case "draw_matches":
                return String.format(Locale.US, "%d (%.1f%%)", drawMatches, (totalMatches > 0) ? (double) drawMatches / totalMatches * 100 : 0);
            case "strongest_attack":
            case "weakest_defense":
            case "strongest_defense":
            case "weakest_attack":
                int bestValue;
                boolean findMax;
                if (statType.equals("strongest_attack") || statType.equals("weakest_defense")) {
                    bestValue = -1;
                    findMax = true;
                } else {
                    bestValue = Integer.MAX_VALUE;
                    findMax = false;
                }
                List<String> bestTeamIds = new ArrayList<>();
                for (TeamStats stats : statsMap.values()) {
                    int currentValue = (statType.endsWith("attack")) ? stats.goalsFor : stats.goalsAgainst;
                    boolean isBetter = findMax ? currentValue > bestValue : currentValue < bestValue;
                    if (isBetter) {
                        bestValue = currentValue;
                        bestTeamIds.clear();
                        bestTeamIds.add(stats.teamId);
                    } else if (currentValue == bestValue) {
                        bestTeamIds.add(stats.teamId);
                    }
                }
                String teamNames = getTeamNamesByIds(bestTeamIds, allTeams, language);
                if (bestValue == -1 || bestValue == Integer.MAX_VALUE) return "-";
                return teamNames.isEmpty() ? "-" : String.format("%s (%d)", teamNames, bestValue);
            default:
                return "";
        }
    }

    private View createSingleStatCard(String title, String value, String language) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        card.setLayoutParams(params);
        card.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(12));
        card.setBackgroundColor(this.cardBackgroundColor);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(this.secondaryTextColor);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(22);
        valueView.setTypeface(null, Typeface.BOLD);
        valueView.setTextColor(CLR_AQUA);
        valueView.setGravity(Gravity.CENTER);
        card.addView(valueView);
        return card;
    }
    // New method for team stat row that knows the stat type
private View createTeamStatRow(PlayerStat stat, int count, String lang, final String statType) {
    boolean isRTL = "ar".equalsIgnoreCase(lang);
    final String playerName = stat.playerName;
    final String teamId = stat.teamId;
    final String teamName = stat.teamName;
    final String finalStatType = statType;

    LinearLayout r = new LinearLayout(context);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
    r.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
    rp.setMargins(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5));
    r.setLayoutParams(rp);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
        r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        r.setBackground(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
    } else {
        r.setBackgroundDrawable(createCardBackground(CLR_CARD, CLR_BORDER, dpToPx(12)));
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        r.setElevation(dpToPx(6));
    }

    LinearLayout ptl = new LinearLayout(context);
    ptl.setOrientation(LinearLayout.VERTICAL);
    ptl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
    ptl.setPadding(0, 0, dpToPx(12), 0);

    TextView pName = createTextView(stat.playerName, -1, 0, true);
    pName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    pName.setTextSize(16);
    pName.setTextColor(CLR_AQUA);
    pName.setTypeface(null, Typeface.BOLD);

    pName.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            PlayerClicked(playerName, teamId, teamName, finalStatType);
            showPlayerStatsPopup(playerName, teamId, teamName, finalStatType, lang);
        }
    });

    TextView tName = createTextView(stat.teamName, -1, 0, false);
    tName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    tName.setTextColor(CLR_TEAL);
    tName.setTextSize(12);

    ptl.addView(pName);
    ptl.addView(tName);

    String icon = "";
    if ("goals".equals(statType)) {
        icon = "⚽ ";
    } else if ("assists".equals(statType)) {
        icon = "🎯 ";
    } else if ("clean_sheets".equals(statType)) {
        icon = "🧤 ";
    }

    TextView sv = createTextView(icon + count, -2, 0, true);
    sv.setTextSize(20);
    sv.setTextColor(Color.WHITE);
    sv.setMinWidth(dpToPx(64));
    sv.setGravity(Gravity.CENTER);

    r.addView(ptl);
    r.addView(sv);

    return r;
}

    private View createStatsGroupHeaderView(String name, String lang) {
        String headerText = name;
        if (name.startsWith("Group ")) {
            headerText = getStatLocalizedText("group", lang) + " " + name.substring(6);
        }
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.setMargins(0, dpToPx(16), 0, dpToPx(8));
        row.setLayoutParams(rp);

        // Left accent bar
        View accent = new View(context);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(CLR_AQUA);
        accentBg.setCornerRadius(dpToPx(4));
        if (Build.VERSION.SDK_INT >= 16) accent.setBackground(accentBg); else accent.setBackgroundDrawable(accentBg);
        accent.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(4), dpToPx(32)));

        TextView label = new TextView(context);
        label.setText(headerText);
        GradientDrawable labelBg = new GradientDrawable();
        labelBg.setColor(CLR_CARD);
        labelBg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) label.setBackground(labelBg); else label.setBackgroundDrawable(labelBg);
        label.setTextColor(CLR_AQUA);
        label.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        label.setTextSize(16);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dpToPx(10), 0, 0, 0);
        label.setLayoutParams(lp);

        row.addView(accent);
        row.addView(label);
        return row;
    }

    private JSONObject findTeamById(String teamId, JSONArray teams) throws JSONException {
        if (teamId == null || teams == null) return null;
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.getJSONObject(i);
            if (teamId.equals(team.optString("team_id"))) {
                return team;
            }
        }
        return null;
    }

    private String getTeamNamesByIds(List<String> ids, JSONArray teams, String lang) throws JSONException {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            JSONObject team = findTeamById(ids.get(i), teams);
            if (team != null) {
                names.append(getLocalizedText(team, "name", lang));
                if (i < ids.size() - 1) {
                    names.append(", ");
                }
            }
        }
        return names.toString();
    }

    private String getStatLocalizedText(String key, String lang) {
        boolean isAR = "ar".equalsIgnoreCase(lang);
        
        if ("total_matches_title".equals(key)) return isAR ? "إجمالي المباريات" : "Matches Played";
        if ("total_goals_title".equals(key)) return isAR ? "الأهداف المسجلة (المعدل)" : "Goals Scored (Rate)";
        if ("goal_rate_title".equals(key)) return isAR ? "معدل الأهداف / مباراة" : "Goal Rate / Match";
        if ("winner_matches_title".equals(key)) return isAR ? "مباريات انتهت بفوز" : "Matches With Winner";
        if ("draw_matches_title".equals(key)) return isAR ? "مباريات انتهت بالتعادل" : "Draw Matches";
        if ("strongest_attack_title".equals(key)) return isAR ? "أقوى هجوم" : "Strongest Attack";
        if ("strongest_defense_title".equals(key)) return isAR ? "أقوى دفاع" : "Strongest Defense";
        if ("weakest_attack_title".equals(key)) return isAR ? "أضعف هجوم" : "Weakest Attack";
        if ("weakest_defense_title".equals(key)) return isAR ? "أضعف دفاع" : "Weakest Defense";
        if ("no_completed_matches".equals(key)) return isAR ? "لا توجد مباريات مكتملة" : "No completed matches.";
        if ("group".equals(key)) return isAR ? "المجموعة" : "Group";
        if ("overall_stats".equals(key)) return isAR ? "إحصائيات عامة" : "Overall Stats";
        if ("wins_stat_title".equals(key)) return isAR ? "مرات الفوز (النسبة)" : "Wins (Percentage)";
        if ("draws_stat_title".equals(key)) return isAR ? "التعادلات (النسبة)" : "Draws (Percentage)";
        if ("losses_stat_title".equals(key)) return isAR ? "الخسائر (النسبة)" : "Losses (Percentage)";
        if ("total_goals_conceded_title".equals(key)) return isAR ? "الأهداف المستقبلة (المعدل)" : "Goals Conceded (Rate)";

        return key;
    }
    
    private void preCacheData() {
        if (jsonData == null || isDataCached) return;
        
        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    if (jsonData.has("teams")) {
                        JSONArray teams = jsonData.optJSONArray("teams");
                        for (int i = 0; i < teams.length(); i++) {
                            JSONObject team = teams.getJSONObject(i);
                            cachedTeamsById.put(team.getString("team_id"), team);
                        }
                    }
                    
                    // In preCacheData method:
if (jsonData.has("matches")) {
    JSONArray matches = jsonData.optJSONArray("matches");
    if (matches != null) {
        cachedMatchesList.clear();
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.getJSONObject(i);
            // Store optimized version with only needed fields
            JSONObject optimizedMatch = extractEssentialFields(match, MATCH_FIELDS);
            cachedMatchesList.add(optimizedMatch);
        }
    }
}
                    
                    isDataCached = true;
                } catch (JSONException e) {
                    Log.e("FootballSuite", "Error during preCacheData", e);
                }
            }
        });
    }
    
    private void findAllHorizontalScrollViews(View v, java.util.List<HorizontalScrollView> list) {
        if (v instanceof HorizontalScrollView) {
            list.add((HorizontalScrollView) v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findAllHorizontalScrollViews(vg.getChildAt(i), list);
            }
        }
    }
    
    private void parseJsonFromUrlWithRetry(final String url, final int retryCount) {
     if (retryCount > MAX_RETRIES) {  // ← Add this check at the beginning
        activity.runOnUiThread(new Runnable() {
    @Override
    public void run() {
        AfterParsingFail("Max retries exceeded");
    }
});
        return;
    }

    final AsyncHttpClient client = AsyncHttpClient.getDefaultInstance();
    final AsyncHttpGet request = new AsyncHttpGet(url);
    
    client.executeByteBufferList(request, new AsyncHttpClient.DownloadCallback() {
        @Override
        public void onCompleted(final Exception e, final AsyncHttpResponse source, 
                               final ByteBufferList result) {
            try {
                // IMPORTANT: Close the response to prevent connection leaks
                if (source != null) {
                    source.close();
                }
                
                backgroundExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (e != null) {
                            if (retryCount < MAX_RETRIES) {
                                try {
                                    Thread.sleep(RETRY_DELAY_MS);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                parseJsonFromUrlWithRetry(url, retryCount + 1);
                            } else {
                                activity.runOnUiThread(new Runnable() {
                                    public void run() {
                                        jsonData = null;
                                        AfterParsingFail("Network Error: " + e.getMessage());
                                    }
                                });
                            }
                            return;
                        }
                        
                        try {
                            String jsonString = "";
                            if (result != null) {
                                byte[] bytes = result.getAllByteArray();
                                jsonString = new String(bytes, "UTF-8");
                                result.recycle(); // Recycle the ByteBufferList
                            }
                            
                            final JSONObject parsedData = new JSONObject(jsonString);
                            
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    jsonData = parsedData;
                                    prefs.edit().putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis()).apply();
                                    preCacheData();
                                    AfterParsingSuccess();
                                }
                            });
                        } catch (final JSONException je) {
                            Log.e("FootballSuite", "JSON parsing failed", je);
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    jsonData = null;
                                    AfterParsingFail("JSON Parsing Error: " + je.getMessage());
                                }
                            });
                        } catch (final java.io.UnsupportedEncodingException uee) {
                            Log.e("FootballSuite", "Unsupported encoding", uee);
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    jsonData = null;
                                    AfterParsingFail("Encoding Error: " + uee.getMessage());
                                }
                            });
                        } catch (final Exception je) {
                            Log.e("FootballSuite", "Unexpected error parsing JSON", je);
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    jsonData = null;
                                    AfterParsingFail("Error parsing data: " + je.getMessage());
                                }
                            });
                        } finally {
                            // Additional cleanup if needed
                            if (result != null) {
                                result.recycle();
                            }
                        }
                    }
                });
            } catch (Exception cleanupError) {
                // Log cleanup error but don't crash
                android.util.Log.e("FootballSuite", "Error during cleanup", cleanupError);
            }
        }
        
        @Override
        public void onConnect(AsyncHttpResponse response) {
            // Optional: Called when connection is established
        }
        
        
        public void onDataAvailable(AsyncHttpResponse response, ByteBufferList data) {
            // Optional: Called as data arrives
        }
    });
}
    

    public void onDestroy() {
    if (backgroundExecutor != null) {
        backgroundExecutor.shutdown();
    }
    
    if (viewCache != null) {
        viewCache.clear();
    }
    if (cachedTeamsById != null) {
        cachedTeamsById.clear();
    }
    if (cachedMatchesList != null) {
        cachedMatchesList.clear();
    }
    
    // ADD THIS SECTION:
    // Shutdown AsyncHttpClient to prevent lingering connections
    try {
        AsyncHttpClient.getDefaultInstance().getServer().stop();
    } catch (Exception e) {
        // Ignore shutdown errors
    }
}
// ✅ ADD THE SAFE TEXT VIEW CREATOR HERE - before the final closing brace
    /**
     * Creates a TextView with null safety protection.
     * @param text The text to display (can be null)
     * @param fallback The fallback text to show if primary text is null or empty
     * @return A properly configured TextView
     */
    private TextView createSafeTextView(String text, String fallback) {
        TextView tv = new TextView(context);
        String displayText = (text != null && !text.isEmpty()) ? text : fallback;
        tv.setText(displayText);
        tv.setTextColor(this.primaryTextColor);
        tv.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        return tv;
    }
    
    /**
     * Creates a TextView with null safety and custom styling.
     * @param text The text to display (can be null)
     * @param fallback The fallback text if primary is null
     * @param isBold Whether to make the text bold
     * @return A properly configured TextView
     */
    private TextView createSafeTextViewWithStyle(String text, String fallback, boolean isBold) {
        TextView tv = createSafeTextView(text, fallback);
        if (isBold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        return tv;
    }
        // ==========================================
    // ==========================================
    // WATERMARK IMPLEMENTATION
    // ==========================================
    
    private View watermarkView;
    private WindowManager wm;

    @SimpleFunction(description = "Starts the watermark protection overlay.")
    public void StartWatermark() {
        if (watermarkView != null) return; // Already running

        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        
        // Create a View that draws the text
        watermarkView = new View(context) {
            Paint paint = new Paint();
            
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                
                // Paint settings
                paint.setColor(FootballSuite.this.primaryTextColor);
                paint.setAlpha(200); // Slight transparency
                paint.setTextSize(40); // Text size
                paint.setAntiAlias(true); // Smooth edges
                paint.setFakeBoldText(true); // Bold text
                paint.setTextAlign(Paint.Align.CENTER); // Center alignment
                paint.setShadowLayer(10, 10, 10, FootballSuite.this.shadowColor); // Drop shadow

                // Draw text at center
                String text = "youthscores.org";
                float textWidth = paint.measureText(text);
                float x = (canvas.getWidth() - textWidth) / 2;
                float y = canvas.getHeight() / 2;
                
                canvas.drawText(text, x, y, paint);
            }
        };

        // Layout Params to overlay everything
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        try {
            wm.addView(watermarkView, params);
        } catch (Exception e) {
            // Permission might be denied
            android.util.Log.e("Watermark", "Failed to add view", e);
        }
    }

    @SimpleFunction(description = "Stops the watermark protection overlay.")
    public void StopWatermark() {
        if (watermarkView != null && wm != null) {
            try {
                wm.removeView(watermarkView);
            } catch (Exception e) {
                // Ignore
            }
            watermarkView = null;
        }
    }

    private View createTeamStatSectionHeader(String title) {
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(15);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(CLR_AQUA);
        label.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CLR_CARD);
        bg.setCornerRadius(dpToPx(10));
        if (Build.VERSION.SDK_INT >= 16) label.setBackground(bg); else label.setBackgroundDrawable(bg);
        label.setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dpToPx(12));
        label.setLayoutParams(p);
        return label;
    }

    private View buildPieChartView(final int wins, final int draws, final int losses, final int total) {
        View chart = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float w = getWidth();
                float h = getHeight();
                float cx = w / 2f;
                float cy = h / 2f;
                float radius = Math.min(w, h) / 2f - dpToPx(16);
                float innerRadius = radius * 0.52f;
                float labelR = (radius + innerRadius) / 2f;

                int winColor  = CLR_AQUA;
                int drawColor = Color.parseColor("#FFD700");
                int lossColor = Color.parseColor("#FF6B6B");
                int bgColor   = CLR_CARD;

                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.FILL);

                float winAngle  = total > 0 ? (wins  / (float) total) * 360f : 0f;
                float drawAngle = total > 0 ? (draws  / (float) total) * 360f : 0f;
                float lossAngle = total > 0 ? (losses / (float) total) * 360f : 0f;

                RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

                float start = -90f;
                if (winAngle  > 0) { p.setColor(winColor);  canvas.drawArc(oval, start, winAngle,  true, p); }
                start += winAngle;
                if (drawAngle > 0) { p.setColor(drawColor); canvas.drawArc(oval, start, drawAngle, true, p); }
                start += drawAngle;
                if (lossAngle > 0) { p.setColor(lossColor); canvas.drawArc(oval, start, lossAngle, true, p); }

                // donut hole
                p.setColor(bgColor);
                canvas.drawCircle(cx, cy, innerRadius, p);

                // slice labels: count + %
                Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(dpToPx(10));
                tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                tp.setColor(bgColor);
                float ls = dpToPx(12);

                if (winAngle >= 20) {
                    float mid = -90f + winAngle / 2f;
                    float lx = cx + labelR * (float) Math.cos(Math.toRadians(mid));
                    float ly = cy + labelR * (float) Math.sin(Math.toRadians(mid));
                    canvas.drawText(String.valueOf(wins), lx, ly - ls / 2f, tp);
                    canvas.drawText(String.format(Locale.US, "%.0f%%", (wins / (float) total) * 100), lx, ly + ls / 2f, tp);
                }
                if (drawAngle >= 20) {
                    float mid = -90f + winAngle + drawAngle / 2f;
                    float lx = cx + labelR * (float) Math.cos(Math.toRadians(mid));
                    float ly = cy + labelR * (float) Math.sin(Math.toRadians(mid));
                    canvas.drawText(String.valueOf(draws), lx, ly - ls / 2f, tp);
                    canvas.drawText(String.format(Locale.US, "%.0f%%", (draws / (float) total) * 100), lx, ly + ls / 2f, tp);
                }
                if (lossAngle >= 20) {
                    float mid = -90f + winAngle + drawAngle + lossAngle / 2f;
                    float lx = cx + labelR * (float) Math.cos(Math.toRadians(mid));
                    float ly = cy + labelR * (float) Math.sin(Math.toRadians(mid));
                    canvas.drawText(String.valueOf(losses), lx, ly - ls / 2f, tp);
                    canvas.drawText(String.format(Locale.US, "%.0f%%", (losses / (float) total) * 100), lx, ly + ls / 2f, tp);
                }
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int sz = MeasureSpec.getSize(widthSpec);
                setMeasuredDimension(sz, sz);
            }
        };

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dpToPx(32), dpToPx(8), dpToPx(32), dpToPx(8));
        chart.setLayoutParams(cp);
        return chart;
    }

    private View buildPieLegendRow(int wins, int draws, int losses, int total, String lang) {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout legend = new LinearLayout(context);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dpToPx(8), 0, 0);
        legend.setLayoutParams(lp);

        legend.addView(buildLegendItem(isRTL ? "فوز"   : "Win",  wins,  total, CLR_AQUA));
        legend.addView(buildLegendItem(isRTL ? "تعادل" : "Draw", draws, total, Color.parseColor("#FFD700")));
        legend.addView(buildLegendItem(isRTL ? "خسارة" : "Loss", losses, total, Color.parseColor("#FF6B6B")));
        return legend;
    }

    private LinearLayout buildLegendItem(String label, int count, int total, int color) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));

        View dot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setColor(color);
        dotBg.setShape(GradientDrawable.OVAL);
        if (Build.VERSION.SDK_INT >= 16) dot.setBackground(dotBg); else dot.setBackgroundDrawable(dotBg);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(12), dpToPx(12)));

        float pct = total > 0 ? (count / (float) total) * 100f : 0f;
        TextView tv = new TextView(context);
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor("#CCFFFFFF"));
        tv.setText("  " + label + ": " + count + " (" + String.format(Locale.US, "%.0f%%", pct) + ")");

        item.addView(dot);
        item.addView(tv);
        return item;
    }

    private View buildGoalsBarChart(int gf, int ga, int played, String lang) {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(CLR_CARD);
        cardBg.setCornerRadius(dpToPx(12));
        if (Build.VERSION.SDK_INT >= 16) card.setBackground(cardBg); else card.setBackgroundDrawable(cardBg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(6));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dpToPx(4), 0, 0);
        card.setLayoutParams(cp);

        int maxGoals = Math.max(Math.max(gf, ga), 1);
        String perGame = isRTL ? "/مباراة" : "/game";
        String scoredLabel   = isRTL ? "أهداف مسجلة"   : "Goals Scored";
        String concededLabel = isRTL ? "أهداف مستقبلة" : "Goals Conceded";

        card.addView(buildGoalBar(scoredLabel,   gf, played, maxGoals, CLR_AQUA, perGame));
        View sp = new View(context);
        sp.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(14)));
        card.addView(sp);
        card.addView(buildGoalBar(concededLabel, ga, played, maxGoals, Color.parseColor("#FF6B6B"), perGame));
        return card;
    }

    private View buildGoalBar(String label, int count, int played, int maxGoals, int barColor, String perGame) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(Color.WHITE);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));

        double rate = played > 0 ? (double) count / played : 0;
        TextView countView = new TextView(context);
        countView.setText(count + "  (" + String.format(Locale.US, "%.2f", rate) + " " + perGame + ")");
        countView.setTextSize(13);
        countView.setTextColor(barColor);
        countView.setTypeface(null, Typeface.BOLD);
        countView.setGravity(Gravity.END);

        header.addView(labelView);
        header.addView(countView);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.parseColor("#0D3050"));
        barBg.setCornerRadius(dpToPx(6));
        if (Build.VERSION.SDK_INT >= 16) bar.setBackground(barBg); else bar.setBackgroundDrawable(barBg);
        LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(-1, dpToPx(14));
        barP.setMargins(0, dpToPx(6), 0, 0);
        bar.setLayoutParams(barP);

        int fillW  = (int)((float) count / maxGoals * 100);
        int emptyW = 100 - fillW;
        if (fillW > 0) {
            View fill = new View(context);
            GradientDrawable fb = new GradientDrawable();
            fb.setColor(barColor);
            fb.setCornerRadius(dpToPx(6));
            if (Build.VERSION.SDK_INT >= 16) fill.setBackground(fb); else fill.setBackgroundDrawable(fb);
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, -1, fillW));
            bar.addView(fill);
        }
        if (emptyW > 0) {
            View empty = new View(context);
            empty.setLayoutParams(new LinearLayout.LayoutParams(0, -1, emptyW));
            bar.addView(empty);
        }

        row.addView(header);
        row.addView(bar);
        return row;
    }
}
