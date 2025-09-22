package com.waellotfy.footballdataplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.*;
import com.squareup.picasso.Picasso;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@DesignerComponent(version = 19, description = "Companion with news, ads, and multi-level season/competition navigation.", category = ComponentCategory.EXTENSION, nonVisible = true, iconName = "images/extension.png")
@SimpleObject(external = true)
// @UsesLibraries(libraries = "picasso-2.5.2.jar") // <-- FIX: REMOVED THIS LINE
public class Footballdataplus extends AndroidNonvisibleComponent implements Component {

    private final Context context;
    private JSONObject jsonData;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "FootballDataPlusPrefs";
    private static final String LAST_NEWS_COUNT_KEY = "lastNewsCount";
    private static final String SHOWN_ADS_KEY = "shownAds";
    private final Form form;

    public Footballdataplus(ComponentContainer container) {
        super(container.$form());
        this.context = container.$context();
        this.form = container.$form();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @SimpleEvent(description = "Triggered when new news items are found.")
    public void NewNewsFound(int newCount, String message) { EventDispatcher.dispatchEvent(this, "NewNewsFound", newCount, message); }
    @SimpleEvent(description = "Triggered when JSON data is loaded successfully.")
    public void AfterParsingSuccess() { EventDispatcher.dispatchEvent(this, "AfterParsingSuccess"); }
    @SimpleEvent(description = "Triggered if the JSON data string is invalid.")
    public void AfterParsingFail(String error) { EventDispatcher.dispatchEvent(this, "AfterParsingFail", error); }
    
    @SimpleEvent(description = "Triggered when a season is clicked.")
    public void SeasonClicked(String seasonName) { EventDispatcher.dispatchEvent(this, "SeasonClicked", seasonName); }
    @SimpleEvent(description = "Triggered when a competition is clicked.")
    public void CompetitionClicked(String competitionId, String competitionName, boolean hasAges) { EventDispatcher.dispatchEvent(this, "CompetitionClicked", competitionId, competitionName, hasAges); }
    @SimpleEvent(description = "Triggered when an age group is clicked.")
    public void AgeClicked(String competitionId, String age, boolean hasSectors, String matchesUrlOrNull) { EventDispatcher.dispatchEvent(this, "AgeClicked", competitionId, age, hasSectors, matchesUrlOrNull); }
    @SimpleEvent(description = "Triggered when a sector is clicked.")
    public void SectorClicked(String sectorName, String matchesUrl) { EventDispatcher.dispatchEvent(this, "SectorClicked", sectorName, matchesUrl); }


    @SimpleFunction(description = "Loads JSON data from a string. Must be called first.")
    public void SetJsonDataFromString(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) { AfterParsingFail("Input JSON string is empty."); return; }
        try {
            this.jsonData = new JSONObject(jsonString);
            AfterParsingSuccess();
        } catch (JSONException e) {
            this.jsonData = null;
            AfterParsingFail("JSON Parsing Error: " + e.getMessage());
        }
    }

    // ================== COMPETITION BLOCKS ==================
    
