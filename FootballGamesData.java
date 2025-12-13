package com.waellotfy.footballdata;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.runtime.util.YailList;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.squareup.picasso.Picasso;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@DesignerComponent(version = 108, description = "Final version with local assets icons and all UI polishes.", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "images/extension.png")
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
@UsesLibraries(libraries = "androidasync-2.1.1.jar, gson-2.8.2.jar, picasso-2.5.2.jar")
public class FootballGamesData extends AndroidNonvisibleComponent implements Component {
    private final Activity activity;
    private final Context context;
    private JSONObject jsonData;
    private ScrollView lastCreatedMatchListScrollView;
    private View firstUpcomingMatchView;

    private class TeamStats {
        String teamId; int position=0, points=0, matchesPlayed=0, wins=0, draws=0, losses=0, goalsFor=0, goalsAgainst=0, penaltyPoints=0;
        TeamStats(String tId) { this.teamId = tId; }
        int getGoalDifference() { return goalsFor - goalsAgainst; }
    }
    private class PlayerStat {
        String playerName, teamId, teamName; int goals = 0; int assists = 0; int cleanSheets = 0;
        PlayerStat(String pName, String tId, String tName) { this.playerName = pName; this.teamId = tId; this.teamName = tName; }
    }

    public FootballGamesData(ComponentContainer c) {
        super(c.$form()); this.activity = c.$context(); this.context = c.$context();
    }
    @SimpleEvent public void MatchClicked(String matchId) { EventDispatcher.dispatchEvent(this, "MatchClicked", matchId); }
    @SimpleEvent public void TeamClicked(String teamId) { EventDispatcher.dispatchEvent(this, "TeamClicked", teamId); }
    @SimpleEvent public void AfterParsingSuccess() { EventDispatcher.dispatchEvent(this, "AfterParsingSuccess"); }
    @SimpleEvent public void AfterParsingFail(String error) { EventDispatcher.dispatchEvent(this, "AfterParsingFail", error); }

    @SimpleFunction public void ParseJsonFromUrl(String url) {
        AsyncHttpClient.getDefaultInstance().executeString(new AsyncHttpGet(url), new AsyncHttpClient.StringCallback() {
            @Override public void onCompleted(final Exception e, final AsyncHttpResponse source, final String result) {
                activity.runOnUiThread(new Runnable() { @Override public void run() {
                    if (e != null) { jsonData = null; AfterParsingFail("Network Error: " + e.getMessage()); return; }
                    try { jsonData = new JSONObject(result); AfterParsingSuccess();
                    } catch (JSONException je) { jsonData = null; AfterParsingFail("JSON Parsing Error: " + je.getMessage()); }
                }});
            }
        });
    }

    @SimpleFunction public String GetJsonDataAsString() { if (jsonData != null) { return jsonData.toString(); } return "{}"; }
    @SimpleFunction public void CreateTournamentScorersList(HVArrangement c, String lang) { calculateAndDisplayTournamentStats(c, lang, "goals"); }
    @SimpleFunction public void CreateTournamentAssistsList(HVArrangement c, String lang) { calculateAndDisplayTournamentStats(c, lang, "assists"); }
    @SimpleFunction public void CreateTournamentCleanSheetsList(HVArrangement c, String lang) { calculateAndDisplayCleanSheets(c, lang); }
    @SimpleFunction public void CreateTeamScorersList(HVArrangement c, String teamId, String lang) {
        calculateAndDisplayTeamStats(c, teamId, lang, "goals");
    }
    @SimpleFunction public void CreateTeamAssistsList(HVArrangement c, String teamId, String lang) {
        calculateAndDisplayTeamStats(c, teamId, lang, "assists");
    }
    
