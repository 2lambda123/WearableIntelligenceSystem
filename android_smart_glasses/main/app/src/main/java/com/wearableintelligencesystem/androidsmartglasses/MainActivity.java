package com.wearableintelligencesystem.androidsmartglasses;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.example.wearableintelligencesystemandroidsmartglasses.BuildConfig;
import com.example.wearableintelligencesystemandroidsmartglasses.R;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.wearableintelligencesystem.androidsmartglasses.archive.GlboxClientSocket;
import com.wearableintelligencesystem.androidsmartglasses.comms.MessageTypes;
import com.wearableintelligencesystem.androidsmartglasses.ui.ASGFragment;
import com.wearableintelligencesystem.androidsmartglasses.ui.ReferenceUi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class MainActivity extends AppCompatActivity {
    public WearableAiService mService;
    public boolean mBound = false;

    boolean currentlyScrolling = false;

    public static String TAG = "WearableAiDisplay_MainActivity";

    public long lastFaceUpdateTime = 0;
    public long faceUpdateInterval = 5000; //milliseconds

    public final static String ACTION_UI_UPDATE = "com.example.wearableaidisplaymoverio.UI_UPDATE";
    public final static String ACTION_TEXT_REFERENCE = "com.example.wearableaidisplaymoverio.ACTION_TEXT_REFERENCE";
    public final static String PHONE_CONN_STATUS_UPDATE = "com.example.wearableaidisplaymoverio.PHONE_CONN_STATUS_UPDATE";
    public final static String WIFI_CONN_STATUS_UPDATE = "com.example.wearableaidisplaymoverio.WIFI_CONN_STATUS_UPDATE";
    public final static String BATTERY_CHARGING_STATUS_UPDATE = "com.example.wearableaidisplaymoverio.BATTERY_CHARGIN_STATUS_UPDATE";
    public final static String BATTERY_LEVEL_STATUS_UPDATE = "com.example.wearableaidisplaymoverio.BATTERY_LEVEL_STATUS_UPDATE";

    //current person recognized's names
    public ArrayList<String> faceNames = new ArrayList<String>();

    //social UI
    TextView messageTextView;
    TextView eyeContactMetricTextView;
    TextView eyeContact5MetricTextView;
    TextView eyeContact30MetricTextView;
    TextView facialEmotionMetricTextView;
    TextView facialEmotion5MetricTextView;
    TextView facialEmotion30MetricTextView;
    Button toggleCameraButton;
    private PieChart chart;

    //text list ui
    ArrayList<String> textListHolder = new ArrayList<>();
    TextView textListView;

    //text list ui
    private String textBlockHolder = "";
    TextView textBlockView;

    //metrics
    float eye_contact_30 = 0;
    String facial_emotion_30 = "";
    String facial_emotion_5 = "";

    //save current mode
    String curr_mode = "";

    //HUD ui
    private TextView mClockTextView;
    private boolean clockRunning = false;
    private ImageView mWifiStatusImageView;
    private ImageView mPhoneStatusImageView;
    private ImageView mBatteryStatusImageView;

    //device status
    private TextView mBatteryStatusTextView;
    boolean phoneConnected = false;
    boolean wifiConnected;
    boolean batteryFull;
    float batteryLevel;
    boolean batteryCharging = false;

    private Handler uiHandler;
    private Handler navHandler;

    private NavController navController;

    TextView modeStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate running");

        super.onCreate(savedInstanceState);

        //help us find potential issues in dev
        if (BuildConfig.DEBUG){
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        //setup ui handler
        uiHandler = new Handler();

        //setup nav handler
        navHandler = new Handler();

        //set main view
        setContentView(R.layout.activity_main);
        modeStatus = findViewById(R.id.mode_hud);

        //setup the nav bar
//        Log.d(TAG, getSupportFragmentManager().toString());
//        Log.d(TAG, getSupportFragmentManager().getFragments().toString());
////        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
//        NavHostFragment navHostFragment = (NavHostFragment) NavHostFragment.findNavController();
//        Log.d(TAG, navHostFragment.toString());
//        navController = navHostFragment.getNavController();
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        //setup main view
        switchMode(MessageTypes.MODE_LIVE_LIFE_CAPTIONS);

        //create the WearableAI service if it isn't already running
        startWearableAiService();
    }

    private void turnOffScreen(){
        screenBrightnessControl(0);
    }

    private void turnOnScreen(){
        screenBrightnessControl(-1);
    }

    private void screenBrightnessControl(float brightness){
        //turn screen brightness to 0;
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = brightness;
        getWindow().setAttributes(params);
    }

    private void setupHud(){
        //setup clock
        if (! clockRunning) {
            clockRunning = true;
            uiHandler.post(new Runnable() {
                public void run() {
                    // Default time format for current locale, with respect (on API 22+) to user's 12/24-hour
                    // settings. I couldn't find any simple way to respect it back to API 14.
                    //String prettyTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()) + "-" + DateFormat.getDateInstance(DateFormat.SHORT).format(new Date());
                    String prettyTime = new SimpleDateFormat("kk:mm").format(new Date())  + "-" + DateFormat.getDateInstance(DateFormat.SHORT).format(new Date());
                    mClockTextView = (TextView) findViewById(R.id.clock_text_view);
                    if (mClockTextView != null) {
                        mClockTextView.setText(prettyTime);
                    }
                    uiHandler.postDelayed(this, 1000);
                }
            });
        } else{
            //do just one update so user doesn't have to wait
            String prettyTime = new SimpleDateFormat("kk:mm").format(new Date())  + "-" + DateFormat.getDateInstance(DateFormat.SHORT).format(new Date());
            mClockTextView = (TextView) findViewById(R.id.clock_text_view);
            if (mClockTextView != null) {
                mClockTextView.setText(prettyTime);
            }
        }

        //setup wifi status
        updateWifiHud();

        //setup phone connect status
        updatePhoneHud();

        //setup battery connect status
        updateBatteryHud();
    }

    private void updateWifiHud(){
        mWifiStatusImageView = (ImageView) findViewById(R.id.wifi_image_view);

        if (mWifiStatusImageView == null){
            return;
        }
        //wifiConnected = WifiUtils.checkWifiOnAndConnected(this); //check wifi status - don't need now that we have listener in service
        Drawable wifiOnDrawable = this.getDrawable(R.drawable.wifi_on_green);
        Drawable wifiOffDrawable = this.getDrawable(R.drawable.wifi_off_red);
        if (wifiConnected) {
            mWifiStatusImageView.setImageDrawable(wifiOnDrawable);
        } else {
            mWifiStatusImageView.setImageDrawable(wifiOffDrawable);
        }
    }

    private void updatePhoneHud(){
        mPhoneStatusImageView = (ImageView) findViewById(R.id.phone_status_image_view);
        if (mPhoneStatusImageView == null){
            return;
        }
        Drawable phoneOnDrawable = this.getDrawable(R.drawable.phone_connected_green);
        Drawable phoneOffDrawable = this.getDrawable(R.drawable.phone_disconnected_red);
        if (phoneConnected) {
            mPhoneStatusImageView.setImageDrawable(phoneOnDrawable);
        } else {
            mPhoneStatusImageView.setImageDrawable(phoneOffDrawable);
        }
    }

    private void updateBatteryHud(){
        //set the icon
        mBatteryStatusImageView = (ImageView) findViewById(R.id.battery_status_image_view);
        if (mBatteryStatusImageView == null){
            return;
        }
        Drawable batteryFullDrawable = this.getDrawable(R.drawable.full_battery_green);
        Drawable batteryFullChargingDrawable = this.getDrawable(R.drawable.full_battery_charging_green);
        Drawable batteryLowDrawable = this.getDrawable(R.drawable.low_battery_red);
        Drawable batteryLowChargingDrawable = this.getDrawable(R.drawable.low_battery_charging_red);
        if (batteryFull) {
            if (batteryCharging) {
                mBatteryStatusImageView.setImageDrawable(batteryFullChargingDrawable);
            } else {
                mBatteryStatusImageView.setImageDrawable(batteryFullDrawable);
            }
        } else {
            if (batteryCharging) {
                mBatteryStatusImageView.setImageDrawable(batteryLowChargingDrawable);
            } else {
                mBatteryStatusImageView.setImageDrawable(batteryLowDrawable);
            }
        }

        //set the text
        mBatteryStatusTextView = (TextView) findViewById(R.id.battery_percentage_text_view);
        mBatteryStatusTextView.setText((int)batteryLevel + "%");
    }

    private void switchMode(String mode) {
        curr_mode = mode;
        String modeDisplayName = "...";

        if (mode != MessageTypes.MODE_BLANK){
            turnOnScreen();
        }

        switch (mode) {
            case MessageTypes.MODE_BLANK:
                blankUi();
                modeDisplayName = "Blank";
                break;
            case MessageTypes.MODE_LANGUAGE_TRANSLATE:
                navController.navigate(R.id.nav_language_translate);
                modeDisplayName = "Lang.Trans.";
                break;
            case MessageTypes.MODE_TEXT_LIST:
                setupTextList();
                modeDisplayName = "TextList";
                break;
            case MessageTypes.MODE_TEXT_BLOCK:
                setupTextBlock();
                modeDisplayName = "TextBlock";
                break;
//            case MessageTypes.MODE_WEARABLE_FACE_RECOGNIZER:
//                showWearableFaceRecognizer();
//                modeDisplayName = "FaceRec.";
//                break;
            case MessageTypes.MODE_VISUAL_SEARCH:
                //setupVisualSearchViewfinder();
                navController.navigate(R.id.nav_visual_search);
                modeDisplayName = "VisualSearch";
                break;
//            case MessageTypes.MODE_REFERENCE_GRID:
//                setContentView(R.layout.image_gridview);
//                modeDisplayName = "Ref.Grid";
//                break;
//            case MessageTypes.MODE_SOCIAL_MODE:
//                setupSocialIntelligenceUi();
//                modeDisplayName = "Social";
//                break;
            case MessageTypes.MODE_LIVE_LIFE_CAPTIONS:
                navController.navigate(R.id.nav_live_life_captions);
                modeDisplayName = "LiveCaption";
                break;
            case MessageTypes.MODE_CONVERSATION_MODE:
                navController.navigate(R.id.nav_convo_mode);
                modeDisplayName = "Convo";
                break;
            case MessageTypes.MODE_SEARCH_ENGINE_RESULT:
                modeDisplayName = "SearchResult";
            break;
        }

        //set new mode status
        modeStatus.setText(modeDisplayName);

        //registerReceiver(mComputeUpdateReceiver, makeComputeUpdateIntentFilter());
    }

    private void showWearableFaceRecognizer(){
        setContentView(R.layout.wearable_face_recognizer);
        TextView faceRecTitle = findViewById(R.id.face_rec_title);
        faceRecTitle.setText("Face detected!\nName: " + faceNames.get(0));

//        //for now, show for n seconds and then return to llc
//        int show_time = 3000; //milliseconds
//        Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            public void run() {
//                setupLlcUi();
//            }
//        }, show_time);
    }

    //generic way to set the current enumerated list of strings and display them, scrollably, on the main UI
    private void setupTextList() {
        //live life captions mode gui setup
        setContentView(R.layout.text_list);

        //setup the text list view
        textListView = (TextView) findViewById(R.id.text_list);
        textListView.setText("");
        for (int i = 0; i < textListHolder.size(); i++) {
            textListView.append(Integer.toString(i+1) + ": " + textListHolder.get(i) + "\n");
        }
        textListView.setPadding(10, 0, 10, 0);
        textListView.setMovementMethod(new ScrollingMovementMethod());
        textListView.setSelected(true);
    }

    //generic way to set the current single string, scrollably, on the main UI
    private void setupTextBlock() {
        //live life captions mode gui setup
        setContentView(R.layout.text_block);

        //setup the text list view
        textBlockView = (TextView) findViewById(R.id.text_block);
        textBlockView.setText(textBlockHolder);
        textBlockView.setPadding(10, 0, 10, 0);
        textBlockView.setMovementMethod(new ScrollingMovementMethod());
        textBlockView.setSelected(true);
    }
    private void setupSocialIntelligenceUi() {
        //social mode ui setup
        setContentView(R.layout.social_intelligence_activity);
        messageTextView = (TextView) findViewById(R.id.message);
        eyeContactMetricTextView = (TextView) findViewById(R.id.eye_contact_metric);
        eyeContact5MetricTextView = (TextView) findViewById(R.id.eye_contact_metric_5);
        eyeContact30MetricTextView = (TextView) findViewById(R.id.eye_contact_metric_30);
        facialEmotionMetricTextView = (TextView) findViewById(R.id.facial_emotion_metric);
        facialEmotion5MetricTextView = (TextView) findViewById(R.id.facial_emotion_metric_5);
        facialEmotion30MetricTextView = (TextView) findViewById(R.id.facial_emotion_metric_30);

        //handle chart
        chart = findViewById(R.id.stress_confidence_chart);
        chart.setBackgroundColor(Color.BLACK);
        moveOffScreen(); //not sure what this does really
        setupChart();
    }

    private void blankUi() {
        turnOffScreen();
    }

    @Override
    public void onResume() {
        super.onResume();

        setupHud();

        registerReceiver(mComputeUpdateReceiver, makeComputeUpdateIntentFilter());

        bindWearableAiService();
    }

    private void teardownHud() {
        uiHandler.removeCallbacksAndMessages(null);
        clockRunning = false;
    }

    @Override
    public void onPause() {
        super.onPause();

        teardownHud();

        unbindWearableAiService();

        //unregister receiver
        unregisterReceiver(mComputeUpdateReceiver);
    }

    public void setGuiMessage(String message, TextView tv, String postfix) {
        //see if the message is generic or one of the metrics to be displayed
        messageTextView.setText("");
        tv.setText(message + postfix);
    }

    public void receiveFacialEmotionMessage(String message) {
        //see if the message is generic or one of the metrics to be displayed
        messageTextView.setText("");
        facialEmotionMetricTextView.setText(message);
    }

    private static IntentFilter makeComputeUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GlboxClientSocket.COMMAND_SWITCH_MODE);
        intentFilter.addAction(GlboxClientSocket.ACTION_WIKIPEDIA_RESULT);
        intentFilter.addAction(GlboxClientSocket.ACTION_TRANSLATION_RESULT);
        intentFilter.addAction(GlboxClientSocket.ACTION_VISUAL_SEARCH_RESULT);
        intentFilter.addAction(GlboxClientSocket.ACTION_AFFECTIVE_SUMMARY_RESULT);
        intentFilter.addAction(MessageTypes.FACE_SIGHTING_EVENT);

        intentFilter.addAction(ASPClientSocket.ACTION_AFFECTIVE_MEM_TRANSCRIPT_LIST);
        intentFilter.addAction(ASPClientSocket.ACTION_AFFECTIVE_SEARCH_QUERY);

        intentFilter.addAction(ACTION_UI_UPDATE);
        intentFilter.addAction(ASPClientSocket.ACTION_UI_DATA);

        return intentFilter;
    }

    private final BroadcastReceiver mComputeUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ASPClientSocket.ACTION_UI_DATA.equals(action)) {
                Log.d(TAG, "THIS SHIT: " + action.toString());
                try {
                    JSONObject data = new JSONObject(intent.getStringExtra(ASPClientSocket.RAW_MESSAGE_JSON_STRING));
                    String typeOf = data.getString(MessageTypes.MESSAGE_TYPE_LOCAL);
                    if (typeOf.equals(MessageTypes.SEARCH_ENGINE_RESULT)){
                        showSearchEngineResults(data);
                    } else if (typeOf.equals(MessageTypes.ACTION_SWITCH_MODES)){
                        //parse out the name of the mode
                        String modeName = data.getString(MessageTypes.NEW_MODE);
                        //switch to that mode
                        switchMode(modeName);
                    }
                } catch(JSONException e){
                        e.printStackTrace();
                }
            } else if (ACTION_UI_UPDATE.equals(action)) {
                Log.d(TAG, "GOT ACTION_UI_UPDATE");
                if (intent.hasExtra(PHONE_CONN_STATUS_UPDATE)) {
                    phoneConnected = intent.getBooleanExtra(PHONE_CONN_STATUS_UPDATE, false);
                    updatePhoneHud();
                }
                if (intent.hasExtra(BATTERY_CHARGING_STATUS_UPDATE)){
                    batteryCharging = intent.getBooleanExtra(BATTERY_CHARGING_STATUS_UPDATE, false);
                    updateBatteryHud();
                }
                if (intent.hasExtra(BATTERY_LEVEL_STATUS_UPDATE)){
                    batteryLevel = intent.getFloatExtra(BATTERY_LEVEL_STATUS_UPDATE, 100f);
                    if (batteryLevel > 40f) {
                        batteryFull = true;
                    } else {
                        batteryFull = false;
                    }
                    updateBatteryHud();
                }
                if (intent.hasExtra(WIFI_CONN_STATUS_UPDATE)){
                    wifiConnected = intent.getBooleanExtra(WIFI_CONN_STATUS_UPDATE, false);
                    updateWifiHud();
                }
        } else if (curr_mode.equals(MessageTypes.MODE_SOCIAL_MODE) && ASPClientSocket.ACTION_RECEIVE_MESSAGE.equals(action)) {
                if (intent.hasExtra(ASPClientSocket.EYE_CONTACT_5_MESSAGE)) {
                    String message = intent.getStringExtra(ASPClientSocket.EYE_CONTACT_5_MESSAGE);
                    setGuiMessage(message, eyeContact5MetricTextView, "%");
                } else if (intent.hasExtra(ASPClientSocket.EYE_CONTACT_30_MESSAGE)) {
                    String message = intent.getStringExtra(ASPClientSocket.EYE_CONTACT_30_MESSAGE);
                    setGuiMessage(message, eyeContact30MetricTextView, "%");
                    eye_contact_30 = Float.parseFloat(message);
                    setChartData();
                } else if (intent.hasExtra(ASPClientSocket.EYE_CONTACT_300_MESSAGE)) {
                    String message = intent.getStringExtra(ASPClientSocket.EYE_CONTACT_300_MESSAGE);
                    setGuiMessage(message, eyeContactMetricTextView, "%");
                } else if (intent.hasExtra(ASPClientSocket.FACIAL_EMOTION_5_MESSAGE)) {
                    String message = intent.getStringExtra(ASPClientSocket.FACIAL_EMOTION_5_MESSAGE);
                    setGuiMessage(message, facialEmotion5MetricTextView, "");
                    facial_emotion_5 = message;
                } else if (intent.hasExtra(ASPClientSocket.FACIAL_EMOTION_30_MESSAGE)) {
                    String message = intent.getStringExtra(ASPClientSocket.FACIAL_EMOTION_30_MESSAGE);
                    setGuiMessage(message, facialEmotion30MetricTextView, "");
                    facial_emotion_30 = message;
                } else if (intent.hasExtra(ASPClientSocket.FACIAL_EMOTION_300_MESSAGE)) {
                    String message = intent.getStringExtra(ASPClientSocket.FACIAL_EMOTION_300_MESSAGE);
                    setGuiMessage(message, facialEmotionMetricTextView, "");
                }
            } else if (ASPClientSocket.ACTION_AFFECTIVE_MEM_TRANSCRIPT_LIST.equals(action)) {
                try {
                    JSONObject affective_mem = new JSONObject(intent.getStringExtra(ASPClientSocket.AFFECTIVE_MEM_TRANSCRIPT_LIST));
                    textListHolder.clear();
                    int i = 0;
                    while (true) {
                        if (!affective_mem.has(Integer.toString(i))) {
                            break;
                        }
                        String transcript = affective_mem.getString(Integer.toString(i));
                        textListHolder.add(transcript);
                        i++;
                    }
                    switchMode(MessageTypes.MODE_TEXT_LIST);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (ASPClientSocket.ACTION_AFFECTIVE_SEARCH_QUERY.equals(action)) {
                try {
                    JSONObject affective_query_result = new JSONObject(intent.getStringExtra(ASPClientSocket.AFFECTIVE_SEARCH_QUERY_RESULT));
                    textBlockHolder = "";
                    textBlockHolder = "Most " + affective_query_result.getString("emotion") + " of last conversation: \n\n" + affective_query_result.getString("phrase");
                    switchMode(MessageTypes.MODE_TEXT_BLOCK);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (MessageTypes.FACE_SIGHTING_EVENT.equals(action)) {
                String currName = intent.getStringExtra(MessageTypes.FACE_NAME);
                faceNames.clear();
                faceNames.add(currName);
                long timeSinceLastFaceUpdate = System.currentTimeMillis() - lastFaceUpdateTime;
                if (timeSinceLastFaceUpdate > faceUpdateInterval){
                    Toast.makeText(MainActivity.this, "Saw: " + currName, Toast.LENGTH_SHORT).show();
                    lastFaceUpdateTime = System.currentTimeMillis();
                }
                //switchMode(MessageTypes.MODE_WEARABLE_FACE_RECOGNIZER);
            } else if (GlboxClientSocket.ACTION_AFFECTIVE_SUMMARY_RESULT.equals(action)) {
                String str_data = intent.getStringExtra(GlboxClientSocket.AFFECTIVE_SUMMARY_RESULT);
                textBlockHolder = str_data;
                switchMode(MessageTypes.MODE_TEXT_BLOCK);
            }
        }
    };


    //stuff for the charts
    private void setChartData() {
        float max = 100;
        ArrayList<PieEntry> values = new ArrayList<>();

        //temporary method of deducing stress
        float input = (max - eye_contact_30);
        if ((facial_emotion_30 == "Happy") || (facial_emotion_5 == "Happy")) {
            input = Math.max(0, input - 20);
        }

//        for (int i = 0; i < count; i++) {
//            values.add(new PieEntry((float) ((Math.random() * range) + range / 5), "Stress"));
//        }
        values.add(new PieEntry((float) (input), ""));
        values.add(new PieEntry((float) (max - input), ""));

        PieDataSet dataSet = new PieDataSet(values, "Election Results");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(5f);
        dataSet.setDrawValues(false);

        //dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setColors(Color.RED, Color.BLACK);
        //dataSet.setSelectionShift(0f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(24f);
        data.setValueTextColor(Color.BLACK);
        //data.setValueTypeface(tfLight);
        chart.setData(data);
        //chart.animateY(1400, Easing.EaseInOutQuad);

        chart.invalidate();
    }

    private void moveOffScreen() {

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        int height = displayMetrics.heightPixels;

        int offset = (int) (height * 0.65); /* percent to move */

        RelativeLayout.LayoutParams rlParams =
                (RelativeLayout.LayoutParams) chart.getLayoutParams();
        rlParams.setMargins(0, 0, 0, -offset);
        chart.setLayoutParams(rlParams);
    }

    private void setupChart() {
        chart.setUsePercentValues(false);
        chart.getDescription().setEnabled(false);

        //chart.setCenterTextTypeface(tfLight);

        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.BLACK);

        chart.setTransparentCircleColor(Color.BLACK);
        chart.setTransparentCircleAlpha(110);

        chart.setHoleRadius(58f);
        chart.setTransparentCircleRadius(61f);

        chart.setDrawCenterText(true);

        chart.setRotationEnabled(false);
        chart.setHighlightPerTapEnabled(false);

        chart.setMaxAngle(180f); // HALF CHART
        chart.setRotationAngle(180f);
        chart.setCenterTextOffset(0, -20);
        chart.setCenterTextColor(Color.WHITE);
        chart.setCenterTextSize(28);
        chart.setCenterText("Stress");

        setChartData();

        chart.animateY(1400, Easing.EaseInOutQuad);

//        Legend l = chart.getLegend();
//        l.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
//        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
//        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
//        l.setDrawInside(false);
//        l.setXEntrySpace(7f);
//        l.setYEntrySpace(0f);
//        l.setYOffset(0f);
//
//        // entry label styling
        chart.setEntryLabelColor(Color.BLACK);
        //chart.setEntryLabelTypeface(tfRegular);
        chart.setEntryLabelTextSize(19f);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WearableAiService.LocalBinder binder = (WearableAiService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;

            //get update of ui information
            mService.requestUiUpdate();
            Log.d(TAG, "onServiceConnected complete");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "got keycode");
        Log.d(TAG, Integer.toString(keyCode));
        //broadcast to child fragments that this happened
        final Intent nintent = new Intent();
        nintent.setAction(MessageTypes.SG_TOUCHPAD_EVENT);
        nintent.putExtra(MessageTypes.SG_TOUCHPAD_KEYCODE, keyCode);
        this.sendBroadcast(nintent); //eventually, we won't need to use the activity context, as our service will have its own context to send from

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                Log.d(TAG, "keycode _ enter felt");
                return super.onKeyUp(keyCode, event);
            case KeyEvent.KEYCODE_DEL:
                Log.d(TAG, "keycode DEL entered - this means go back home");
                navHandler.removeCallbacksAndMessages(null);
                switchMode(MessageTypes.MODE_LIVE_LIFE_CAPTIONS);
                return super.onKeyUp(keyCode, event);
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    // Note  from authour - From what I've seen you don't need the wake-lock or wifi-lock below for the audio-recorder to persist through screen-off.
    // However, to be on the safe side you might want to activate them anyway. (and/or if you have other functions that need them)
    private PowerManager.WakeLock wakeLock_partial = null;
    public void StartPartialWakeLock() {
        if (wakeLock_partial != null && wakeLock_partial.isHeld()) return;
        Log.i("vmain", "Starting partial wake-lock.");
        final PowerManager pm = (PowerManager) this.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        wakeLock_partial = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.myapp:partial_wake_lock");
        wakeLock_partial.acquire();
    }

    public void bindWearableAiService(){
        // Bind to that service
        Intent intent = new Intent(this, WearableAiService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void unbindWearableAiService() {
        // Bind to that service
        unbindService(connection);
    }

    private void showReferenceCard(String title, String body, String imgUrl, long timeout){
        navHandler.removeCallbacksAndMessages(null);
        String lastMode = curr_mode;

        //show the reference
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("body", body);
        args.putString("img_url", imgUrl);
        navController.navigate(R.id.nav_reference, args);

        //for now, show for n seconds and then return to llc
        navHandler.postDelayed(new Runnable() {
            public void run() {
                switchMode(lastMode);
            }
        }, timeout);
    }

    private void showSearchEngineResults(JSONObject data) {
        try {
            //parse out the response object - one result for now
            JSONObject search_engine_result = new JSONObject(data.getString(MessageTypes.SEARCH_ENGINE_RESULT_DATA));

            //get content
            String title = search_engine_result.getString("title");
            String summary = search_engine_result.getString("body");

            //pull image from web and display in imageview
            String img_url = null;
            if (search_engine_result.has("image")) {
                img_url = search_engine_result.getString("image");
            }

            showReferenceCard(title, summary, img_url, 8000);
            switchMode(MessageTypes.MODE_SEARCH_ENGINE_RESULT);
        } catch (JSONException e) {
            Log.d(TAG, e.toString());
        }
    }

    //start, stop, handle the service
    public void stopWearableAiService() {
        Log.d(TAG, "Stopping WearableAI service");
        unbindWearableAiService();

        if (!isMyServiceRunning(WearableAiService.class)) return;
        Intent stopIntent = new Intent(this, WearableAiService.class);
        stopIntent.setAction(WearableAiService.ACTION_STOP_FOREGROUND_SERVICE);
        startService(stopIntent);
    }

    public void sendWearableAiServiceMessage(String message) {
        if (!isMyServiceRunning(WearableAiService.class)) return;
        Intent messageIntent = new Intent(this, WearableAiService.class);
        messageIntent.setAction(message);
        Log.d(TAG, "Sending WearableAi Service this message: " + message);
        startService(messageIntent);
    }

    public void startWearableAiService() {
        Log.d(TAG, "Starting wearableAiService");
        if (isMyServiceRunning(WearableAiService.class)) return;
        Intent startIntent = new Intent(this, WearableAiService.class);
        startIntent.setAction(WearableAiService.ACTION_START_FOREGROUND_SERVICE);
        startService(startIntent);
    }

    //check if service is running
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}

