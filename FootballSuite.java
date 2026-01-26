package com.waellotfy.footballsuite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

// App Inventor Imports
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
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

// External Libraries
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.ByteBufferList;
import java.nio.charset.Charset;
import com.squareup.picasso.Picasso;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
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
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FootballSuite extends AndroidNonvisibleComponent implements Component {

    // --- Class Fields ---
    private final Activity activity;
    private final Context context;
    private final Form form;
    private JSONObject jsonData;
    private SharedPreferences prefs;
    private ScrollView lastCreatedMatchListScrollView;
    private View firstUpcomingMatchView;
    private static final String PREFS_NAME = "FootballDataPlusPrefs";
    private static final String LAST_NEWS_COUNT_KEY = "lastNewsCount";
    private static final String SHOWN_ADS_KEY = "shownAds";
    private int groupHeaderTextColor = Color.BLACK;

    // --- UI Customization Fields (Defaults) ---
    private int primaryTextColor = Color.BLACK;
    private int secondaryTextColor = Color.DKGRAY;
    private int cardBackgroundColor = Color.WHITE;
    private int headerBackgroundColor = Color.parseColor("#F5F5F5");
    private int dividerColor = Color.parseColor("#E0E0E0");
    private int accentColor = Color.parseColor("#B71C1C"); // For highlights/points

    // --- Inner Helper Classes ---
    private class TeamStats {
        String teamId;
        int position = 0, points = 0, matchesPlayed = 0, wins = 0, draws = 0, losses = 0, goalsFor = 0, goalsAgainst = 0, penaltyPoints = 0;
        TeamStats(String tId) { this.teamId = tId; }
        int getGoalDifference() { return goalsFor - goalsAgainst; }
    }

    private class PlayerStat {
        String playerName, teamId, teamName;
        int goals = 0, assists = 0, cleanSheets = 0;
        PlayerStat(String pName, String tId, String tName) { this.playerName = pName; this.teamId = tId; this.teamName = tName; }
    }

    // --- Constructor ---
    public FootballSuite(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.activity = container.$context();
        this.form = container.$form();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- UI Properties (@SimpleProperty) ---

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
    @SimpleProperty(description = "Sets the color for main titles, team names, and primary info.")
    public void PrimaryTextColor(int color) {
        this.primaryTextColor = color;
    }

    @SimpleProperty(description = "Returns the primary text color.")
    public int PrimaryTextColor() {
        return this.primaryTextColor;
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF444444")
    @SimpleProperty(description = "Sets the color for secondary text like dates, venues, and notes.")
    public void SecondaryTextColor(int color) {
        this.secondaryTextColor = color;
    }

    @SimpleProperty
    public int SecondaryTextColor() {
        return this.secondaryTextColor;
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFFFFFFF")
    @SimpleProperty(description = "Sets the background color for match cards and news items.")
    public void CardBackgroundColor(int color) {
        this.cardBackgroundColor = color;
    }

    @SimpleProperty
    public int CardBackgroundColor() {
        return this.cardBackgroundColor;
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFF5F5F5")
    @SimpleProperty(description = "Sets the background color for group headers and date strips.")
    public void HeaderBackgroundColor(int color) {
        this.headerBackgroundColor = color;
    }

    @SimpleProperty
    public int HeaderBackgroundColor() {
        return this.headerBackgroundColor;
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFE0E0E0")
    @SimpleProperty(description = "Sets the color of the divider lines.")
    public void DividerColor(int color) {
        this.dividerColor = color;
    }

    @SimpleProperty
    public int DividerColor() {
        return this.dividerColor;
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFFB71C1C")
    @SimpleProperty(description = "Sets the accent color used for points, high scores, or special highlights.")
    public void AccentColor(int color) {
        this.accentColor = color;
    }

    @SimpleProperty
    public int AccentColor() {
        return this.accentColor;
    }
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COLOR, defaultValue = "&HFF000000")
    @SimpleProperty(description = "Sets the text color for group/section headers (e.g. Group A, Week 1).")
    public void GroupHeaderTextColor(int color) {
        this.groupHeaderTextColor = color;
    }

    @SimpleProperty
    public int GroupHeaderTextColor() {
        return this.groupHeaderTextColor;
    }

    // --- Events (@SimpleEvent) ---

    @SimpleEvent(description = "Event raised after the ad view is closed by the user.")
    public void AdClosed() { EventDispatcher.dispatchEvent(this, "AdClosed"); }

    @SimpleEvent(description = "Event raised if there is an error during JSON parsing or data processing.")
    public void AfterParsingFail(String error) { EventDispatcher.dispatchEvent(this, "AfterParsingFail", error); }

    @SimpleEvent(description = "Event raised after the JSON data has been successfully parsed and is ready to use.")
    public void AfterParsingSuccess() { EventDispatcher.dispatchEvent(this, "AfterParsingSuccess"); }

    @SimpleEvent(description = "Event raised when an age category is clicked in a list.")
    public void AgeClicked(String competitionId, String age, boolean hasSectors, String matchesUrlOrNull) { EventDispatcher.dispatchEvent(this, "AgeClicked", competitionId, age, hasSectors, matchesUrlOrNull); }

    @SimpleEvent(description = "Event raised when a competition is clicked in a list.")
    public void CompetitionClicked(String competitionId, String competitionName, boolean hasAges) { EventDispatcher.dispatchEvent(this, "CompetitionClicked", competitionId, competitionName, hasAges); }

    @SimpleEvent(description = "Event raised when a match item is clicked in a list. Returns the unique ID of the match.")
    public void MatchClicked(String matchId) { EventDispatcher.dispatchEvent(this, "MatchClicked", matchId); }

    @SimpleEvent(description = "Event raised when new, unread news items are found. Provides the count of new items and a localized message.")
    public void NewNewsFound(int newCount, String message) { EventDispatcher.dispatchEvent(this, "NewNewsFound", newCount, message); }

    @SimpleEvent(description = "Event raised when a season is clicked in a list.")
    public void SeasonClicked(String seasonName) { EventDispatcher.dispatchEvent(this, "SeasonClicked", seasonName); }

    @SimpleEvent(description = "Event raised when a sector is clicked in a list.")
    public void SectorClicked(String sectorName, String matchesUrl) { EventDispatcher.dispatchEvent(this, "SectorClicked", sectorName, matchesUrl); }

    @SimpleEvent(description = "Event raised when a team item is clicked in a list. Returns the unique ID of the team.")
    public void TeamClicked(String teamId) { EventDispatcher.dispatchEvent(this, "TeamClicked", teamId); }

    @SimpleEvent(description = "Event raised when the JSON version code is higher than the installed app version.")
    public void UpdateRequired(String newVersionName, String newVersionCode) { 
        EventDispatcher.dispatchEvent(this, "UpdateRequired", newVersionName, newVersionCode); 
    }
    
    @SimpleEvent(description = "Event raised when the app is up to date.")
    public void AppIsUpToDate() { 
        EventDispatcher.dispatchEvent(this, "AppIsUpToDate"); 
    }


    // --- Public Functions (@SimpleFunction) ---

    @SimpleFunction(description = "Calculates and displays a league standings table.")
    public void CalculateAndShowStandings(final HVArrangement container, final String groupId, final String stageId, final String lang) {
        if (jsonData == null) return;
        
        // Start background thread for calculation
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Heavy Calculation & Sorting
                    final java.util.List<TeamStats> standings = calculateStandingsForGroup(groupId, stageId);

                    // 2. Update UI on Main Thread
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
        }).start();
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
            java.util.List<String> groups = getJavaGroupList();
            if (groups.isEmpty()) groups.add("");
            for (String gid : groups) {
                if (gid != null && !gid.isEmpty())
                    ml.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + gid, lang));
                java.util.List<TeamStats> standings = calculateStandingsForGroup(gid, "");
                if (standings != null && !standings.isEmpty()) {
                    View table = buildStandingsTable(standings, lang);
                    ml.addView(table);
                }
            }
            sv.addView(ml);
            vg.addView(sv);
        } catch (Exception e) {
            AfterParsingFail("Error creating all standings: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view showing comprehensive statistics.")
    public void CreateAllStatisticsView(final HVArrangement container, final String language) {
        if (this.jsonData == null) { AfterParsingFail("JSON data is not set."); return; }
        
        // Start background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONArray allMatches = jsonData.optJSONArray("matches");
                    final JSONArray allTeams = jsonData.optJSONArray("teams");
                    if (allMatches == null || allTeams == null) { 
                        activity.runOnUiThread(new Runnable() { public void run() { AfterParsingFail("JSON missing matches/teams."); }});
                        return; 
                    }

                    // 1. Heavy Logic: Grouping and Sorting
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

                    // 2. Build UI on Main Thread
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

                                if (matchesByBucket.isEmpty()) {
                                    mainLayout.addView(createSingleStatCard(getStatLocalizedText("no_completed_matches", language), "-", language));
                                    sv.addView(mainLayout);
                                    vg.addView(sv);
                                    return;
                                }

                                for (String bucketName : sortedBucketNames) {
                                    List<JSONObject> bucketMatches = matchesByBucket.get(bucketName);
                                    if (bucketMatches == null || bucketMatches.isEmpty()) continue;

                                    if (!bucketName.equals(getStatLocalizedText("overall_stats", "en"))) {
                                        mainLayout.addView(createStatsGroupHeaderView(bucketName, language));
                                    }

                                    String[] statTypes = {"total_matches", "total_goals", "goal_rate", "winner_matches", "draw_matches", "strongest_attack", "strongest_defense", "weakest_attack", "weakest_defense"};
                                    for (String statType : statTypes) {
                                        String value = calculateStatForGroup(bucketMatches, allTeams, statType, language);
                                        mainLayout.addView(createSingleStatCard(getStatLocalizedText(statType + "_title", language), value, language));
                                    }
                                    mainLayout.addView(createDivider());
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
        }).start();
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
            ml.addView(createDateHeaderView(matchObject.getString("date"), 1, lang, true, matchObject.getString("week")));
            String groupName = getMatchGroupName(matchObject);
            if (groupName != null) ml.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));
            ml.addView(createMatchItemView(matchObject, jsonData.getJSONArray("teams"), lang));
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
                        if (event.equals("صناعة الاهداف") || event.equalsIgnoreCase("Assists")) { isAssistSection = true; continue; }
                        if (!event.isEmpty()) { if (isAssistSection) allAssists.add(event); else allScorers.add(event); }
                    }
                }
                JSONArray awayEvents = getLocalizedArray(match, "away_scorers", lang);
                if (awayEvents != null) {
                    boolean isAssistSection = false;
                    for (int i = 0; i < awayEvents.length(); i++) {
                        String event = awayEvents.getString(i);
                        if (event.equals("صناعة الاهداف") || event.equalsIgnoreCase("Assists")) { isAssistSection = true; continue; }
                        if (!event.isEmpty()) { if (isAssistSection) allAssists.add(event); else allScorers.add(event); }
                    }
                }
            } catch (Exception e) { /* ignore JSON errors during processing */ }

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
    public void CreateMatchLineup(HVArrangement container, String matchId, String lang) { createTwoColumnDetailView(container, matchId, lang, "lineup", "home_squade", "away_squade"); }

    @SimpleFunction(description = "Creates a scrollable list of all matches, grouped by date, week, and group.")
    public void CreateMatchList(HVArrangement container, final String lang) {
        if (jsonData == null) return;
        try {
            JSONArray matches = jsonData.optJSONArray("matches");
            if (matches == null) return;
            final JSONArray teams = jsonData.getJSONArray("teams");
            java.util.List<JSONObject> mList = new java.util.ArrayList<>();
            for (int i = 0; i < matches.length(); i++) mList.add(matches.getJSONObject(i));
            Collections.sort(mList, new Comparator<JSONObject>() { @Override public int compare(JSONObject o1, JSONObject o2) { try { int d = o1.getString("date").compareTo(o2.getString("date")); if (d != 0) return d; return o1.optString("time", "").compareTo(o2.optString("time", "")); } catch (JSONException e) { return 0; } } });
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
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            lastCreatedMatchListScrollView = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context);
            ml.setOrientation(LinearLayout.VERTICAL);
            firstUpcomingMatchView = null;
            for (String date : byDateAndWeek.keySet()) {
                java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                int totalDayMatches = 0;
                if(byWeek == null) continue;
                for (java.util.List<JSONObject> weekMatchesList : byWeek.values()) totalDayMatches += weekMatchesList.size();
                boolean multiWeek = byWeek.size() > 1;
                ml.addView(createDateHeaderView(date, totalDayMatches, lang, !multiWeek, byWeek.keySet().iterator().next()));
                if (firstUpcomingMatchView == null) {
                    for (java.util.List<JSONObject> weekMatchesList : byWeek.values()) {
                        for (JSONObject match : weekMatchesList) {
                            if (!"completed".equalsIgnoreCase(match.optString("status", "upcoming"))) {
                                firstUpcomingMatchView = ml.getChildAt(ml.getChildCount() - 1);
                                break;
                            }
                        }
                        if (firstUpcomingMatchView != null) break;
                    }
                }
                for (String week : byWeek.keySet()) {
                    java.util.List<JSONObject> weekMatches = byWeek.get(week);
                    if (weekMatches == null || weekMatches.isEmpty()) continue;
                    if (multiWeek) ml.addView(createWeekHeaderView(week, lang));
                    java.util.Map<String, java.util.List<JSONObject>> byGroup = new java.util.LinkedHashMap<>();
                    for (JSONObject match : weekMatches) {
                        String group = match.optString("group", null);
                        if (group == null || group.isEmpty() || group.equals("null")) {
                            group = "_no_group_";
                        }
                        if (!byGroup.containsKey(group)) byGroup.put(group, new java.util.ArrayList<JSONObject>());
                        java.util.List<JSONObject> groupList = byGroup.get(group);
                        if (groupList != null) groupList.add(match);
                    }
                    for (String gName : byGroup.keySet()) {
                        if (gName != null && !gName.equals("_no_group_")) {
                            ml.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + gName, lang));
                        }
                        java.util.List<JSONObject> groupMatches = byGroup.get(gName);
                        if (groupMatches != null) {
                            for (JSONObject match : groupMatches) {
                                ml.addView(createMatchItemView(match, teams, lang));
                            }
                        }
                    }
                }
            }
            lastCreatedMatchListScrollView.addView(ml);
            vg.addView(lastCreatedMatchListScrollView);
        } catch (Exception e) {
            AfterParsingFail("Error creating match list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view showing the red cards for both teams in a specific match.")
    public void CreateMatchRedCards(HVArrangement container, String matchId, String lang) { createTwoColumnDetailView(container, matchId, lang, "red_cards", "home_rc", "away_rc"); }

    @SimpleFunction(description = "Creates a view showing the goal scorers for both teams in a specific match.")
    public void CreateMatchScorers(HVArrangement container, String matchId, String lang) { createTwoColumnDetailView(container, matchId, lang, "scorers_list", "home_scorers", "away_scorers"); }

    @SimpleFunction(description = "Creates a view showing the substitutions for both teams in a specific match.")
    public void CreateMatchSubstitutes(HVArrangement container, String matchId, String lang) { createTwoColumnDetailView(container, matchId, lang, "substitutions", "home_sub", "away_sub"); }

    @SimpleFunction(description = "Creates a view showing the yellow cards for both teams in a specific match.")
    public void CreateMatchYellowCards(HVArrangement container, String matchId, String lang) { createTwoColumnDetailView(container, matchId, lang, "yellow_cards", "home_yc", "away_yc"); }

    @SimpleFunction(description = "Creates and displays a scrollable list of news articles from the JSON data.")
    public void CreateNewsList(HVArrangement container, final String language) {
        if (jsonData == null) return;
        ViewGroup vg = (ViewGroup) container.getView();
        vg.removeAllViews();
        try {
            JSONArray newsArray = jsonData.optJSONArray("news");
            if (newsArray == null || newsArray.length() == 0) return;
            final int lastSeenCount = prefs.getInt(LAST_NEWS_COUNT_KEY, 0);
            int currentCount = newsArray.length();
            if (currentCount > lastSeenCount) {
                int newCount = currentCount - lastSeenCount;
                String message = "ar".equalsIgnoreCase(language) ? newCount + " غير مقروء" : newCount + " unread";
                NewNewsFound(newCount, message);
            }
            final ScrollView sv = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context);
            ml.setOrientation(LinearLayout.VERTICAL);
            ml.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            View firstNewItemView = null;
            for (int i = 0; i < newsArray.length(); i++) {
                View newsCard = createNewsCardView(newsArray.getJSONObject(i), language);
                ml.addView(newsCard);
                if (i == lastSeenCount) firstNewItemView = newsCard;
            }
            sv.addView(ml);
            vg.addView(sv);
            final View targetView = firstNewItemView;
            if (targetView != null) {
                sv.post(new Runnable() { @Override public void run() { sv.smoothScrollTo(0, targetView.getTop()); }});
            }
        } catch (Exception e) {
            AfterParsingFail("Error creating news list: " + e.getMessage());
        }
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
                backgroundCircle.setColor(Color.parseColor("#80FFFFFF"));
                GradientDrawable progressCircle = new GradientDrawable();
                progressCircle.setShape(GradientDrawable.OVAL);
                progressCircle.setColor(Color.parseColor("#4CAF50"));
                Drawable[] layers = {
                        backgroundCircle,
                        new android.graphics.drawable.ClipDrawable(progressCircle, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL)
                };
                LayerDrawable layerDrawable = new LayerDrawable(layers);
                progressBar.setProgressDrawable(layerDrawable);
                final TextView countdownText = new TextView(context);
                countdownText.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
                countdownText.setTextColor(Color.BLACK);
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
                                closeButton.setTextColor(Color.DKGRAY);
                                closeButton.setTextSize(20);
                                closeButton.setTypeface(null, Typeface.BOLD);
                                closeButton.setGravity(Gravity.CENTER);
                                GradientDrawable closeBg = new GradientDrawable();
                                closeBg.setShape(GradientDrawable.OVAL);
                                closeBg.setColor(Color.parseColor("#B3FFFFFF"));
                                closeBg.setStroke(dpToPx(2), Color.DKGRAY);
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
    public void CreateTeamAssistsList(HVArrangement container, String teamId, String lang) { calculateAndDisplayTeamStats(container, teamId, lang, "assists"); }

    @SimpleFunction(description = "Creates a view with the detailed header for a specific team, showing its logo and name.")
    public void CreateTeamHeader(HVArrangement container, String teamId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject tInfo = getTeamInfoById(teamId, jsonData.getJSONArray("teams"));
            if (tInfo == null) return;
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            LinearLayout header = createTeamLayout(tInfo, lang);
            header.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            header.setPadding(32, 32, 32, 32);
            vg.addView(header);
        } catch (Exception e) {
            AfterParsingFail("Error creating team header: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view showing a team's information, such as city and field.")
    public void CreateTeamInfo(HVArrangement container, String teamId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject tInfo = getTeamInfoById(teamId, jsonData.getJSONArray("teams"));
            if (tInfo == null) return;
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            LinearLayout il = new LinearLayout(context);
            il.setOrientation(LinearLayout.VERTICAL);
            addInfoRow(il, getLocalizedText(null, "city", lang), getLocalizedText(tInfo, "city", lang), null, lang);
            addInfoRow(il, getLocalizedText(null, "field", lang), getLocalizedText(tInfo, "field", lang), tInfo.optString("fieldurl"), lang);
            addInfoRow(il, getLocalizedText(null, "information", lang), getLocalizedText(tInfo, "information", lang), null, lang);
            vg.addView(il);
        } catch (Exception e) {
            AfterParsingFail("Error creating team info: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates and displays a searchable, grouped list of all teams.")
    public void CreateTeamList(HVArrangement container, final String lang) {
        if (jsonData == null) return;
        try {
            final JSONArray teams = jsonData.getJSONArray("teams");
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
            search.setTextColor(this.primaryTextColor);
            search.setHintTextColor(this.secondaryTextColor);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(24, 24, 24, 16);
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
        try {
            JSONArray allMatches = jsonData.optJSONArray("matches");
            if (allMatches == null) return;
            JSONArray teamMatches = new JSONArray();
            for (int i = 0; i < allMatches.length(); i++) {
                JSONObject match = allMatches.getJSONObject(i);
                if (teamId.equals(match.getString("home_team_id")) || teamId.equals(match.getString("away_team_id")))
                    teamMatches.put(match);
            }
            JSONObject tempJson = new JSONObject();
            tempJson.put("matches", teamMatches);
            tempJson.put("teams", jsonData.getJSONArray("teams"));
            JSONObject originalJson = this.jsonData;
            this.jsonData = tempJson;
            CreateMatchList(container, lang);
            this.jsonData = originalJson;
        } catch (Exception e) {
            AfterParsingFail("Error creating team match list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a view showing a team's players, grouped by position.")
    public void CreateTeamPlayers(HVArrangement container, String teamId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject tInfo = getTeamInfoById(teamId, jsonData.getJSONArray("teams"));
            if (tInfo == null || !tInfo.has("players")) return;
            ViewGroup vg = (ViewGroup) container.getView();
            vg.removeAllViews();
            ScrollView sv = new ScrollView(context);
            LinearLayout pl = new LinearLayout(context);
            pl.setOrientation(LinearLayout.VERTICAL);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "coach", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "goalkeepers", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "defenders", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "midfielders", lang);
            addPlayerSection(pl, tInfo.getJSONObject("players"), "attackers", lang);
            sv.addView(pl);
            vg.addView(sv);
        } catch (Exception e) {
            AfterParsingFail("Error creating player list: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Creates a list of top scorers for a specific team.")
    public void CreateTeamScorersList(HVArrangement container, String teamId, String lang) { calculateAndDisplayTeamStats(container, teamId, lang, "goals"); }

    @SimpleFunction(description = "Creates a list of players with the most assists in the tournament.")
    public void CreateTournamentAssistsList(HVArrangement container, String lang) { calculateAndDisplayTournamentStats(container, lang, "assists"); }

    @SimpleFunction(description = "Creates a list of goalkeepers with the most clean sheets in the tournament.")
    public void CreateTournamentCleanSheetsList(HVArrangement container, String lang) { calculateAndDisplayCleanSheets(container, lang); }

    @SimpleFunction(description = "Creates a list of the top goal scorers in the tournament.")
    public void CreateTournamentScorersList(HVArrangement container, String lang) { calculateAndDisplayTournamentStats(container, lang, "goals"); }

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

    @SimpleFunction(description = "Returns a list of all unique group names found in the data (e.g., 'A', 'B', 'C').")
    public YailList GetGroupList() {
        if (jsonData == null) return YailList.makeEmptyList();
        return YailList.makeList(getJavaGroupList());
    }

    @SimpleFunction(description = "Returns standings data as a list of lists without displaying it. Can be used for custom displays.")
    public YailList GetStandingsData(String groupId, String stageId, String lang) {
        if (jsonData == null) return YailList.makeEmptyList();
        try {
            java.util.List<TeamStats> standings = calculateStandingsForGroup(groupId, stageId);
            if (standings == null || standings.isEmpty()) return YailList.makeEmptyList();
            java.util.List<YailList> resultList = new java.util.ArrayList<>();
            JSONArray teams = jsonData.getJSONArray("teams");
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

    @SimpleFunction(description = "Fetches and parses JSON data from a given URL. Triggers AfterParsingSuccess or AfterParsingFail event.")
    public void ParseJsonFromUrl(String url) {
        AsyncHttpClient.getDefaultInstance().executeByteBufferList(new AsyncHttpGet(url), new AsyncHttpClient.DownloadCallback() {
            @Override
            public void onCompleted(final Exception e, final AsyncHttpResponse source, final com.koushikdutta.async.ByteBufferList result) {
                // Run heavy processing in a background thread
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (e != null) {
                            activity.runOnUiThread(new Runnable() { public void run() { 
                                jsonData = null;
                                AfterParsingFail("Network Error: " + e.getMessage()); 
                            }});
                            return;
                        }
                        try {
                            // 1. Convert bytes to UTF-8 String (Heavy operation)
                            String jsonString = "";
                            if (result != null) {
                                byte[] bytes = result.getAllByteArray();
                                jsonString = new String(bytes, "UTF-8");
                            }
                            
                            // 2. Parse JSON (Heavy operation)
                            final JSONObject parsedData = new JSONObject(jsonString);

                            // 3. Update UI / Fire Event on Main Thread
                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    jsonData = parsedData;
                                    AfterParsingSuccess();
                                }
                            });
                        } catch (final Exception je) {
                            activity.runOnUiThread(new Runnable() { public void run() { 
                                jsonData = null;
                                AfterParsingFail("JSON Parsing Error: " + je.getMessage()); 
                            }});
                        }
                    }
                }).start();
            }
        });
    }

    @SimpleFunction(description = "Scrolls a previously created match list to the first upcoming match.")
    public void ScrollMatchListToUpcoming() {
        if (lastCreatedMatchListScrollView != null && firstUpcomingMatchView != null) {
            lastCreatedMatchListScrollView.post(new Runnable() { @Override public void run() { lastCreatedMatchListScrollView.smoothScrollTo(0, firstUpcomingMatchView.getTop()); }});
        }
    }

    @SimpleFunction(description = "Sets the internal JSON data from a provided text string. Triggers AfterParsingSuccess or AfterParsingFail event.")
    public void SetJsonDataFromString(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            AfterParsingFail("Input JSON string is empty.");
            return;
        }
        try {
            this.jsonData = new JSONObject(jsonString);
            AfterParsingSuccess();
        } catch (JSONException e) {
            this.jsonData = null;
            AfterParsingFail("JSON Parsing Error: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Updates the persistent record of the current news count, so the app knows which news items have been seen.")
    public void UpdateLastNewsCount() {
        if (jsonData == null || !jsonData.has("news")) return;
        try {
            JSONArray newsArray = jsonData.getJSONArray("news");
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(LAST_NEWS_COUNT_KEY, newsArray.length());
            editor.apply();
        } catch (JSONException e) {
            // Ignore error
        }
    }

    @SimpleFunction(description = "Checks if the installed app version matches the version in the JSON file. Triggers 'UpdateRequired' if JSON version is higher.")
    public void CheckAppVersion() {
        if (jsonData == null) {
            AfterParsingFail("JSON data is not loaded.");
            return;
        }

        try {
            // 1. Get Local App Version
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo = pm.getPackageInfo(context.getPackageName(), 0);
            int installedVersionCode = pInfo.versionCode; // e.g., 7

            // 2. Get Remote JSON Version
            JSONObject appVersionObj = jsonData.optJSONObject("app_version");
            if (appVersionObj == null) {
                // Fail silently or log if key is missing, or assume up to date
                return; 
            }
            
            // Parse as integer (handles if it's "7" string or 7 number in JSON)
            int remoteVersionCode = Integer.parseInt(appVersionObj.optString("version_code", "0"));
            String remoteVersionName = appVersionObj.optString("version_name", "Unknown");

            // 3. Compare
            if (remoteVersionCode > installedVersionCode) {
                // An update is available
                UpdateRequired(remoteVersionName, String.valueOf(remoteVersionCode));
            } else {
                // App is up to date
                AppIsUpToDate();
            }

        } catch (Exception e) {
            AfterParsingFail("Error checking app version: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Opens the Google Play Store page for this application to download the update.")
    public void OpenGooglePlay() {
        try {
            // This creates a dynamic link based on the installed package name (com.waellotfy.youthscores)
            String appId = context.getPackageName();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appId));
            
            // Flags to ensure it opens in a new task (standard for extensions)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(intent);
        } catch (Exception e) {
            AfterParsingFail("Could not open Google Play: " + e.getMessage());
        }
    }

    @SimpleFunction(description = "Calculates statistics for a specific team. filterType accepts: 'all', 'home', or 'away'.")
    public void CreateTeamAllStatistics(HVArrangement container, final String teamId, String filterType, final String lang) {
        if (jsonData == null || teamId == null || teamId.isEmpty()) return;

        // Sanitize input (handle casing: "Home" -> "home")
        final String mode = (filterType != null) ? filterType.toLowerCase().trim() : "all";

        new Thread(new Runnable() {
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
                        
                        // 1. Check Status
                        if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;

                        String homeId = match.getString("home_team_id");
                        String awayId = match.getString("away_team_id");

                        boolean isHomeTeam = teamId.equals(homeId);
                        boolean isAwayTeam = teamId.equals(awayId);

                        // 2. Logic: Home Matches
                        // Process if we are the Home Team AND user wanted "all" or "home"
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
                        // 3. Logic: Away Matches
                        // Process if we are the Away Team AND user wanted "all" or "away"
                        else if (isAwayTeam && (mode.equals("all") || mode.equals("away"))) {
                            played++;
                            int hScore = match.getInt("home_score");
                            int aScore = match.getInt("away_score");
                            
                            // Note: For Away team, Goals For = Away Score
                            goalsFor += aScore;
                            goalsAgainst += hScore;

                            if (aScore > hScore) wins++;
                            else if (aScore < hScore) losses++;
                            else draws++;
                        }
                    }

                    // Prepare final variables for UI
                    final int fPlayed = played;
                    final int fWins = wins;
                    final int fDraws = draws;
                    final int fLosses = losses;
                    final int fGF = goalsFor;
                    final int fGA = goalsAgainst;

                    // 4. Build UI on Main Thread
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

                                if (fPlayed == 0) {
                                    mainLayout.addView(createSingleStatCard(getStatLocalizedText("no_completed_matches", lang), "-", lang));
                                    sv.addView(mainLayout);
                                    vg.addView(sv);
                                    return;
                                }

                                String playedStr = String.valueOf(fPlayed);
                                
                                double winPct = (double) fWins / fPlayed * 100.0;
                                String winsStr = String.format(Locale.US, "%d (%.1f%%)", fWins, winPct);

                                double drawPct = (double) fDraws / fPlayed * 100.0;
                                String drawsStr = String.format(Locale.US, "%d (%.1f%%)", fDraws, drawPct);

                                double lossPct = (double) fLosses / fPlayed * 100.0;
                                String lossStr = String.format(Locale.US, "%d (%.1f%%)", fLosses, lossPct);

                                double gfRate = (double) fGF / fPlayed;
                                String gfStr = String.format(Locale.US, "%d (%.2f / %s)", fGF, gfRate, getLocalizedText(null, "game_short", lang));

                                double gaRate = (double) fGA / fPlayed;
                                String gaStr = String.format(Locale.US, "%d (%.2f / %s)", fGA, gaRate, getLocalizedText(null, "game_short", lang));

                                mainLayout.addView(createSingleStatCard(getStatLocalizedText("total_matches_title", lang), playedStr, lang));
                                mainLayout.addView(createSingleStatCard(getStatLocalizedText("wins_stat_title", lang), winsStr, lang));
                                mainLayout.addView(createSingleStatCard(getStatLocalizedText("draws_stat_title", lang), drawsStr, lang));
                                mainLayout.addView(createSingleStatCard(getStatLocalizedText("losses_stat_title", lang), lossStr, lang));
                                mainLayout.addView(createSingleStatCard(getStatLocalizedText("total_goals_title", lang), gfStr, lang));
                                mainLayout.addView(createSingleStatCard(getStatLocalizedText("total_goals_conceded_title", lang), gaStr, lang));

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
        }).start();
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


    // --- Private Helper Methods ---

    private int dpToPx(int dp) {
        return (int)(dp * context.getResources().getDisplayMetrics().density);
    }

    private View createDivider() {
        View d = new View(context);
        d.setLayoutParams(new LinearLayout.LayoutParams(-1, dpToPx(1)));
        d.setBackgroundColor(this.dividerColor);
        return d;
    }

    private JSONObject findMatchById(String mId) throws JSONException {
        JSONArray matches = jsonData.optJSONArray("matches");
        if (matches == null) return null;
        for (int i = 0; i < matches.length(); i++) {
            JSONObject match = matches.getJSONObject(i);
            if (match.getString("match_id").equals(mId)) return match;
        }
        return null;
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
            ml.setOrientation(LinearLayout.VERTICAL);
            if (!"scorers_list".equals(titleKey)) {
                ml.addView(createTextView(getLocalizedText(null, titleKey, lang), -1, 0, true));
            }
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setPadding(16, 16, 16, 16);
            LinearLayout homeCol = new LinearLayout(context);
            homeCol.setOrientation(LinearLayout.VERTICAL);
            homeCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            homeCol.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout awayCol = new LinearLayout(context);
            awayCol.setOrientation(LinearLayout.VERTICAL);
            awayCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            awayCol.setGravity(Gravity.CENTER_HORIZONTAL);
            JSONObject homeTInfo = getTeamInfoById(match.getString("home_team_id"), jsonData.getJSONArray("teams"));
            TextView homeHeader = createTextView(getLocalizedText(homeTInfo, "name", lang), -1, 0, true);
            homeHeader.setPadding(8, 8, 8, 16);
            homeCol.addView(homeHeader);
            JSONObject awayTInfo = getTeamInfoById(match.getString("away_team_id"), jsonData.getJSONArray("teams"));
            TextView awayHeader = createTextView(getLocalizedText(awayTInfo, "name", lang), -1, 0, true);
            awayHeader.setPadding(8, 8, 8, 16);
            awayCol.addView(awayHeader);
            populateDetailColumn(homeCol, homeData, lang, titleKey);
            populateDetailColumn(awayCol, awayData, lang, titleKey);
            View separator = new View(context);
            separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(1), -1));
            separator.setBackgroundColor(Color.parseColor("#CCCCCC"));
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
        String assistsDelimiterAr = getLocalizedText(null, "assists_delimiter", "ar");
        String assistsDelimiterEn = getLocalizedText(null, "assists_delimiter", "en");
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
                boolean isBold = item.equalsIgnoreCase("البدلاء") || item.equalsIgnoreCase("substitutes");
                TextView tv = createTextView(item, -2, 0, isBold);
                tv.setGravity(Gravity.CENTER_HORIZONTAL);
                tv.setPadding(8, 8, 8, 8);
                col.addView(tv);
            }
        }
    }

    private void calculateAndDisplayTournamentStats(HVArrangement c, String lang, final String statType) {
        if (jsonData == null) return;
        try {
            java.util.Map<String, PlayerStat> playerStats = new java.util.HashMap<>();
            JSONArray matches = jsonData.optJSONArray("matches");
            if (matches == null) return;
            JSONArray teams = jsonData.getJSONArray("teams");
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                JSONObject homeTInfo = getTeamInfoById(match.getString("home_team_id"), teams);
                JSONObject awayTInfo = getTeamInfoById(match.getString("away_team_id"), teams);
                processTeamEvents(playerStats, match, "home_scorers", homeTInfo, lang);
                processTeamEvents(playerStats, match, "away_scorers", awayTInfo, lang);
            }
            java.util.Map<String, java.util.List<PlayerStat>> statsByGroup = new java.util.LinkedHashMap<>();
            for (PlayerStat stat : playerStats.values()) {
                JSONObject teamInfo = getTeamInfoById(stat.teamId, teams);
                String groupKey = (teamInfo != null && teamInfo.has("group")) ? teamInfo.getString("group") : "_no_group_";
                if (!statsByGroup.containsKey(groupKey)) statsByGroup.put(groupKey, new java.util.ArrayList<>());
                statsByGroup.get(groupKey).add(stat);
            }
            boolean hasAnyGroups = getJavaGroupList().size() > 1;
            ViewGroup vg = (ViewGroup) c.getView();
            vg.removeAllViews();
            ScrollView sv = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context);
            ml.setOrientation(LinearLayout.VERTICAL);
            // --- NEW: Sort groups alphabetically ---
            List<String> sortedGroupKeys = new ArrayList<>(statsByGroup.keySet());
            Collections.sort(sortedGroupKeys);

            for (String groupName : sortedGroupKeys) {
                java.util.List<PlayerStat> groupStats = statsByGroup.get(groupName);
            // ---------------------------------------
                Collections.sort(groupStats, new Comparator<PlayerStat>() { @Override public int compare(PlayerStat o1, PlayerStat o2) {
                    if ("goals".equals(statType)) return Integer.valueOf(o2.goals).compareTo(o1.goals);
                    else return Integer.valueOf(o2.assists).compareTo(o1.assists);
                }});
                if (hasAnyGroups && !groupName.equals("_no_group_"))
                    ml.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));
                ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, statType, lang)));
                ml.addView(createDivider());
                // New Code with Limit:
                int limitCounter = 0; // 1. Initialize counter
                int MAX_ITEMS_TO_SHOW = 15; // 2. Set your limit here

                for (PlayerStat stat : groupStats) {
                    if (limitCounter >= MAX_ITEMS_TO_SHOW) break; // 3. Stop if limit reached

                    int count = "goals".equals(statType) ? stat.goals : stat.assists;
                    if (count > 0) {
                       ml.addView(createTournamentStatRow(stat, count, lang));
                       ml.addView(createDivider());
                       limitCounter++; // 4. Increment counter
                 }
               }
            }
            sv.addView(ml);
            vg.addView(sv);
        } catch (Exception e) {
            AfterParsingFail("Error calculating tournament stats: " + e.getMessage());
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
            if (eventString.equals("صناعة الاهداف") || eventString.equalsIgnoreCase("Assists")) {
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

    private void calculateAndDisplayCleanSheets(HVArrangement c, String lang) {
        if (jsonData == null) return;
        try {
            java.util.Map<String, PlayerStat> keeperStats = new java.util.HashMap<>();
            JSONArray matches = jsonData.optJSONArray("matches");
            if (matches == null) return;
            JSONArray teams = jsonData.getJSONArray("teams");
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
            java.util.Map<String, java.util.List<PlayerStat>> statsByGroup = new java.util.LinkedHashMap<>();
            for (PlayerStat stat : keeperStats.values()) {
                JSONObject teamInfo = getTeamInfoById(stat.teamId, teams);
                String groupKey = (teamInfo != null && teamInfo.has("group")) ? teamInfo.getString("group") : "_no_group_";
                if (!statsByGroup.containsKey(groupKey)) statsByGroup.put(groupKey, new java.util.ArrayList<>());
                statsByGroup.get(groupKey).add(stat);
            }
            boolean hasAnyGroups = getJavaGroupList().size() > 1;
            ViewGroup vg = (ViewGroup) c.getView();
            vg.removeAllViews();
            ScrollView sv = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context);
            ml.setOrientation(LinearLayout.VERTICAL);
            // --- NEW: Sort groups alphabetically ---
            List<String> sortedGroupKeys = new ArrayList<>(statsByGroup.keySet());
            Collections.sort(sortedGroupKeys);

            for (String groupName : sortedGroupKeys) {
                java.util.List<PlayerStat> groupStats = statsByGroup.get(groupName);
            // ---------------------------------------
                Collections.sort(groupStats, new Comparator<PlayerStat>() { @Override public int compare(PlayerStat o1, PlayerStat o2) { return Integer.valueOf(o2.cleanSheets).compareTo(o1.cleanSheets); }});
                if (hasAnyGroups && !groupName.equals("_no_group_"))
                    ml.addView(createListGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));
                ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, "clean_sheets", lang)));
                ml.addView(createDivider());
                for (PlayerStat stat : groupStats) {
                    if (stat.cleanSheets > 0) {
                        ml.addView(createTournamentStatRow(stat, stat.cleanSheets, lang));
                        ml.addView(createDivider());
                    }
                }
            }
            sv.addView(ml);
            vg.addView(sv);
        } catch (Exception e) {
            AfterParsingFail("Error calculating clean sheets: " + e.getMessage());
        }
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
        r.setPadding(24, 16, 24, 16);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundColor(this.headerBackgroundColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TextView pth = createTextView(getLocalizedText(null, "player", lang) + " / " + getLocalizedText(null, "team", lang), 0, 1, true);
        pth.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        TextView sh = createTextView(title, -2, 0, true);
        sh.setGravity(Gravity.CENTER);
        r.addView(pth);
        r.addView(sh);
        return r;
    }

    private View createTournamentStatRow(PlayerStat stat, int count, String lang) {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(24, 24, 24, 24);
        r.setGravity(Gravity.CENTER_VERTICAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        LinearLayout ptl = new LinearLayout(context);
        ptl.setOrientation(LinearLayout.VERTICAL);
        ptl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView pName = createTextView(stat.playerName, -1, 0, true);
        pName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        pName.setTextSize(16);
        TextView tName = createTextView(stat.teamName, -1, 0, false);
        tName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tName.setTextColor(this.secondaryTextColor);
        tName.setTextSize(12);
        ptl.addView(pName);
        ptl.addView(tName);
        TextView sv = createTextView(String.valueOf(count), -2, 0, true);
        sv.setTextSize(18);
        sv.setMinWidth(120);
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
                c.addView(createDivider());
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
                        c.addView(createDivider());
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { TeamClicked(teamId); } });
        ImageView logo = new ImageView(context);
        logo.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
        Picasso.with(context).load(team.optString("logo")).into(logo);
        TextView name = createTextView(getLocalizedText(team, "name", lang), 0, 1, true);
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
            JSONArray teams = jsonData.getJSONArray("teams");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject item = teams.getJSONObject(i);
                if (item.has("group") && !item.isNull("group") && !item.getString("group").isEmpty()) {
                    String group = item.getString("group");
                    if (!gl.contains(group)) gl.add(group);
                }
            }
        } catch (Exception e) {
            // Ignore error
        }
        Collections.sort(gl);
        return gl;
    }

    private void calculateAndDisplayTeamStats(HVArrangement c, String teamId, String lang, final String statType) {
        if (jsonData == null || teamId == null || teamId.isEmpty()) return;
        try {
            java.util.Map<String, PlayerStat> playerStats = new java.util.HashMap<>();
            JSONArray matches = jsonData.optJSONArray("matches");
            if (matches == null) return;
            JSONArray teams = jsonData.getJSONArray("teams");
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                if (teamId.equals(match.getString("home_team_id"))) {
                    JSONObject teamInfo = getTeamInfoById(teamId, teams);
                    processTeamEvents(playerStats, match, "home_scorers", teamInfo, lang);
                }
                if (teamId.equals(match.getString("away_team_id"))) {
                    JSONObject teamInfo = getTeamInfoById(teamId, teams);
                    processTeamEvents(playerStats, match, "away_scorers", teamInfo, lang);
                }
            }
            ViewGroup vg = (ViewGroup) c.getView();
            vg.removeAllViews();
            ScrollView sv = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context);
            ml.setOrientation(LinearLayout.VERTICAL);
            java.util.List<PlayerStat> sortedStats = new ArrayList<>(playerStats.values());
            Collections.sort(sortedStats, new Comparator<PlayerStat>() {
                @Override public int compare(PlayerStat o1, PlayerStat o2) {
                    if ("goals".equals(statType)) {
                        return Integer.valueOf(o2.goals).compareTo(o1.goals);
                    } else {
                        return Integer.valueOf(o2.assists).compareTo(o1.assists);
                    }
                }
            });
            ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, statType, lang)));
            ml.addView(createDivider());
            for (PlayerStat stat : sortedStats) {
                int count = "goals".equals(statType) ? stat.goals : stat.assists;
                if (count > 0) {
                    ml.addView(createTournamentStatRow(stat, count, lang));
                    ml.addView(createDivider());
                }
            }
            sv.addView(ml);
            vg.addView(sv);
        } catch (Exception e) {
            AfterParsingFail("Error calculating team stats: " + e.getMessage());
        }
    }

    private java.util.List<TeamStats> calculateStandingsForGroup(String gId, String stageId) throws JSONException {
        final JSONArray teams = jsonData.getJSONArray("teams");
        final JSONArray matches = jsonData.optJSONArray("matches");
        java.util.Map<String, TeamStats> statsMap = new java.util.HashMap<>();
        boolean hasGroupFilter = gId != null && !gId.isEmpty();
        boolean hasStageFilter = stageId != null && !stageId.isEmpty();

        // 1. Initialize Teams
    for (int i = 0; i < teams.length(); i++) {
        JSONObject team = teams.getJSONObject(i);
        if (!hasGroupFilter || gId.equals(team.optString("group"))) {
            TeamStats stats = new TeamStats(team.getString("team_id"));

            // --- NEW: Check for Point Deduction ---
            // We parse the string "3" into an integer and subtract it from points
            String deductionStr = team.optString("point_deduction", "0");
            try {
                if (deductionStr != null && !deductionStr.isEmpty()) {
                    int deduction = Integer.parseInt(deductionStr);
                    stats.points -= deduction; 
                }
            } catch (NumberFormatException e) {
                // Ignore if value is not a number
            }
            // --------------------------------------

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

        java.util.List<TeamStats> sorted = new java.util.ArrayList<>(statsMap.values());
        Collections.sort(sorted, new Comparator<TeamStats>() {
            @Override public int compare(TeamStats t1, TeamStats t2) {
                // 1. Total Points
                int pointsCompare = Integer.valueOf(t2.points).compareTo(t1.points);
                if (pointsCompare != 0) return pointsCompare;

                // 2. Head-to-Head (Only if BOTH matches played)
                int h2hPoints1 = 0, h2hPoints2 = 0;
                int h2hGoalsFor1 = 0, h2hGoalsAgainst1 = 0;
                int h2hMatchesPlayed = 0; // Changed from boolean to counter

                try {
                    if (matches != null) {
                        for (int i = 0; i < matches.length(); i++) {
                            JSONObject match = matches.getJSONObject(i);
                            if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;
                            if ("knockout".equalsIgnoreCase(match.optString("stage"))) continue; // Skip KO matches in H2H

                            String homeId = match.getString("home_team_id");
                            String awayId = match.getString("away_team_id");

                            if ((homeId.equals(t1.teamId) && awayId.equals(t2.teamId)) || (homeId.equals(t2.teamId) && awayId.equals(t1.teamId))) {
                                h2hMatchesPlayed++; // Count the match
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

                // LOGIC CHANGE: Only apply H2H if 2 or more matches played
                if (h2hMatchesPlayed >= 2) {
                    int h2hPointsCompare = Integer.valueOf(h2hPoints2).compareTo(h2hPoints1);
                    if (h2hPointsCompare != 0) return h2hPointsCompare;

                    int h2hGd1 = h2hGoalsFor1 - h2hGoalsAgainst1;
                    int h2hGd2 = -h2hGd1; // Reverse for t2
                    int h2hGdCompare = Integer.valueOf(h2hGd2).compareTo(h2hGd1);
                    if (h2hGdCompare != 0) return h2hGdCompare;
                    
                    // If points and GD are equal in H2H, move to away goals rule (optional) or general rules
                }

                // 3. Goal Difference (General)
                int gdc = Integer.valueOf(t2.getGoalDifference()).compareTo(t1.getGoalDifference());
                if (gdc != 0) return gdc;

                // 4. Goals Scored (General)
                int gfc = Integer.valueOf(t2.goalsFor).compareTo(t1.goalsFor);
                if (gfc != 0) return gfc;

                // 5. Alphabetical
                try { return getLocalizedText(getTeamInfoById(t1.teamId, teams), "name", "en").compareTo(getLocalizedText(getTeamInfoById(t2.teamId, teams), "name", "en"));
                } catch (JSONException e) { return 0; }
            }
        });
        for (int i = 0; i < sorted.size(); i++) sorted.get(i).position = i + 1;
        return sorted;
    }

    private View buildStandingsTable(java.util.List<TeamStats> sorted, final String lang) throws JSONException {
        JSONArray teams = jsonData.getJSONArray("teams");
        ScrollView vsv = new ScrollView(context);
        final HorizontalScrollView hsv = new HorizontalScrollView(context);

        // CHANGE: Force RTL layout direction if language is Arabic
        if ("ar".equalsIgnoreCase(lang) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            hsv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }

        LinearLayout tl = new LinearLayout(context);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.setPadding(0, 0, 16, 0);
        tl.addView(createHeaderRow(lang));
        tl.addView(createDivider());
        for (TeamStats stats : sorted) {
            tl.addView(createDataRow(stats, teams, lang));
            tl.addView(createDivider());
        }
        hsv.addView(tl);
        vsv.addView(hsv);

        // CHANGE: Scroll to the far right if language is Arabic
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
        l.setPadding(16, 16, 16, 16);
        l.setGravity(Gravity.CENTER);
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
        for (View v : views) l.addView(v);
        return l;
    }

    private View createDataRow(TeamStats stats, JSONArray teamsData, String lang) throws JSONException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        l.setPadding(16, 24, 16, 24);
        l.setGravity(Gravity.CENTER);
        JSONObject tInfo = getTeamInfoById(stats.teamId, teamsData);
        ImageView iv = new ImageView(context);
        iv.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        String tName = getLocalizedText(tInfo, "name", lang);
        if (tInfo != null) Picasso.with(context).load(tInfo.optString("logo")).into(iv);
        LinearLayout tl = new LinearLayout(context);
        tl.setOrientation(LinearLayout.HORIZONTAL);
        tl.setLayoutParams(new LinearLayout.LayoutParams(400, -2));
        tl.setGravity((isRTL ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        TextView tnv = createTextView(tName, -2, 0, true);
        if (isRTL) {
            tl.addView(tnv);
            tl.addView(iv);
        } else {
            tl.addView(iv);
            tl.addView(tnv);
        }
        String faScore = isRTL ? String.format(Locale.US, "%d:%d", stats.goalsAgainst, stats.goalsFor) : String.format(Locale.US, "%d:%d", stats.goalsFor, stats.goalsAgainst);
        TextView pointsView = createTextView(String.valueOf(stats.points), 90, 0, true);
        pointsView.setTextColor(this.accentColor);
        TextView gdView = createTextView(String.format(Locale.US, "%+d", stats.getGoalDifference()), 80, 0, true);
        gdView.setTextColor(this.primaryTextColor);
        java.util.List<View> views = new java.util.ArrayList<>();
        views.add(createTextView(String.valueOf(stats.position), 80, 0, false));
        views.add(tl);
        views.add(createTextView(String.valueOf(stats.matchesPlayed), 80, 0, false));
        views.add(pointsView);
        views.add(createTextView(faScore, 100, 0, false));
        views.add(gdView);
        views.add(createTextView(String.valueOf(stats.wins), 80, 0, false));
        views.add(createTextView(String.valueOf(stats.draws), 80, 0, false));
        views.add(createTextView(String.valueOf(stats.losses), 80, 0, false));
        views.add(createTextView(String.valueOf(stats.penaltyPoints), 80, 0, false));
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
        h.setBackgroundColor(this.headerBackgroundColor);
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
        String gamesText = (count == 1) ? getLocalizedText(null, "game_one", lang) : count + " " + getLocalizedText(null, "games", lang);
        TextView cl = createTextView(gamesText, 0, 1, true);
        cl.setTextSize(12);
        if (showWeek) {
            TextView wl = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, 0, 1, false);
            wl.setGravity(isRTL ? Gravity.END : Gravity.START);
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

    private View createWeekHeaderView(String weekNum, String lang) {
        TextView weekLabel = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, -1, 0, true);
        weekLabel.setBackgroundColor(this.headerBackgroundColor);
        weekLabel.setPadding(24, 8, 24, 8);
        weekLabel.setTextSize(14);
        weekLabel.setGravity(Gravity.CENTER);
        return weekLabel;
    }

    private View createListGroupHeaderView(String name, String lang) {
        TextView label = createTextView(name, -1, 0, true);
        label.setBackgroundColor(this.headerBackgroundColor);
        // CHANGE: Use the new property variable
        label.setTextColor(this.groupHeaderTextColor); 
        label.setPadding(24, 8, 24, 8);
        label.setTextSize(16);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 8, 0, 8);
        label.setLayoutParams(p);
        return label;
    }

    private View createMatchItemView(JSONObject match, JSONArray teams, final String lang) throws JSONException, ParseException {
        final boolean isRTL = "ar".equalsIgnoreCase(lang);
        final String matchId = match.getString("match_id");
        LinearLayout card = new LinearLayout(context);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(24, 8, 24, 16);
        card.setLayoutParams(cp);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 16, 16, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(this.cardBackgroundColor);
        bg.setCornerRadius(24);
        bg.setStroke(2, this.dividerColor);
        if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg);
        else card.setBackgroundDrawable(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(4);
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { MatchClicked(matchId); } });
        TextView top = createTextView("", -1, 0, false);
        top.setTextSize(12);
        top.setTextColor(this.secondaryTextColor);
        top.setGravity(Gravity.CENTER);
        LinearLayout mid = new LinearLayout(context);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        mid.setPadding(0, 8, 0, 8);
        TextView center = createTextView("", -2, 0, true);
        center.setTextSize(20);
        center.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        TextView bottom = createTextView("", -1, 0, false);
        bottom.setTextSize(12);
        bottom.setTextColor(this.secondaryTextColor);
        bottom.setGravity(Gravity.CENTER);
        LinearLayout homeLayout = createTeamLayout(getTeamInfoById(match.getString("home_team_id"), teams), lang);
        LinearLayout awayLayout = createTeamLayout(getTeamInfoById(match.getString("away_team_id"), teams), lang);
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
        if ("completed".equals(status)) {
            top.setText(getLocalizedText(null, "completed", lang));
            String score = isRTL ? String.format(Locale.US, "%d : %d", match.getInt("away_score"), match.getInt("home_score")) : String.format(Locale.US, "%d : %d", match.getInt("home_score"), match.getInt("away_score"));
            center.setText(score);
            String note = getLocalizedText(match, "note", lang);
            if (!note.isEmpty()) bottom.setText(note);
            else bottom.setVisibility(View.GONE);
        } else if ("postponed".equals(status) || "delayed".equals(status)) {
            top.setText(getLocalizedText(null, status, lang));
            center.setText(match.optString("time", "-"));
            bottom.setText(getLocalizedText(match, "venue", lang));
        } else {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(match.getString("date"));
            top.setText(new SimpleDateFormat("dd MMM", isRTL ? new Locale("ar") : Locale.US).format(date));
            center.setText(match.optString("time", "-"));
            bottom.setText(getLocalizedText(match, "venue", lang));
        }
        card.addView(top);
        card.addView(mid);
        card.addView(bottom);
        return card;
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
        name.setPadding(0, 8, 0, 0);
        l.addView(logo);
        l.addView(name);
        return l;
    }

    private String getLocalizedText(JSONObject o, String key, String lang) {
        if (o == null) {
            boolean isAR = "ar".equalsIgnoreCase(lang);
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
            if ("week".equals(key)) return isAR ? "الأسبوع" : "Week";
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
            if ("game_short".equals(key)) return isAR ? "م" : "M"; // 'M' for Match, 'م' for مباراة
            return key;
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
        for (int i = 0; i < data.length(); i++) {
            if (data.getJSONObject(i).getString("team_id").equals(id)) return data.getJSONObject(i);
        }
        return null;
    }

    private String getMatchGroupName(JSONObject match) throws JSONException {
        String group = match.optString("group", null);
        if (group != null && !group.isEmpty() && !group.equalsIgnoreCase("null")) return group;
        JSONObject teamInfo = getTeamInfoById(match.getString("home_team_id"), jsonData.getJSONArray("teams"));
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
        
        // Define exact alignment based on language
        // In Arabic, we want text to align to the RIGHT side of the screen
        int textAlignment = isRTL ? Gravity.RIGHT : Gravity.LEFT;

        // Check if this is the "Information" (long text) section
        if ("information".equals(getEnglishKeyFor(title, lang))) {
            r.setOrientation(LinearLayout.VERTICAL);
            
            TextView tv = createTextView(title, -1, 0, true);
            tv.setGravity(Gravity.CENTER);
            
            TextView vv = createTextView(value, -1, 0, false);
            // Force text to the Right side for Arabic
            vv.setGravity(textAlignment);
            vv.setPadding(0, 16, 0, 0);
            
            r.addView(tv); 
            r.addView(vv);
        } else {
            // This is for single rows like "City" or "Field"
            r.setOrientation(LinearLayout.HORIZONTAL);
            
            // 1. The Label (e.g. "City")
            TextView tv = createTextView(title, 0, 1, true);
            tv.setGravity(textAlignment | Gravity.CENTER_VERTICAL);
            
            // 2. The Value (e.g. "Cairo")
            TextView vv = createTextView(value, 0, 2, false);
            vv.setGravity(textAlignment | Gravity.CENTER_VERTICAL);
            
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

            // --- THE FIX ---
            // If Arabic, add Value first (Left), then Label (Right).
            // Visual Result: [ Value ........ Label ]
            if (isRTL) {
                r.addView(vv); // Value on the left side
                r.addView(tv); // Label on the right side
            } else {
                r.addView(tv); // Label on the left side
                r.addView(vv); // Value on the right side
            }
        }
        c.addView(r); 
        c.addView(createDivider());
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
        tv.setPadding(32, 24, 32, 8);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(this.headerBackgroundColor);
        TextView pv = createTextView(pList, -1, 0, false);
        pv.setPadding(32, 16, 32, 24);
        pv.setGravity(Gravity.CENTER);
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
        titleView.setTextColor(this.accentColor);
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
        adLayout.setBackgroundColor(Color.BLACK);
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
        adName.setTextColor(Color.WHITE);
        adName.setGravity(Gravity.CENTER);
        adName.setBackgroundColor(Color.parseColor("#80000000"));
        adName.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        RelativeLayout.LayoutParams nameParams = new RelativeLayout.LayoutParams(-1, -2);
        nameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        adLayout.addView(adName, nameParams);
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBackgroundColor(Color.parseColor("#80000000"));
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
            locText.setTextColor(Color.WHITE);
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
                // Ignore error
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
        final LinearLayout listContent = new LinearLayout(context);
        listContent.setOrientation(LinearLayout.VERTICAL);
        buildClickableListView(listContent, dataArray, language, "", type, competitionId, age);
        EditText searchBar = new EditText(context);
        searchBar.setHint("...");
        searchBar.setTextColor(this.primaryTextColor);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        searchParams.setMargins(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8));
        searchBar.setLayoutParams(searchParams);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                buildClickableListView(listContent, dataArray, language, s.toString(), type, competitionId, age);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(listContent);
        mainLayout.addView(searchBar);
        mainLayout.addView(scrollView);
        vg.addView(mainLayout);
    }

    private void buildClickableListView(LinearLayout container, JSONArray dataArray, String language, String filter, final String type, final String competitionId, final String age) {
        container.removeAllViews();
        String filterLower = filter.toLowerCase();
        try {
            for (int i = 0; i < dataArray.length(); i++) {
                final JSONObject item = dataArray.getJSONObject(i);
                final String name;
                if (type.equals("age")) name = item.optString("age");
                else if (type.equals("sector")) name = item.optString("name");
                else if (type.equals("season")) name = item.optString("season");
                else name = getLocalizedText(item, "name", language);
                if (name.toLowerCase().contains(filterLower)) {
                    TextView itemView = new TextView(context);
                    itemView.setText(name);
                    itemView.setTextColor(this.primaryTextColor);
                    itemView.setTextSize(16);
                    itemView.setGravity(Gravity.CENTER);
                    itemView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                    itemView.setTypeface(null, Typeface.BOLD);
                    itemView.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
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
                    container.addView(itemView);
                    container.addView(createDivider());
                }
            }
        } catch (Exception e) {
            // Ignore error
        }
    }

    private View createNewsCardView(JSONObject newsItem, String lang) throws JSONException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout card = new LinearLayout(context);
        
        // Card Layout Params: Width = Match Parent, Height = Wrap Content
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        
        // Force Layout Direction for the container
        if (Build.VERSION.SDK_INT >= 17) {
            card.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }

        // Card Styling
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(this.cardBackgroundColor);
        bg.setCornerRadius(dpToPx(8));
        bg.setStroke(dpToPx(1), this.dividerColor);
        if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg);
        else card.setBackgroundDrawable(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(2));

        // Image Handling
        String imageUrl = newsItem.optString("image", null);
        if (isValid(imageUrl)) {
            ImageView imageView = new ImageView(context);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            imageParams.setMargins(0, 0, 0, dpToPx(12));
            imageView.setLayoutParams(imageParams);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Picasso.with(context).load(imageUrl).into(imageView);
            card.addView(imageView);
        }

        // --- IMPORTANT FIX: Text Layout Params ---
        // Setting width to MATCH_PARENT (-1) ensures the text can align to the far right.
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // Title
        TextView titleView = new TextView(context);
        titleView.setLayoutParams(textParams); 
        titleView.setText(getLocalizedText(newsItem, "title", lang));
        titleView.setTextSize(18);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(this.primaryTextColor);
        titleView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT); 

        // Date
        TextView dateView = new TextView(context);
        dateView.setLayoutParams(textParams);
        dateView.setText(getLocalizedText(newsItem, "date", lang));
        dateView.setTextSize(12);
        dateView.setTextColor(this.secondaryTextColor);
        dateView.setPadding(0, dpToPx(4), 0, dpToPx(8));
        dateView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);

        // Details
        TextView detailsView = new TextView(context);
        detailsView.setLayoutParams(textParams);
        detailsView.setText(getLocalizedText(newsItem, "details", lang).trim());
        detailsView.setTextSize(14);
        detailsView.setTextColor(this.secondaryTextColor);
        detailsView.setLineSpacing(0, 1.2f);
        detailsView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);

        card.addView(titleView);
        card.addView(dateView);
        card.addView(detailsView);
        return card;
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
        card.setBackgroundColor(Color.parseColor("#F5F5F5"));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTextColor(Color.DKGRAY);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextSize(22);
        valueView.setTypeface(null, Typeface.BOLD);
        valueView.setTextColor(Color.BLACK);
        valueView.setGravity(Gravity.CENTER);
        card.addView(valueView);
        return card;
    }

    private View createStatsGroupHeaderView(String name, String lang) {
        TextView label = new TextView(context);
        String headerText = name;
        if (name.startsWith("Group ")) {
            headerText = getStatLocalizedText("group", lang) + " " + name.substring(6);
        }
        label.setText(headerText);
        label.setBackgroundColor(this.headerBackgroundColor);
        // CHANGE: Use the new property variable
        label.setTextColor(this.groupHeaderTextColor);
        label.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        label.setTextSize(18);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dpToPx(12), 0, dpToPx(4));
        label.setLayoutParams(p);
        return label;
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
        
        // Existing Keys
        if ("total_matches_title".equals(key)) return isAR ? "إجمالي المباريات" : "Matches Played";
        if ("total_goals_title".equals(key)) return isAR ? "الأهداف المسجلة (المعدل)" : "Goals Scored (Rate)";
        if ("goal_rate_title".equals(key)) return isAR ? "معدل الأهداف / مباراة" : "Goal Rate / Match";
        if ("winner_matches_title".equals(key)) return isAR ? "مباريات انتهت بفوز" : "Matches With Winner";
        if ("draw_matches_title".equals(key)) return isAR ? "مباريات انتهت بالتعادل" : "Draw Matches";
        if ("strongest_attack_title".equals(key)) return isAR ? "أقوى خط هجوم" : "Strongest Attack";
        if ("strongest_defense_title".equals(key)) return isAR ? "أقوى خط دفاع" : "Strongest Defense";
        if ("weakest_attack_title".equals(key)) return isAR ? "أضعف خط هجوم" : "Weakest Attack";
        if ("weakest_defense_title".equals(key)) return isAR ? "أضعف خط دفاع" : "Weakest Defense";
        if ("no_completed_matches".equals(key)) return isAR ? "لا توجد مباريات مكتملة" : "No completed matches.";
        if ("group".equals(key)) return isAR ? "المجموعة" : "Group";
        if ("overall_stats".equals(key)) return isAR ? "إحصائيات عامة" : "Overall Stats";

        // --- NEW KEYS FOR TEAM STATISTICS ---
        if ("wins_stat_title".equals(key)) return isAR ? "مرات الفوز (النسبة)" : "Wins (Percentage)";
        if ("draws_stat_title".equals(key)) return isAR ? "التعادلات (النسبة)" : "Draws (Percentage)";
        if ("losses_stat_title".equals(key)) return isAR ? "الخسائر (النسبة)" : "Losses (Percentage)";
        if ("total_goals_conceded_title".equals(key)) return isAR ? "الأهداف المستقبلة (المعدل)" : "Goals Conceded (Rate)";
        // ------------------------------------

        return key;
    }
    
    @SimpleFunction(description = "Scrolls all standings tables inside the container to the far right. Useful for RTL languages.")
    public void ScrollStandingsToRight(HVArrangement container) {
        if (container == null) return;
        View view = container.getView();
        
        // List to hold all found scroll views
        final java.util.List<HorizontalScrollView> allScrollViews = new java.util.ArrayList<>();
        
        // Find ALL tables, not just the first one
        findAllHorizontalScrollViews(view, allScrollViews);
        
        // Scroll every table found
        for (final HorizontalScrollView hsv : allScrollViews) {
            hsv.post(new Runnable() {
                @Override
                public void run() {
                    hsv.fullScroll(View.FOCUS_RIGHT);
                }
            });
        }
    }

    // Helper method to recursively find ALL HorizontalScrollViews
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
}