    @SimpleFunction(description = "Creates a searchable list of seasons. This is the new first step.")
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
        } catch (Exception e) { AfterParsingFail("Error creating season list: " + e.getMessage()); }
    }
    
    @SimpleFunction(description = "Creates a searchable list of competitions for a selected season.")
    public void CreateCompetitionList(HVArrangement container, String seasonName, final String language) {
        if (jsonData == null) return;
        try {
            JSONObject season = findObjectInSeasons(jsonData, "season", seasonName);
            if (season == null) return;
            final JSONArray competitions = season.optJSONArray("competitions");
            if (competitions == null || competitions.length() == 0) return;
            createSearchableListView(container, language, "competition", competitions, null, null);
        } catch (Exception e) { AfterParsingFail("Error creating competition list: " + e.getMessage()); }
    }
    
    @SimpleFunction(description = "Creates a list of age groups for a given competition.")
    public void CreateAgeList(HVArrangement container, String competitionId, final String language) {
        if (jsonData == null) return;
        try {
            JSONObject competition = findCompetitionById(competitionId);
            if (competition == null) return;
            final JSONArray ages = competition.optJSONArray("ages");
            if (ages == null || ages.length() == 0) return;
            createSearchableListView(container, language, "age", ages, competitionId, null);
        } catch (Exception e) { AfterParsingFail("Error creating age list: " + e.getMessage()); }
    }

    @SimpleFunction(description = "Creates a searchable list of sectors for a given competition and age.")
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
            for(int i = 0; i < sectors.length(); i++) {
                if (i < urls.length()) {
                    JSONObject item = new JSONObject();
                    item.put("name", sectors.getString(i));
                    item.put("url", urls.getString(i));
                    customList.put(item);
                }
            }
            createSearchableListView(container, language, "sector", customList, competitionId, age);
        } catch (Exception e) { AfterParsingFail("Error creating sector list: " + e.getMessage()); }
    }

    // ================== OTHER BLOCKS (GLOBAL DATA) ==================
    
    @SimpleFunction(description = "Saves the current number of news items as the last seen count.")
    public void UpdateLastNewsCount() {
        if (jsonData == null || !jsonData.has("news")) return;
        try {
            JSONArray newsArray = jsonData.getJSONArray("news");
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(LAST_NEWS_COUNT_KEY, newsArray.length());
            editor.apply();
        } catch (JSONException e) {}
    }

    @SimpleFunction(description = "Creates a list of news articles, scrolling to the first unread item.")
    public void CreateNewsList(HVArrangement container, final String language) {
        if (jsonData == null) return;
        ViewGroup vg = (ViewGroup) container.getView(); vg.removeAllViews();
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
            LinearLayout ml = new LinearLayout(context); ml.setOrientation(LinearLayout.VERTICAL); ml.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            View firstNewItemView = null;
            for (int i = 0; i < newsArray.length(); i++) {
                View newsCard = createNewsCardView(newsArray.getJSONObject(i), language);
                ml.addView(newsCard);
                if (i == lastSeenCount) firstNewItemView = newsCard;
            }
            sv.addView(ml); vg.addView(sv);
            final View targetView = firstNewItemView;
            if (targetView != null) {
                sv.post(new Runnable() { @Override public void run() { sv.smoothScrollTo(0, targetView.getTop()); }});
            }
        } catch (Exception e) { AfterParsingFail("Error creating news list: " + e.getMessage()); }
    }

    @SimpleFunction(description = "Creates a searchable list of venues.")
    public void CreateVenueList(HVArrangement container, final String language) {
        if (jsonData == null) return;
        try {
            final JSONArray venuesArray = jsonData.optJSONArray("venues");
            if (venuesArray == null || venuesArray.length() == 0) return;
             createSearchableListView(container, language, "venue", venuesArray, null, null);
        } catch (Exception e) { AfterParsingFail("Error creating venue list: " + e.getMessage()); }
    }

    @SimpleFunction(description = "Displays a random, non-expired, non-repeated ad in full screen.")
    public void CreateRandomAd(HVArrangement container) {
        if (jsonData == null) return;
        ViewGroup vg = (ViewGroup) container.getView(); vg.removeAllViews();
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
                    } catch (Exception e) { validAds.add(ad); }
                } else { validAds.add(ad); }
            }
            if (validAds.isEmpty()) return;
            List<String> shownAds = new ArrayList<>(Arrays.asList(prefs.getString(SHOWN_ADS_KEY, "").split(",")));
            List<JSONObject> unseenAds = new ArrayList<>();
            for (JSONObject ad : validAds) {
                if (!shownAds.contains(ad.optString("name"))) { unseenAds.add(ad); }
            }
            if (unseenAds.isEmpty() && !validAds.isEmpty()) {
                prefs.edit().putString(SHOWN_ADS_KEY, "").apply();
                unseenAds = validAds;
            }
            if (unseenAds.isEmpty()) return;
            JSONObject randomAd = unseenAds.get(new Random().nextInt(unseenAds.size()));
            vg.addView(createAdCardView(randomAd));
            String newShownAds = prefs.getString(SHOWN_ADS_KEY, "") + randomAd.optString("name") + ",";
            prefs.edit().putString(SHOWN_ADS_KEY, newShownAds).apply();
        } catch (Exception e) { AfterParsingFail("Error creating ad view: " + e.getMessage()); }
    }
    
    // ================== HELPER METHODS ==================
    private int dpToPx(int dp) { return (int) (dp * context.getResources().getDisplayMetrics().density); }
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
        if(seasonsObj instanceof JSONArray) {
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
    private View createAdCardView(JSONObject ad) { /* Unchanged */
        RelativeLayout adLayout=new RelativeLayout(context);adLayout.setLayoutParams(new RelativeLayout.LayoutParams(-1,-1));adLayout.setBackgroundColor(Color.BLACK);ImageView adImage=new ImageView(context);RelativeLayout.LayoutParams imageParams=new RelativeLayout.LayoutParams(-1,-2);imageParams.addRule(RelativeLayout.CENTER_IN_PARENT);adImage.setLayoutParams(imageParams);adImage.setAdjustViewBounds(true);adImage.setScaleType(ImageView.ScaleType.FIT_CENTER);Picasso.with(context).load(ad.optString("image")).into(adImage);adLayout.addView(adImage);TextView adName=new TextView(context);adName.setText(getLocalizedText(ad,"name","ar"));adName.setTextSize(18);adName.setTypeface(null,Typeface.BOLD);adName.setTextColor(Color.WHITE);adName.setGravity(Gravity.CENTER);adName.setBackgroundColor(Color.parseColor("#80000000"));adName.setPadding(dpToPx(8),dpToPx(8),dpToPx(8),dpToPx(8));RelativeLayout.LayoutParams nameParams=new RelativeLayout.LayoutParams(-1,-2);nameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);adLayout.addView(adName,nameParams);LinearLayout actions=new LinearLayout(context);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setBackgroundColor(Color.parseColor("#80000000"));actions.setPadding(dpToPx(8),dpToPx(8),dpToPx(8),dpToPx(8));RelativeLayout.LayoutParams actionParams=new RelativeLayout.LayoutParams(-1,-2);actionParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);adLayout.addView(actions,actionParams);String locationUrl=ad.optString("location_url",null);if(isValid(locationUrl)){LinearLayout locGroup=new LinearLayout(context);locGroup.setOrientation(LinearLayout.HORIZONTAL);locGroup.setGravity(Gravity.CENTER_VERTICAL);locGroup.setPadding(dpToPx(8),0,dpToPx(8),0);locGroup.setOnClickListener(createLinkClickListener(locationUrl));locGroup.addView(createIcon("location_icon.png"));TextView locText=new TextView(context);locText.setText(getLocalizedText(ad,"location","ar"));locText.setTextColor(Color.WHITE);locText.setTextSize(14);locText.setPadding(dpToPx(4),0,0,0);locGroup.addView(locText);actions.addView(locGroup);}
        LinearLayout iconGroup=new LinearLayout(context);iconGroup.setOrientation(LinearLayout.HORIZONTAL);iconGroup.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);iconGroup.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));addClickableIcon(iconGroup,ad,"youtube_video","yt_icon.png",null);addClickableIcon(iconGroup,ad,"facebook_link","fb_icon.png",null);addClickableIcon(iconGroup,ad,"mobile_number","phone_icon.png","tel:");addClickableIcon(iconGroup,ad,"whatsapp_number","whatsapp_icon.png","https://wa.me/");actions.addView(iconGroup);return adLayout;
    }
    private void addClickableIcon(ViewGroup parent, JSONObject source, String key, String iconName, String uriPrefix) {
        String value=source.optString(key,null);if(isValid(value)){ImageView icon=createIcon(iconName);parent.addView(icon);final String finalValue=(uriPrefix!=null)?uriPrefix+value:value;icon.setOnClickListener(createLinkClickListener(finalValue));}
    }
    private boolean isValid(String value) { return value != null && !value.isEmpty() && !value.equalsIgnoreCase("null"); }
    private View.OnClickListener createLinkClickListener(final String url) {
        return new View.OnClickListener() { @Override public void onClick(View v) { try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {} } };
    }
    private ImageView createIcon(String assetName) {
        ImageView icon=new ImageView(context);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dpToPx(32),dpToPx(32));params.setMargins(dpToPx(8),0,dpToPx(8),0);icon.setLayoutParams(params);try {InputStream is=form.openAsset(assetName);Drawable d=Drawable.createFromStream(is,null);icon.setImageDrawable(d);} catch (java.io.IOException e) {} return icon;
    }
    private void createSearchableListView(HVArrangement container, final String language, final String type, final JSONArray dataArray, final String competitionId, final String age) {
        ViewGroup vg=(ViewGroup)container.getView();vg.removeAllViews();LinearLayout mainLayout=new LinearLayout(context);mainLayout.setOrientation(LinearLayout.VERTICAL);final LinearLayout listContent=new LinearLayout(context);listContent.setOrientation(LinearLayout.VERTICAL);buildClickableListView(listContent,dataArray,language,"",type,competitionId,age);EditText searchBar=new EditText(context);searchBar.setHint("...");searchBar.setTextColor(Color.BLACK);LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(-1,-2);searchParams.setMargins(dpToPx(16),dpToPx(16),dpToPx(16),dpToPx(8));searchBar.setLayoutParams(searchParams);searchBar.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){} @Override public void onTextChanged(CharSequence s,int start,int before,int count){buildClickableListView(listContent,dataArray,language,s.toString(),type,competitionId,age);} @Override public void afterTextChanged(Editable s){}});ScrollView scrollView=new ScrollView(context);scrollView.addView(listContent);mainLayout.addView(searchBar);mainLayout.addView(scrollView);vg.addView(mainLayout);
    }
    private void buildClickableListView(LinearLayout container, JSONArray dataArray, String language, String filter, final String type, final String competitionId, final String age) {
        container.removeAllViews();String filterLower=filter.toLowerCase();try {
            for(int i=0;i<dataArray.length();i++){
                final JSONObject item=dataArray.getJSONObject(i);final String name;
                if(type.equals("age"))name=item.optString("age");
                else if(type.equals("sector"))name=item.optString("name");
                else if(type.equals("season"))name=item.optString("season");
                else name=getLocalizedText(item,"name",language);
                if(name.toLowerCase().contains(filterLower)){
                    TextView itemView=new TextView(context);itemView.setText(name);itemView.setTextColor(Color.BLACK);itemView.setTextSize(16);itemView.setGravity(Gravity.CENTER);itemView.setPadding(dpToPx(16),dpToPx(16),dpToPx(16),dpToPx(16));itemView.setTypeface(null,Typeface.BOLD);
                    itemView.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){try {
                        if(type.equals("season")){SeasonClicked(name);}
                        else if(type.equals("competition")){String id=item.getString("competition_id");boolean hasAges=item.optJSONArray("ages")!=null;CompetitionClicked(id,name,hasAges);}
                        else if(type.equals("age")){String ageValue=item.getString("age");String url=item.optString("matchesurl","");boolean hasSectors=item.optJSONArray("matchesurl")!=null;if(hasSectors)url="";AgeClicked(competitionId,ageValue,hasSectors,url);}
                        else if(type.equals("sector")){SectorClicked(name,item.getString("url"));}
                        else if(type.equals("venue")){String url=item.optString("url");if(isValid(url))context.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
                    }catch(Exception e){}}});
                    container.addView(itemView);container.addView(createDivider());
                }
            }
        }catch(Exception e){}
    }
    private String getLocalizedText(JSONObject o, String key, String lang) {
        if(o==null||!o.has(key)||o.isNull(key))return"";try{Object v=o.get(key);if(v instanceof String)return(String)v;if(v instanceof JSONObject){JSONObject lObj=(JSONObject)v;if(lObj.has(lang))return lObj.getString(lang);return lObj.optString("en");}else if(v instanceof JSONArray){JSONArray arr=(JSONArray)v;if(arr.length()>0)return arr.getString(0);} return v.toString();}catch(JSONException e){return"";}
    }
    private JSONArray getLocalizedArray(JSONObject source, String key, String lang) {
        if(source==null||!source.has(key)||source.isNull(key))return null;try{Object data=source.get(key);if(data instanceof JSONArray)return(JSONArray)data;if(data instanceof JSONObject){JSONObject langObj=(JSONObject)data;if(langObj.has(lang)&&!langObj.isNull(lang))return langObj.getJSONArray(lang);if(langObj.has("en")&&!langObj.isNull("en"))return langObj.getJSONArray("en");if(langObj.has("ar")&&!langObj.isNull("ar"))return langObj.getJSONArray("ar");}}catch(JSONException e){return null;} return null;
    }
    private View createNewsCardView(JSONObject newsItem, String lang) throws JSONException {
        boolean isRTL = "ar".equalsIgnoreCase(lang);
        LinearLayout card = new LinearLayout(context); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
        card.setLayoutParams(params); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dpToPx(8)); bg.setStroke(dpToPx(1), Color.parseColor("#F0F0F0"));
        if (Build.VERSION.SDK_INT >= 16) card.setBackground(bg); else card.setBackgroundDrawable(bg);
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dpToPx(2));
        TextView titleView = new TextView(context); titleView.setText(getLocalizedText(newsItem, "title", lang)); titleView.setTextSize(18); titleView.setTypeface(null, Typeface.BOLD); titleView.setTextColor(Color.BLACK); titleView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
        TextView dateView = new TextView(context); dateView.setText(getLocalizedText(newsItem, "date", lang)); dateView.setTextSize(12); dateView.setTextColor(Color.GRAY); dateView.setPadding(0, dpToPx(4), 0, dpToPx(8)); dateView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
        TextView detailsView = new TextView(context); detailsView.setText(getLocalizedText(newsItem, "details", lang).trim()); detailsView.setTextSize(14); detailsView.setTextColor(Color.DKGRAY); detailsView.setLineSpacing(0, 1.2f); detailsView.setGravity(isRTL ? Gravity.RIGHT : Gravity.LEFT);
        card.addView(titleView); card.addView(dateView); card.addView(detailsView);
        return card;
    }
    private View createDivider() {
        View d = new View(context); d.setLayoutParams(new LinearLayout.LayoutParams(-1, 1)); d.setBackgroundColor(Color.parseColor("#E0E0E0")); return d;
    }
}