    @SimpleFunction public void CreateMatchDetailHeader(HVArrangement c, String mId, String lang) {
        if (jsonData == null) return;
        try {
            JSONObject matchObject = findMatchById(mId); if (matchObject == null) return;
            ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();
            LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);
            ml.addView(createDateHeaderView(matchObject.getString("date"), 1, lang, true, matchObject.getString("week")));
            String groupName = getMatchGroupName(matchObject);
            if (groupName != null) ml.addView(createGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));
            ml.addView(createMatchItemView(matchObject, jsonData.getJSONArray("teams"), lang));
            vg.addView(ml);
        } catch (Exception e) { AfterParsingFail("Error creating match header: " + e.getMessage()); }
    }
    @SimpleFunction public void CreateMatchLineup(HVArrangement c, String mId, String lang) { createTwoColumnDetailView(c, mId, lang, "lineup", "home_squade", "away_squade"); }
    @SimpleFunction public void CreateMatchScorers(HVArrangement c, String mId, String lang) { createTwoColumnDetailView(c, mId, lang, "scorers_list", "home_scorers", "away_scorers"); }
    @SimpleFunction public void CreateMatchYellowCards(HVArrangement c, String mId, String lang) { createTwoColumnDetailView(c, mId, lang, "yellow_cards", "home_yc", "away_yc"); }
    @SimpleFunction public void CreateMatchRedCards(HVArrangement c, String mId, String lang) { createTwoColumnDetailView(c, mId, lang, "red_cards", "home_rc", "away_rc"); }
    @SimpleFunction public void CreateMatchSubstitutes(HVArrangement c, String mId, String lang) { createTwoColumnDetailView(c, mId, lang, "substitutions", "home_sub", "away_sub"); }
    @SimpleFunction public YailList GetGroupList() { if (jsonData == null) return YailList.makeEmptyList(); return YailList.makeList(getJavaGroupList()); }

    @SimpleFunction(description = "Calculates standings for a group and/or stage. Use empty strings to ignore a filter.")
    public void CalculateAndShowStandings(HVArrangement c, String groupId, String stageId, final String lang) {
        if (jsonData == null) return;
        try {
            java.util.List<TeamStats> standings = calculateStandingsForGroup(groupId, stageId);
            if (standings == null || standings.isEmpty()) return;
            ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();
            View table = buildStandingsTable(standings, lang);
            vg.addView(table, new ViewGroup.LayoutParams(-1, -1));
            if ("ar".equalsIgnoreCase(lang) && table instanceof ScrollView) {
                final HorizontalScrollView hsv = (HorizontalScrollView)((ScrollView)table).getChildAt(0);
                hsv.post(new Runnable() { @Override public void run() { hsv.fullScroll(View.FOCUS_RIGHT); } });
            }
        } catch (Exception e) { AfterParsingFail("Error displaying standings: " + e.getMessage()); }
    }

    @SimpleFunction(description = "Returns standings data as a list of lists without displaying it.")
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
                row.add(stats.position); row.add(stats.teamId); row.add(teamName);
                row.add(stats.points); row.add(stats.matchesPlayed);
                row.add(stats.wins); row.add(stats.draws); row.add(stats.losses);
                row.add(stats.goalsFor); row.add(stats.goalsAgainst);
                row.add(stats.getGoalDifference()); row.add(stats.penaltyPoints);
                resultList.add(YailList.makeList(row));
            }
            return YailList.makeList(resultList);
        } catch (Exception e) { AfterParsingFail("Error getting standings data: " + e.getMessage()); return YailList.makeEmptyList(); }
    }
    
    @SimpleFunction public void CreateAllGroupsStandings(HVArrangement c, final String lang) {
        if (jsonData == null) return;
        try {
            ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();
            ScrollView sv = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);
            java.util.List<String> groups = getJavaGroupList(); if (groups.isEmpty()) groups.add("");
            for (String gid : groups) {
                if (gid != null && !gid.isEmpty()) ml.addView(createGroupHeaderView(getLocalizedText(null, "group", lang) + " " + gid, lang));
                java.util.List<TeamStats> standings = calculateStandingsForGroup(gid, "");
                if (standings != null && !standings.isEmpty()) {
                    View table = buildStandingsTable(standings, lang); ml.addView(table);
                }
            }
            sv.addView(ml); vg.addView(sv);
        } catch (Exception e) { AfterParsingFail("Error creating all standings: " + e.getMessage()); }
    }

    @SimpleFunction public void CreateMatchList(HVArrangement c, String lang) {
        if (jsonData == null) return;
        try {
            JSONArray matches = jsonData.optJSONArray("matches"); if (matches == null) return;
            final JSONArray teams = jsonData.getJSONArray("teams");
            java.util.List<JSONObject> mList = new java.util.ArrayList<>();
            for (int i = 0; i < matches.length(); i++) mList.add(matches.getJSONObject(i));
            Collections.sort(mList, new Comparator<JSONObject>() { @Override public int compare(JSONObject o1, JSONObject o2) { try { int d = o1.getString("date").compareTo(o2.getString("date")); if (d != 0) return d; return o1.optString("time", "").compareTo(o2.optString("time", "")); } catch (JSONException e) { return 0; } } });
            java.util.Map<String, java.util.Map<String, java.util.List<JSONObject>>> byDateAndWeek = new java.util.LinkedHashMap<>();
            for (JSONObject match : mList) {
                String date = match.getString("date"); String week = match.getString("week");
                if (!byDateAndWeek.containsKey(date)) byDateAndWeek.put(date, new java.util.LinkedHashMap<String, java.util.List<JSONObject>>());
                java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                if (!byWeek.containsKey(week)) byWeek.put(week, new java.util.ArrayList<JSONObject>());
                byWeek.get(week).add(match);
            }
            ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();
            lastCreatedMatchListScrollView = new ScrollView(context);
            LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);
            firstUpcomingMatchView = null;
            for (String date : byDateAndWeek.keySet()) {
                java.util.Map<String, java.util.List<JSONObject>> byWeek = byDateAndWeek.get(date);
                int totalDayMatches = 0; for(java.util.List<JSONObject> weekMatchesList : byWeek.values()) totalDayMatches += weekMatchesList.size();
                boolean multiWeek = byWeek.size() > 1;
                ml.addView(createDateHeaderView(date, totalDayMatches, lang, !multiWeek, byWeek.keySet().iterator().next()));
                if (firstUpcomingMatchView == null) { for (java.util.List<JSONObject> weekMatchesList : byWeek.values()) { for (JSONObject match : weekMatchesList) { if (!"completed".equalsIgnoreCase(match.optString("status", "upcoming"))) { firstUpcomingMatchView = ml.getChildAt(ml.getChildCount() - 1); break; } } if(firstUpcomingMatchView != null) break; } }
                for(String week : byWeek.keySet()) {
                    java.util.List<JSONObject> weekMatches = byWeek.get(week); if(weekMatches.isEmpty()) continue;
                    if(multiWeek) ml.addView(createWeekHeaderView(week, lang));
                    java.util.Map<String, java.util.List<JSONObject>> byGroup = new java.util.LinkedHashMap<>();
                    for (JSONObject match : weekMatches) {
                        String group = match.optString("group", null);
                        if (group == null || group.isEmpty() || group.equals("null")) {
                            group = "_no_group_";
                        }
                        if (!byGroup.containsKey(group)) byGroup.put(group, new java.util.ArrayList<JSONObject>());
                        byGroup.get(group).add(match);
                    }
                    for (String gName : byGroup.keySet()) {
                        if (gName != null && !gName.equals("_no_group_")) {
                            ml.addView(createGroupHeaderView(getLocalizedText(null, "group", lang) + " " + gName, lang));
                        }
                        for (JSONObject match : byGroup.get(gName)) {
                            ml.addView(createMatchItemView(match, teams, lang));
                        }
                    }
                }
            }
            lastCreatedMatchListScrollView.addView(ml); vg.addView(lastCreatedMatchListScrollView);
        } catch (Exception e) { AfterParsingFail("Error creating match list: " + e.getMessage()); }
    }

    @SimpleFunction public void ScrollMatchListToUpcoming() { if (lastCreatedMatchListScrollView != null && firstUpcomingMatchView != null) { lastCreatedMatchListScrollView.post(new Runnable() { @Override public void run() { lastCreatedMatchListScrollView.smoothScrollTo(0, firstUpcomingMatchView.getTop()); }}); } }

    @SimpleFunction public void CreateTeamList(HVArrangement c, final String lang) {
        if (jsonData == null) return;
        try {
            final JSONArray teams = jsonData.getJSONArray("teams");
            final java.util.List<JSONObject> teamList = new java.util.ArrayList<>();
            for (int i = 0; i < teams.length(); i++) teamList.add(teams.getJSONObject(i));
            Collections.sort(teamList, new Comparator<JSONObject>() { @Override public int compare(JSONObject o1, JSONObject o2) { return getLocalizedText(o1, "name", lang).compareTo(getLocalizedText(o2, "name", lang)); } });
            ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();
            LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
            buildTeamListView(content, teamList, lang);
            EditText search = new EditText(context); search.setHint(getLocalizedText(null, "search", lang));
            search.setTextColor(Color.BLACK); search.setHintTextColor(Color.GRAY);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2); sp.setMargins(24, 24, 24, 16); search.setLayoutParams(sp);
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { try { String filter = s.toString().toLowerCase(); java.util.List<JSONObject> filtered = new java.util.ArrayList<>(); for (JSONObject team : teamList) { if (getLocalizedText(team, "name", lang).toLowerCase().contains(filter)) filtered.add(team); } buildTeamListView(content, filtered, lang); } catch (Exception e) {} }
                public void afterTextChanged(Editable s) {}
            });
            ScrollView sv = new ScrollView(context); sv.addView(content);
            ml.addView(search); ml.addView(sv); vg.addView(ml);
        } catch (Exception e) { AfterParsingFail("Error creating team list: " + e.getMessage()); }
    }
    
    @SimpleFunction public void CreateTeamHeader(HVArrangement c, String tId, String lang) {if (jsonData == null) return;try {JSONObject tInfo = getTeamInfoById(tId, jsonData.getJSONArray("teams")); if (tInfo == null) return;ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();LinearLayout header = createTeamLayout(tInfo, lang);header.setLayoutParams(new LinearLayout.LayoutParams(-1, -2)); header.setPadding(32, 32, 32, 32);vg.addView(header);} catch (Exception e) { AfterParsingFail("Error creating team header: " + e.getMessage()); }}
    @SimpleFunction public void CreateTeamInfo(HVArrangement c, String tId, String lang) {if (jsonData == null) return;try {JSONObject tInfo = getTeamInfoById(tId, jsonData.getJSONArray("teams")); if (tInfo == null) return;ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();LinearLayout il = new LinearLayout(context); il.setOrientation(LinearLayout.VERTICAL);addInfoRow(il, getLocalizedText(null, "city", lang), getLocalizedText(tInfo, "city", lang), null, lang);addInfoRow(il, getLocalizedText(null, "field", lang), getLocalizedText(tInfo, "field", lang), tInfo.optString("fieldurl"), lang);addInfoRow(il, getLocalizedText(null, "information", lang), getLocalizedText(tInfo, "information", lang), null, lang);vg.addView(il);} catch (Exception e) { AfterParsingFail("Error creating team info: " + e.getMessage()); }}
    @SimpleFunction public void CreateTeamPlayers(HVArrangement c, String tId, String lang) {if (jsonData == null) return;try {JSONObject tInfo = getTeamInfoById(tId, jsonData.getJSONArray("teams"));if (tInfo == null || !tInfo.has("players")) return;ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();LinearLayout pl = new LinearLayout(context); pl.setOrientation(LinearLayout.VERTICAL);addPlayerSection(pl, tInfo.getJSONObject("players"), "coach", lang); addPlayerSection(pl, tInfo.getJSONObject("players"), "goalkeepers", lang);addPlayerSection(pl, tInfo.getJSONObject("players"), "defenders", lang); addPlayerSection(pl, tInfo.getJSONObject("players"), "midfielders", lang);addPlayerSection(pl, tInfo.getJSONObject("players"), "attackers", lang);vg.addView(pl);} catch (Exception e) { AfterParsingFail("Error creating player list: " + e.getMessage()); }}
    @SimpleFunction public void CreateTeamMatchList(HVArrangement c, String tId, String lang) {if (jsonData == null) return;try {JSONArray allMatches = jsonData.optJSONArray("matches"); if (allMatches == null) return;JSONArray teamMatches = new JSONArray();for (int i = 0; i < allMatches.length(); i++) {JSONObject match = allMatches.getJSONObject(i);if (tId.equals(match.getString("home_team_id")) || tId.equals(match.getString("away_team_id"))) teamMatches.put(match);}JSONObject tempJson = new JSONObject(); tempJson.put("matches", teamMatches); tempJson.put("teams", jsonData.getJSONArray("teams"));JSONObject originalJson = this.jsonData; this.jsonData = tempJson;CreateMatchList(c, lang); this.jsonData = originalJson;} catch (Exception e) { AfterParsingFail("Error creating team match list: " + e.getMessage()); }}
    
    // --- HELPER METHODS ---

    private int dpToPx(int dp) { return (int) (dp * context.getResources().getDisplayMetrics().density); }
    private JSONObject findMatchById(String mId) throws JSONException { JSONArray matches = jsonData.optJSONArray("matches"); if (matches == null) return null; for (int i = 0; i < matches.length(); i++) { JSONObject match = matches.getJSONObject(i); if (match.getString("match_id").equals(mId)) return match; } return null; }
    private JSONArray getLocalizedArray(JSONObject source, String key, String lang) {if(source==null||!source.has(key)||source.isNull(key))return null;try{Object data=source.get(key);if(data instanceof JSONArray)return(JSONArray)data;if(data instanceof JSONObject){JSONObject lObj=(JSONObject)data;if(lObj.has(lang)&&!lObj.isNull(lang))return lObj.getJSONArray(lang);if(lObj.has("en")&&!lObj.isNull("en"))return lObj.getJSONArray("en");if(lObj.has("ar")&&!lObj.isNull("ar"))return lObj.getJSONArray("ar");}}catch(JSONException e){return null;} return null;}
    private void createTwoColumnDetailView(HVArrangement c, String mId, String lang, String titleKey, String homeKey, String awayKey) {if (jsonData == null) return;try {ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();JSONObject match = findMatchById(mId); if (match == null) return;JSONArray homeData = getLocalizedArray(match, homeKey, lang);JSONArray awayData = getLocalizedArray(match, awayKey, lang);if ((homeData == null || homeData.length() == 0) && (awayData == null || awayData.length() == 0)) return;boolean isRTL = "ar".equalsIgnoreCase(lang);LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);if (!"scorers_list".equals(titleKey)) { ml.addView(createTextView(getLocalizedText(null, titleKey, lang), -1, 0, true)); }LinearLayout columns = new LinearLayout(context); columns.setOrientation(LinearLayout.HORIZONTAL); columns.setPadding(16, 16, 16, 16);LinearLayout homeCol = new LinearLayout(context); homeCol.setOrientation(LinearLayout.VERTICAL); homeCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1)); homeCol.setGravity(Gravity.CENTER_HORIZONTAL);LinearLayout awayCol = new LinearLayout(context); awayCol.setOrientation(LinearLayout.VERTICAL); awayCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1)); awayCol.setGravity(Gravity.CENTER_HORIZONTAL);JSONObject homeTInfo = getTeamInfoById(match.getString("home_team_id"), jsonData.getJSONArray("teams"));TextView homeHeader = createTextView(getLocalizedText(homeTInfo, "name", lang), -1, 0, true); homeHeader.setPadding(8, 8, 8, 16); homeCol.addView(homeHeader);JSONObject awayTInfo = getTeamInfoById(match.getString("away_team_id"), jsonData.getJSONArray("teams"));TextView awayHeader = createTextView(getLocalizedText(awayTInfo, "name", lang), -1, 0, true); awayHeader.setPadding(8, 8, 8, 16); awayCol.addView(awayHeader);populateDetailColumn(homeCol, homeData, lang, titleKey); populateDetailColumn(awayCol, awayData, lang, titleKey);View separator = new View(context); separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(1), -1)); separator.setBackgroundColor(Color.parseColor("#CCCCCC"));if (isRTL) { columns.addView(awayCol); columns.addView(separator); columns.addView(homeCol); }else { columns.addView(homeCol); columns.addView(separator); columns.addView(awayCol); }ml.addView(columns); vg.addView(ml);} catch (Exception e) { AfterParsingFail("Error creating detail view: " + e.getMessage()); }}
    private void populateDetailColumn(LinearLayout col, JSONArray data, String lang, String titleKey) throws JSONException {
        if (data == null || data.length() == 0) return;
        boolean isScorersList = "scorers_list".equals(titleKey);
        String assistsDelimiterAr = getLocalizedText(null, "assists_delimiter", "ar");
        String assistsDelimiterEn = "Assists";
        
        boolean hasGoals = false;
        if (isScorersList) {
            for (int i = 0; i < data.length(); i++) {
                String item = data.getString(i);
                if (item.equals(assistsDelimiterAr) || item.equals(assistsDelimiterEn)) break;
                if (!item.isEmpty()) { hasGoals = true; break; }
            }
            if (hasGoals) {
                col.addView(createEventHeader(getLocalizedText(null, "goals", lang), "soccer_ball.png", lang), col.getChildCount());
            }
        }

        for (int i = 0; i < data.length(); i++) {
            String item = data.getString(i);
            if (item.isEmpty()) {
                View spacer = new View(context); spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 16)); col.addView(spacer);
            } else if (isScorersList && (item.equals(assistsDelimiterAr) || item.equals(assistsDelimiterEn))) {
                col.addView(createEventHeader(getLocalizedText(null, "assists", lang), "goal_icon.png", lang));
            } else {
                boolean isBold = item.equalsIgnoreCase("البدلاء") || item.equalsIgnoreCase("substitutes");
                TextView tv = createTextView(item, -2, 0, isBold); tv.setGravity(Gravity.CENTER_HORIZONTAL); tv.setPadding(8, 8, 8, 8); col.addView(tv);
            }
        }
    }
    private void calculateAndDisplayTournamentStats(HVArrangement c, String lang, final String statType) {if (jsonData == null) return;try {java.util.Map<String, PlayerStat> playerStats = new java.util.HashMap<>();JSONArray matches = jsonData.optJSONArray("matches"); if (matches == null) return;JSONArray teams = jsonData.getJSONArray("teams");for (int i = 0; i < matches.length(); i++) {JSONObject match = matches.getJSONObject(i); if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;JSONObject homeTInfo = getTeamInfoById(match.getString("home_team_id"), teams);JSONObject awayTInfo = getTeamInfoById(match.getString("away_team_id"), teams);processTeamEvents(playerStats, match, "home_scorers", homeTInfo, lang);processTeamEvents(playerStats, match, "away_scorers", awayTInfo, lang);}java.util.Map<String, java.util.List<PlayerStat>> statsByGroup = new java.util.LinkedHashMap<>();for(PlayerStat stat : playerStats.values()){JSONObject teamInfo = getTeamInfoById(stat.teamId, teams);String groupKey = (teamInfo != null && teamInfo.has("group")) ? teamInfo.getString("group") : "_no_group_";if (!statsByGroup.containsKey(groupKey)) statsByGroup.put(groupKey, new java.util.ArrayList<PlayerStat>());statsByGroup.get(groupKey).add(stat);}boolean hasAnyGroups = getJavaGroupList().size() > 1;ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();ScrollView sv = new ScrollView(context); LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);for(java.util.Map.Entry<String, java.util.List<PlayerStat>> entry : statsByGroup.entrySet()){String groupName = entry.getKey(); java.util.List<PlayerStat> groupStats = entry.getValue();Collections.sort(groupStats, new Comparator<PlayerStat>() { @Override public int compare(PlayerStat o1, PlayerStat o2) { if ("goals".equals(statType)) return Integer.valueOf(o2.goals).compareTo(o1.goals); else return Integer.valueOf(o2.assists).compareTo(o1.assists); } });if (hasAnyGroups && !groupName.equals("_no_group_")) ml.addView(createGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, statType, lang))); ml.addView(createDivider());for (PlayerStat stat : groupStats) {int count = "goals".equals(statType) ? stat.goals : stat.assists;if (count > 0) { ml.addView(createTournamentStatRow(stat, count, lang)); ml.addView(createDivider()); }}}sv.addView(ml); vg.addView(sv);} catch(Exception e) { AfterParsingFail("Error calculating tournament stats: " + e.getMessage()); }}
    private String[] parsePlayerEventString(String raw) {Pattern p = Pattern.compile("(.*?) *\\((\\d+)\\)"); Matcher m = p.matcher(raw);if (m.find()) return new String[]{m.group(1).trim(), m.group(2)};return new String[]{raw.trim(), "1"};}
    private void processTeamEvents(java.util.Map<String, PlayerStat> stats, JSONObject match, String key, JSONObject tInfo, String lang) throws JSONException {JSONArray events = getLocalizedArray(match, key, lang); if (events == null || tInfo == null) return;boolean isParsingAssists = false;String teamId = tInfo.getString("team_id"); String teamName = getLocalizedText(tInfo, "name", lang);for (int i = 0; i < events.length(); i++) {String eventString = events.getString(i);if (eventString.equals(getLocalizedText(null, "assists_delimiter", lang)) || eventString.equalsIgnoreCase("Assists")) { isParsingAssists = true; continue; }String[] parsed = parsePlayerEventString(eventString);String pName = parsed[0];if(pName.equals("لا يوجد بيانات")) continue;int count = Integer.parseInt(parsed[1]); String uniqueKey = pName + "_" + teamId;PlayerStat pStat = stats.get(uniqueKey);if (pStat == null) { pStat = new PlayerStat(pName, teamId, teamName); stats.put(uniqueKey, pStat); }if (isParsingAssists) pStat.assists += count; else pStat.goals += count;}}
    private void calculateAndDisplayCleanSheets(HVArrangement c, String lang) {if (jsonData == null) return;try {java.util.Map<String, PlayerStat> keeperStats = new java.util.HashMap<>();JSONArray matches = jsonData.optJSONArray("matches"); if (matches == null) return;JSONArray teams = jsonData.getJSONArray("teams");for (int i = 0; i < matches.length(); i++) {JSONObject match = matches.getJSONObject(i); if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;if (match.getInt("away_score") == 0) {JSONObject teamInfo = getTeamInfoById(match.getString("home_team_id"), teams);updateCleanSheetStat(keeperStats, teamInfo, getLocalizedArray(match, "home_squade", lang), lang);}if (match.getInt("home_score") == 0) {JSONObject teamInfo = getTeamInfoById(match.getString("away_team_id"), teams);updateCleanSheetStat(keeperStats, teamInfo, getLocalizedArray(match, "away_squade", lang), lang);}}java.util.Map<String, java.util.List<PlayerStat>> statsByGroup = new java.util.LinkedHashMap<>();for(PlayerStat stat : keeperStats.values()){JSONObject teamInfo = getTeamInfoById(stat.teamId, teams);String groupKey = (teamInfo != null && teamInfo.has("group")) ? teamInfo.getString("group") : "_no_group_";if (!statsByGroup.containsKey(groupKey)) statsByGroup.put(groupKey, new java.util.ArrayList<PlayerStat>());statsByGroup.get(groupKey).add(stat);}boolean hasAnyGroups = getJavaGroupList().size() > 1;ViewGroup vg = (ViewGroup) c.getView(); vg.removeAllViews();ScrollView sv = new ScrollView(context); LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL);for(java.util.Map.Entry<String, java.util.List<PlayerStat>> entry : statsByGroup.entrySet()){String groupName = entry.getKey();java.util.List<PlayerStat> groupStats = entry.getValue();Collections.sort(groupStats, new Comparator<PlayerStat>() { @Override public int compare(PlayerStat o1, PlayerStat o2) { return Integer.valueOf(o2.cleanSheets).compareTo(o1.cleanSheets); }});if (hasAnyGroups && !groupName.equals("_no_group_")) ml.addView(createGroupHeaderView(getLocalizedText(null, "group", lang) + " " + groupName, lang));ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, "clean_sheets", lang))); ml.addView(createDivider());for (PlayerStat stat : groupStats) {if (stat.cleanSheets > 0) { ml.addView(createTournamentStatRow(stat, stat.cleanSheets, lang)); ml.addView(createDivider()); }}}sv.addView(ml); vg.addView(sv);} catch(Exception e) { AfterParsingFail("Error calculating clean sheets: " + e.getMessage()); }}
    private void updateCleanSheetStat(java.util.Map<String, PlayerStat> stats, JSONObject teamInfo, JSONArray squad, String lang) throws JSONException {if (teamInfo == null) return;String teamId = teamInfo.getString("team_id");String teamName = getLocalizedText(teamInfo, "name", lang);String keeperName = findGoalkeeperInSquad(squad);String uniqueKey = (keeperName != null) ? (keeperName + "_" + teamId) : teamId;String displayName = (keeperName != null) ? keeperName : teamName;PlayerStat stat = stats.get(uniqueKey);if (stat == null) { stat = new PlayerStat(displayName, teamId, teamName); stats.put(uniqueKey, stat); }stat.cleanSheets++;}
    private String findGoalkeeperInSquad(JSONArray squad) throws JSONException {if (squad == null) return null;Pattern p = Pattern.compile("(.*?) *\\((?:ح\\.م|gk)\\)", Pattern.CASE_INSENSITIVE);for (int i = 0; i < squad.length(); i++) {Matcher m = p.matcher(squad.getString(i));if (m.find()) return m.group(1).trim();}return null;}
    private View createStatsHeaderRow(String lang, String title) {boolean isRTL = "ar".equalsIgnoreCase(lang);LinearLayout r = new LinearLayout(context); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(24, 16, 24, 16); r.setGravity(Gravity.CENTER_VERTICAL); r.setBackgroundColor(Color.parseColor("#F5F5F5"));if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);TextView pth = createTextView(getLocalizedText(null, "player", lang) + " / " + getLocalizedText(null, "team", lang), 0, 1, true); pth.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);TextView sh = createTextView(title, -2, 0, true); sh.setGravity(Gravity.CENTER);r.addView(pth); r.addView(sh); return r;}
    private View createTournamentStatRow(PlayerStat stat, int count, String lang) {boolean isRTL = "ar".equalsIgnoreCase(lang);LinearLayout r = new LinearLayout(context); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(24, 24, 24, 24); r.setGravity(Gravity.CENTER_VERTICAL);if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);LinearLayout ptl = new LinearLayout(context); ptl.setOrientation(LinearLayout.VERTICAL); ptl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));TextView pName = createTextView(stat.playerName, -1, 0, true); pName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); pName.setTextSize(16);TextView tName = createTextView(stat.teamName, -1, 0, false); tName.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); tName.setTextColor(Color.GRAY); tName.setTextSize(12);ptl.addView(pName); ptl.addView(tName);TextView sv = createTextView(String.valueOf(count), -2, 0, true); sv.setTextSize(18); sv.setMinWidth(120); sv.setGravity(Gravity.CENTER);r.addView(ptl); r.addView(sv); return r;}
    private void buildTeamListView(LinearLayout c, java.util.List<JSONObject> teamList, final String lang) throws JSONException {c.removeAllViews(); java.util.List<String> groups = getJavaGroupList();if (groups.isEmpty()) { for (JSONObject team : teamList) { c.addView(createTeamItemView(team, lang)); c.addView(createDivider()); } } else {for (String group : groups) {boolean headerAdded = false;for (JSONObject team : teamList) {if (group.equals(team.optString("group"))) {if (!headerAdded) { c.addView(createGroupHeaderView(getLocalizedText(null, "group", lang) + " " + group, lang)); headerAdded = true; }c.addView(createTeamItemView(team, lang)); c.addView(createDivider());}}}}}
    private View createTeamItemView(final JSONObject team, String lang) throws JSONException {final boolean isRTL = "ar".equalsIgnoreCase(lang); final String teamId = team.getString("team_id");LinearLayout r = new LinearLayout(context); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(32, 24, 32, 24);if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { TeamClicked(teamId); } });ImageView logo = new ImageView(context); logo.setLayoutParams(new LinearLayout.LayoutParams(100, 100)); Picasso.with(context).load(team.optString("logo")).into(logo);TextView name = createTextView(getLocalizedText(team, "name", lang), 0, 1, true); name.setTextSize(16); name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); name.setPadding(16, 0, 16, 0);r.addView(logo); r.addView(name); return r;}
    private java.util.List<String> getJavaGroupList() {java.util.List<String> gl = new java.util.ArrayList<>();try {if (jsonData == null || !jsonData.has("teams")) return gl;JSONArray teams = jsonData.getJSONArray("teams");for (int i = 0; i < teams.length(); i++) {JSONObject item = teams.getJSONObject(i);if (item.has("group") && !item.isNull("group") && !item.getString("group").isEmpty()) {String group = item.getString("group"); if (!gl.contains(group)) gl.add(group);}}} catch (Exception e) {}Collections.sort(gl); return gl;}
    private void calculateAndDisplayTeamStats(HVArrangement c, String teamId, String lang, final String statType) {if (jsonData == null || teamId == null || teamId.isEmpty()) return;try {java.util.Map<String, PlayerStat> playerStats = new java.util.HashMap<>();JSONArray matches = jsonData.optJSONArray("matches");if (matches == null) return;JSONArray teams = jsonData.getJSONArray("teams");for (int i = 0; i < matches.length(); i++) {JSONObject match = matches.getJSONObject(i);if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;if (teamId.equals(match.getString("home_team_id"))) {JSONObject teamInfo = getTeamInfoById(teamId, teams);processTeamEvents(playerStats, match, "home_scorers", teamInfo, lang);}if (teamId.equals(match.getString("away_team_id"))) {JSONObject teamInfo = getTeamInfoById(teamId, teams);processTeamEvents(playerStats, match, "away_scorers", teamInfo, lang);}}ViewGroup vg = (ViewGroup) c.getView();vg.removeAllViews();ScrollView sv = new ScrollView(context);LinearLayout ml = new LinearLayout(context);ml.setOrientation(LinearLayout.VERTICAL);java.util.List<PlayerStat> sortedStats = new ArrayList<>(playerStats.values());Collections.sort(sortedStats, new Comparator<PlayerStat>() {@Override public int compare(PlayerStat o1, PlayerStat o2) {if ("goals".equals(statType)) {return Integer.valueOf(o2.goals).compareTo(o1.goals);} else {return Integer.valueOf(o2.assists).compareTo(o1.assists);}}});ml.addView(createStatsHeaderRow(lang, getLocalizedText(null, statType, lang)));ml.addView(createDivider());for (PlayerStat stat : sortedStats) {int count = "goals".equals(statType) ? stat.goals : stat.assists;if (count > 0) {ml.addView(createTournamentStatRow(stat, count, lang));ml.addView(createDivider());}}sv.addView(ml);vg.addView(sv);} catch(Exception e) {AfterParsingFail("Error calculating team stats: " + e.getMessage());}}
    private java.util.List<TeamStats> calculateStandingsForGroup(String gId, String stageId) throws JSONException {final JSONArray teams = jsonData.getJSONArray("teams");final JSONArray matches = jsonData.optJSONArray("matches");java.util.Map<String, TeamStats> statsMap = new java.util.HashMap<>();boolean hasGroupFilter = gId != null && !gId.isEmpty();boolean hasStageFilter = stageId != null && !stageId.isEmpty();for (int i = 0; i < teams.length(); i++) {JSONObject team = teams.getJSONObject(i);if (!hasGroupFilter || gId.equals(team.optString("group"))) {statsMap.put(team.getString("team_id"), new TeamStats(team.getString("team_id")));}}if (matches != null) {for (int i = 0; i < matches.length(); i++) {JSONObject match = matches.getJSONObject(i);if (hasStageFilter && !stageId.equals(match.optString("stage"))) { continue; }if (!"completed".equalsIgnoreCase(match.optString("status"))) { continue; }String homeId = match.getString("home_team_id");String awayId = match.getString("away_team_id");boolean homeInMap = statsMap.containsKey(homeId);boolean awayInMap = statsMap.containsKey(awayId);if (homeInMap || awayInMap) {int hs = match.getInt("home_score");int as = match.getInt("away_score");String pWinner = match.optString("penalty_winner_team_id");if (homeInMap) {TeamStats homeStats = statsMap.get(homeId);homeStats.matchesPlayed++; homeStats.goalsFor += hs; homeStats.goalsAgainst += as;if (hs > as) { homeStats.wins++; homeStats.points += 3; }else if (as > hs) { homeStats.losses++; }else {homeStats.draws++; homeStats.points++;if(pWinner.equals(homeId)) { homeStats.points++; homeStats.penaltyPoints++; }}}if (awayInMap) {TeamStats awayStats = statsMap.get(awayId);awayStats.matchesPlayed++;awayStats.goalsFor += as; awayStats.goalsAgainst += hs;if (as > hs) { awayStats.wins++; awayStats.points += 3; }else if (hs > as) { awayStats.losses++; }else {awayStats.draws++; awayStats.points++;if(pWinner.equals(awayId)) { awayStats.points++; awayStats.penaltyPoints++; }}}}}}if (statsMap.isEmpty()) return null;java.util.List<TeamStats> sorted = new java.util.ArrayList<>(statsMap.values());Collections.sort(sorted, new Comparator<TeamStats>() {@Override public int compare(TeamStats t1, TeamStats t2) {int pointsCompare = Integer.valueOf(t2.points).compareTo(t1.points);if (pointsCompare != 0) return pointsCompare;int h2hPoints1 = 0, h2hPoints2 = 0;int h2hGF1 = 0, h2hGA1 = 0;try {if (matches != null) {for (int i = 0; i < matches.length(); i++) {JSONObject match = matches.getJSONObject(i);if (!"completed".equalsIgnoreCase(match.optString("status"))) continue;String homeId = match.getString("home_team_id");String awayId = match.getString("away_team_id");if ((homeId.equals(t1.teamId) && awayId.equals(t2.teamId)) || (homeId.equals(t2.teamId) && awayId.equals(t1.teamId))) {int hs = match.getInt("home_score");int as = match.getInt("away_score");if (homeId.equals(t1.teamId)) {h2hGF1 += hs; h2hGA1 += as;if (hs > as) { h2hPoints1 += 3; } else if (as > hs) { h2hPoints2 += 3; } else { h2hPoints1++; h2hPoints2++; }} else {h2hGF1 += as; h2hGA1 += hs;if (as > hs) { h2hPoints1 += 3; } else if (hs > as) { h2hPoints2 += 3; } else { h2hPoints1++; h2hPoints2++; }}}}}} catch (JSONException e) {}int h2hPointsCompare = Integer.valueOf(h2hPoints2).compareTo(h2hPoints1);if (h2hPointsCompare != 0) return h2hPointsCompare;int h2hGd1 = h2hGF1 - h2hGA1;int h2hGd2 = (h2hGF1 - h2hGA1) * -1;int h2hGdCompare = Integer.valueOf(h2hGd2).compareTo(h2hGd1);if (h2hGdCompare != 0) return h2hGdCompare;int gdc=Integer.valueOf(t2.getGoalDifference()).compareTo(t1.getGoalDifference()); if(gdc!=0)return gdc;int gfc=Integer.valueOf(t2.goalsFor).compareTo(t1.goalsFor); if(gfc!=0)return gfc;try { return getLocalizedText(getTeamInfoById(t1.teamId, teams), "name", "en").compareTo(getLocalizedText(getTeamInfoById(t2.teamId, teams), "name", "en")); } catch (JSONException e) { return 0; }}});for (int i = 0; i < sorted.size(); i++) sorted.get(i).position = i + 1;return sorted;}
    private View buildStandingsTable(java.util.List<TeamStats> sorted, String lang) throws JSONException {JSONArray teams = jsonData.getJSONArray("teams"); ScrollView vsv = new ScrollView(context); HorizontalScrollView hsv = new HorizontalScrollView(context);LinearLayout tl = new LinearLayout(context); tl.setOrientation(LinearLayout.VERTICAL); tl.setPadding(0,0,16,0);tl.addView(createHeaderRow(lang)); tl.addView(createDivider());for (TeamStats stats : sorted) { tl.addView(createDataRow(stats, teams, lang)); tl.addView(createDivider()); }hsv.addView(tl); vsv.addView(hsv); return vsv;}
    private View createHeaderRow(String lang) {boolean isRTL = "ar".equalsIgnoreCase(lang); LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL);l.setLayoutParams(new LinearLayout.LayoutParams(-2, -2)); l.setPadding(16, 16, 16, 16); l.setGravity(Gravity.CENTER);java.util.List<View> views = new java.util.ArrayList<>();views.add(createTextView("", 80, 0, true)); views.add(createTextView(getLocalizedText(null, "team", lang), 400, 0, true)); views.add(createTextView(getLocalizedText(null, "p", lang), 80, 0, true));views.add(createTextView(getLocalizedText(null, "pts", lang), 90, 0, true)); views.add(createTextView(getLocalizedText(null, "f:a", lang), 100, 0, true)); views.add(createTextView(getLocalizedText(null, "gd", lang), 80, 0, true));views.add(createTextView(getLocalizedText(null, "w", lang), 80, 0, true)); views.add(createTextView(getLocalizedText(null, "d", lang), 80, 0, true)); views.add(createTextView(getLocalizedText(null, "l", lang), 80, 0, true)); views.add(createTextView(getLocalizedText(null, "p.p", lang), 80, 0, true));if (isRTL) Collections.reverse(views); for(View v : views) l.addView(v); return l;}
    
    // --- MODIFIED: createDataRow with colored/bolded text ---
    private View createDataRow(TeamStats stats, JSONArray teamsData, String lang) throws JSONException {
        boolean isRTL = "ar".equalsIgnoreCase(lang); LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutParams(new LinearLayout.LayoutParams(-2, -2)); l.setPadding(16, 24, 16, 24); l.setGravity(Gravity.CENTER);
        JSONObject tInfo = getTeamInfoById(stats.teamId, teamsData); ImageView iv = new ImageView(context); iv.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        String tName = getLocalizedText(tInfo, "name", lang); if (tInfo != null) Picasso.with(context).load(tInfo.optString("logo")).into(iv);
        LinearLayout tl = new LinearLayout(context); tl.setOrientation(LinearLayout.HORIZONTAL); tl.setLayoutParams(new LinearLayout.LayoutParams(400, -2)); tl.setGravity((isRTL ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        TextView tnv = createTextView(tName, -2, 0, true);
        if (isRTL) { tl.addView(tnv); tl.addView(iv); } else { tl.addView(iv); tl.addView(tnv); }
        String faScore = isRTL ? String.format(Locale.US, "%d:%d", stats.goalsAgainst, stats.goalsFor) : String.format(Locale.US, "%d:%d", stats.goalsFor, stats.goalsAgainst);
        
        TextView pointsView = createTextView(String.valueOf(stats.points), 90, 0, true);
        pointsView.setTextColor(Color.parseColor("#B71C1C")); // Dark Red

        TextView gdView = createTextView(String.format(Locale.US, "%+d", stats.getGoalDifference()), 80, 0, true); // Made Bold
        gdView.setTextColor(Color.parseColor("#0D47A1")); // Navy Blue
        
        java.util.List<View> views = new java.util.ArrayList<>();
        views.add(createTextView(String.valueOf(stats.position), 80, 0, false)); views.add(tl); views.add(createTextView(String.valueOf(stats.matchesPlayed), 80, 0, false));
        views.add(pointsView); views.add(createTextView(faScore, 100, 0, false)); views.add(gdView);
        views.add(createTextView(String.valueOf(stats.wins), 80, 0, false)); views.add(createTextView(String.valueOf(stats.draws), 80, 0, false)); views.add(createTextView(String.valueOf(stats.losses), 80, 0, false)); views.add(createTextView(String.valueOf(stats.penaltyPoints), 80, 0, false));
        if (isRTL) Collections.reverse(views); for(View v : views) l.addView(v); return l;
    }
    
    private View createDateHeaderView(String dateStr, int count, String lang, boolean showWeek, String weekNum) throws JSONException, ParseException {boolean isRTL = "ar".equalsIgnoreCase(lang); LinearLayout h = new LinearLayout(context); h.setOrientation(LinearLayout.HORIZONTAL); h.setPadding(24, 12, 24, 12); h.setGravity(Gravity.CENTER_VERTICAL); h.setBackgroundColor(Color.parseColor("#F5F5F5"));String dateText; SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US); Date matchDate = parser.parse(dateStr);Calendar matchCal = Calendar.getInstance(); matchCal.setTime(matchDate);Calendar today = Calendar.getInstance(); Calendar yesterday = Calendar.getInstance(); yesterday.add(Calendar.DATE, -1); Calendar tomorrow = Calendar.getInstance(); tomorrow.add(Calendar.DATE, 1);today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);yesterday.set(Calendar.HOUR_OF_DAY, 0); yesterday.set(Calendar.MINUTE, 0); yesterday.set(Calendar.SECOND, 0); yesterday.set(Calendar.MILLISECOND, 0);tomorrow.set(Calendar.HOUR_OF_DAY, 0); tomorrow.set(Calendar.MINUTE, 0); tomorrow.set(Calendar.SECOND, 0); tomorrow.set(Calendar.MILLISECOND, 0);matchCal.set(Calendar.HOUR_OF_DAY, 0); matchCal.set(Calendar.MINUTE, 0); matchCal.set(Calendar.SECOND, 0); matchCal.set(Calendar.MILLISECOND, 0);if(today.getTime().equals(matchCal.getTime())) { dateText = getLocalizedText(null, "today", lang); } else if (yesterday.getTime().equals(matchCal.getTime())) { dateText = getLocalizedText(null, "yesterday", lang); }else if (tomorrow.getTime().equals(matchCal.getTime())) { dateText = getLocalizedText(null, "tomorrow", lang); } else { SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy", isRTL ? new Locale("ar") : Locale.US); dateText = sdf.format(matchDate); }TextView dl = createTextView(dateText, 0, 2, true); dl.setTextSize(12);String gamesText = (count == 1) ? getLocalizedText(null, "game_one", lang) : count + " " + getLocalizedText(null, "games", lang);TextView cl = createTextView(gamesText, 0, 1, true); cl.setTextSize(12);if(showWeek) {TextView wl = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, 0, 1, false);wl.setGravity(isRTL ? Gravity.END : Gravity.START);cl.setGravity(isRTL ? Gravity.START : Gravity.END);if (isRTL) { h.addView(cl); h.addView(dl); h.addView(wl); } else { h.addView(wl); h.addView(dl); h.addView(cl); }} else {cl.setGravity(isRTL ? Gravity.START : Gravity.END);TextView datePlaceholder = createTextView("", 0, 1, false);if (isRTL) { h.addView(cl); h.addView(dl); h.addView(datePlaceholder); } else { h.addView(datePlaceholder); h.addView(dl); h.addView(cl); }}return h;}
    private View createWeekHeaderView(String weekNum, String lang){TextView weekLabel = createTextView(getLocalizedText(null, "week", lang) + " " + weekNum, -1, 0, true);weekLabel.setBackgroundColor(Color.parseColor("#FAFAFA")); weekLabel.setPadding(24, 8, 24, 8); weekLabel.setTextSize(14); weekLabel.setGravity(Gravity.CENTER);return weekLabel;}
    private View createGroupHeaderView(String name, String lang) {TextView label = createTextView(name, -1, 0, true); label.setBackgroundColor(Color.parseColor("#EEEEEE")); label.setPadding(24, 8, 24, 8); label.setTextSize(16); label.setTypeface(null, Typeface.BOLD); label.setGravity(Gravity.CENTER);LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 8, 0, 8); label.setLayoutParams(p); return label;}
    private View createMatchItemView(JSONObject match, JSONArray teams, final String lang) throws JSONException, ParseException {final boolean isRTL = "ar".equalsIgnoreCase(lang); final String matchId = match.getString("match_id");LinearLayout card = new LinearLayout(context); LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(24, 8, 24, 16); card.setLayoutParams(cp); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(16, 16, 16, 16);GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(24); bg.setStroke(2, Color.parseColor("#F0F0F0"));if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg); else card.setBackgroundDrawable(bg);if (Build.VERSION.SDK_INT >= 21) card.setElevation(4);card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { MatchClicked(matchId); } });TextView top = createTextView("", -1, 0, false); top.setTextSize(12); top.setTextColor(Color.GRAY); top.setGravity(Gravity.CENTER);LinearLayout mid = new LinearLayout(context); mid.setOrientation(LinearLayout.HORIZONTAL); mid.setGravity(Gravity.CENTER_VERTICAL); mid.setPadding(0, 8, 0, 8);TextView center = createTextView("", -2, 0, true); center.setTextSize(20); center.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);TextView bottom = createTextView("", -1, 0, false); bottom.setTextSize(12); bottom.setTextColor(Color.GRAY); bottom.setGravity(Gravity.CENTER);LinearLayout homeLayout = createTeamLayout(getTeamInfoById(match.getString("home_team_id"), teams), lang);LinearLayout awayLayout = createTeamLayout(getTeamInfoById(match.getString("away_team_id"), teams), lang);if (isRTL) { mid.addView(awayLayout); mid.addView(center, new LinearLayout.LayoutParams(0, -2, 1)); mid.addView(homeLayout); }else { mid.addView(homeLayout); mid.addView(center, new LinearLayout.LayoutParams(0, -2, 1)); mid.addView(awayLayout); }String status = match.optString("status", "upcoming").toLowerCase();if ("completed".equals(status)) {top.setText(getLocalizedText(null, "completed", lang));String score = isRTL ? String.format(Locale.US, "%d : %d", match.getInt("away_score"), match.getInt("home_score")) : String.format(Locale.US, "%d : %d", match.getInt("home_score"), match.getInt("away_score"));center.setText(score); String note = getLocalizedText(match, "note", lang);if (!note.isEmpty()) bottom.setText(note); else bottom.setVisibility(View.GONE);} else if ("postponed".equals(status) || "delayed".equals(status)) {top.setText(getLocalizedText(null, status, lang)); center.setText(match.optString("time", "-")); bottom.setText(getLocalizedText(match, "venue", lang));} else {Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(match.getString("date"));top.setText(new SimpleDateFormat("dd MMM", isRTL ? new Locale("ar") : Locale.US).format(date));center.setText(match.optString("time", "-")); bottom.setText(getLocalizedText(match, "venue", lang));}card.addView(top); card.addView(mid); card.addView(bottom); return card;}
    private LinearLayout createTeamLayout(JSONObject tInfo, String lang) throws JSONException {LinearLayout l = new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER_HORIZONTAL); l.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 2));ImageView logo = new ImageView(context); logo.setLayoutParams(new LinearLayout.LayoutParams(120, 120));if (tInfo != null) Picasso.with(context).load(tInfo.optString("logo")).into(logo);TextView name = createTextView(getLocalizedText(tInfo, "name", lang), -2, 0, true); name.setTextSize(14); name.setPadding(0, 8, 0, 0);l.addView(logo); l.addView(name); return l;}
    private String getLocalizedText(JSONObject o, String key, String lang) {if (o == null) {boolean isAR = "ar".equalsIgnoreCase(lang);if ("today".equals(key)) return isAR ? "اليوم" : "Today";if ("yesterday".equals(key)) return isAR ? "أمس" : "Yesterday";if ("tomorrow".equals(key)) return isAR ? "غدا" : "Tomorrow";if ("team".equals(key)) return isAR ? "الفريق" : "Team";if ("pts".equals(key)) return isAR ? "نقاط" : "Points";if ("p".equals(key)) return isAR ? "لعب" : "P";if ("w".equals(key)) return isAR ? "ف" : "W";if ("d".equals(key)) return isAR ? "ت" : "D";if ("l".equals(key)) return isAR ? "ه" : "L";if ("f:a".equals(key)) return isAR ? "له/عليه" : "F:A";if ("gd".equals(key)) return isAR ? "فارق" : "+/-";if ("p.p".equals(key)) return isAR ? "ر.ت" : "P.P";if ("week".equals(key)) return isAR ? "الأسبوع" : "Week";if ("games".equals(key)) return isAR ? "مباريات" : "Games";if ("game_one".equals(key)) return isAR ? "مباراة واحدة" : "1 Game";if ("completed".equals(key)) return isAR ? "انتهت" : "Completed";if ("postponed".equals(key)) return isAR ? "مؤجلة" : "Postponed";if ("delayed".equals(key)) return isAR ? "تأجلت" : "Delayed";if ("group".equals(key)) return isAR ? "المجموعة" : "Group";if ("search".equals(key)) return isAR ? "ابحث..." : "Search...";if ("city".equals(key)) return isAR ? "المنطقة" : "City";if ("field".equals(key)) return isAR ? "الملعب" : "Field";if ("information".equals(key)) return isAR ? "معلومات" : "Information";if ("coach".equals(key)) return isAR ? "الادارة الفنية" : "Coach";if ("goalkeepers".equals(key)) return isAR ? "حراس المرمي" : "Goalkeepers";if ("defenders".equals(key)) return isAR ? "المدافعون" : "Defenders";if ("midfielders".equals(key)) return isAR ? "لاعبو الوسط" : "Midfielders";if ("attackers".equals(key)) return isAR ? "المهاجمون" : "Attackers";if ("player".equals(key)) return isAR ? "اللاعب" : "Player";if ("goals".equals(key)) return isAR ? "الأهداف" : "Goals";if ("assists".equals(key)) return isAR ? "صناعة الأهداف" : "Assists";if ("clean_sheets".equals(key)) return isAR ? "شباك نظيفة" : "Clean Sheets";if ("lineup".equals(key)) return isAR ? "تشكيل الفريق" : "Lineup";if ("scorers_list".equals(key)) return isAR ? "مسجلي الأهداف" : "Match Scorers";if ("yellow_cards".equals(key)) return isAR ? "البطاقات الصفراء" : "Yellow Cards";if ("red_cards".equals(key)) return isAR ? "البطاقات الحمراء" : "Red Cards";if ("substitutions".equals(key)) return isAR ? "التبديلات" : "Substitutions";if("assists_delimiter".equals(key)) return isAR ? "صناعة الاهداف" : "Assists";return key;}if (!o.has(key) || o.isNull(key)) return "";try {Object v = o.get(key); if (v instanceof String) return (String) v;if (v instanceof JSONObject) {JSONObject lObj = (JSONObject) v; if (lObj.has(lang)) return lObj.getString(lang); return lObj.optString("en");}return v.toString();} catch (JSONException e) { return ""; }}
    private String getEnglishKeyFor(String title, String lang) {if ("ar".equalsIgnoreCase(lang)) {if (title.equals("المنطقة")) return "city"; if (title.equals("الملعب")) return "field"; if (title.equals("معلومات")) return "information";}return title.toLowerCase();}
    private JSONObject getTeamInfoById(String id, JSONArray data) throws JSONException {for (int i = 0; i < data.length(); i++) { if (data.getJSONObject(i).getString("team_id").equals(id)) return data.getJSONObject(i); } return null;}
    private String getMatchGroupName(JSONObject match) throws JSONException {String group = match.optString("group", null);if (group != null && !group.isEmpty() && !group.equalsIgnoreCase("null")) return group;JSONObject teamInfo = getTeamInfoById(match.getString("home_team_id"), jsonData.getJSONArray("teams"));if (teamInfo != null && teamInfo.has("group") && !teamInfo.isNull("group")) {String teamGroup = teamInfo.getString("group");if (teamGroup != null && !teamGroup.isEmpty()) return teamGroup;}return null;}
    private void addInfoRow(LinearLayout c, String title, String value, final String url, String lang) {if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) return;boolean isRTL = "ar".equalsIgnoreCase(lang);LinearLayout r = new LinearLayout(context); r.setPadding(32, 24, 32, 24);if ("information".equals(getEnglishKeyFor(title, lang))) {r.setOrientation(LinearLayout.VERTICAL);TextView tv = createTextView(title, -1, 0, true); tv.setGravity(Gravity.CENTER);TextView vv = createTextView(value, -1, 0, false); vv.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT); vv.setPadding(0, 16, 0, 0);r.addView(tv); r.addView(vv);} else {r.setOrientation(LinearLayout.HORIZONTAL);if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) r.setLayoutDirection(isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);TextView tv = createTextView(title, 0, 1, true); tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);TextView vv = createTextView(value, 0, 2, false); vv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);if (url != null && !url.isEmpty() && !"null".equalsIgnoreCase(url)) {vv.setTextColor(Color.BLUE);r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {} } });}r.addView(tv); r.addView(vv);}c.addView(r); c.addView(createDivider());}
    private void addPlayerSection(LinearLayout c, JSONObject players, String key, String lang) {String title = getLocalizedText(null, key, lang); String pListRaw = getLocalizedText(players, key, lang); if (pListRaw == null || pListRaw.isEmpty() || "null".equalsIgnoreCase(pListRaw)) return;String pList = pListRaw.replaceAll("[\"\\[\\]\\\\]", "").replace(",", "\n"); if (pList.trim().isEmpty()) return;TextView tv = createTextView(title, -1, 0, true); tv.setTextSize(16); tv.setPadding(32, 24, 32, 8); tv.setGravity(Gravity.CENTER); tv.setBackgroundColor(Color.parseColor("#F5F5F5"));TextView pv = createTextView(pList, -1, 0, false); pv.setPadding(32, 16, 32, 24); pv.setGravity(Gravity.CENTER);c.addView(tv); c.addView(pv);}
    private TextView createTextView(String text, int width, float weight, boolean isBold) {TextView tv = new TextView(context); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, -2, weight);if (width == 0) p.width = 0; p.setMargins(8, 0, 8, 0); tv.setLayoutParams(p); tv.setText(text); tv.setTextColor(Color.BLACK); tv.setGravity(Gravity.CENTER);if (isBold) tv.setTypeface(null, Typeface.BOLD); return tv;}
    private View createDivider() {View d = new View(context); d.setLayoutParams(new LinearLayout.LayoutParams(-1, 1)); d.setBackgroundColor(Color.parseColor("#E0E0E0")); return d;}
    private View createEventHeader(String title, String iconFilename, String lang) {boolean isRTL = "ar".equalsIgnoreCase(lang);LinearLayout headerLayout = new LinearLayout(context);headerLayout.setOrientation(LinearLayout.HORIZONTAL);headerLayout.setGravity(Gravity.CENTER);headerLayout.setPadding(0, dpToPx(8), 0, dpToPx(8));if (isRTL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) headerLayout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);ImageView icon = new ImageView(context);icon.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)));try (InputStream is = context.getAssets().open(iconFilename)) {Drawable d = Drawable.createFromStream(is, null);icon.setImageDrawable(d);} catch (Exception e) {}TextView titleView = createTextView(title, ViewGroup.LayoutParams.WRAP_CONTENT, 0, true);titleView.setTextColor(Color.parseColor("#B71C1C"));titleView.setTextSize(16);LinearLayout.LayoutParams titleParams = (LinearLayout.LayoutParams) titleView.getLayoutParams();titleParams.setMargins(dpToPx(8), 0, dpToPx(8), 0);headerLayout.addView(icon);headerLayout.addView(titleView);return headerLayout;}
}