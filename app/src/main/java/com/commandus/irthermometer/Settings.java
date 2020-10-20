package com.commandus.irthermometer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;

import androidx.preference.PreferenceManager;

import java.io.File;

public class Settings {
    public static final int USB_VENDOR_ID = 0x16d0;
    public static final int USB_PRODUCT_ID = 0x087e;
    static final String APPLICATION_ID = "com.commandus.irthermometer";
    static final String INTENT_ACTION_GRANT_USB = APPLICATION_ID + ".GRANT_USB";
    static final String INTENT_ACTION_DISCONNECT = APPLICATION_ID + ".Disconnect";

    private static final String LINK_PREFIX = "https://irthermometer.commandus.com/";
    public  static final String EMAIL_SUPPORT = "andrey.ivanov@ikfia.ysn.ru";

    private static final String PREF_GATE = "gate";
    private static final String PREF_SERVICE_URL = "url";
    private static final String DEF_SERVICE_URL = "https://aikutsk.ru/irtm";

    private static final String PREF_SERVICE_PROTO = "proto";
    private static final String PREF_SECRET = "secret";
    private static final String PREF_FORCE_LOG = "force_log";
    private static final String PREF_AUTO_START = "auto_start";

    private static final String PREF_EMISSIVITY_COEFFICIENT = "e";
    private static final String PREF_CRITICAL_MAX_T = "critical_max_t";
    private static final String PREF_THEME = "theme";

    public static final String LOG_FILE_NAME = "ir-thermometer.txt";

    private static Settings mSettings = null;
    private final Context mContext;

    private boolean mForceLog;
    private long mGate;
    private long mSecret;
    private String mServiceUrl;
    private String mProto; // grpc|json
    private String mTheme; // light|dark
    private double mEmissivityCoefficient;
    private int mCriticalMaxTemperature;
    private boolean mAutoStart;

    public boolean isForceLog() {
        return mForceLog;
    }

    public boolean isAutoStart() {
        return mAutoStart;
    }

    public long getGate() {
        return mGate;
    }

    public long getSecret() {
        return mSecret;
    }

    public String getServiceUrl() {
        return mServiceUrl;
    }

    public String getProto() {
        return mProto;
    }

    public String getTheme() {
        return mTheme;
    }

    public double getEmissivityCoefficient() {
        return mEmissivityCoefficient;
    }

    public int getCriticalMaxTemperature() {
        return mCriticalMaxTemperature;
    }

    public void load() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mForceLog = prefs.getBoolean(PREF_FORCE_LOG, false);
        mAutoStart = prefs.getBoolean(PREF_AUTO_START, true);
        mEmissivityCoefficient = prefs.getFloat(PREF_EMISSIVITY_COEFFICIENT, 0.92f);
        mCriticalMaxTemperature = prefs.getInt(PREF_CRITICAL_MAX_T, 3710 + 27315);

        mGate = prefs.getLong(PREF_GATE, 0);
        mSecret = prefs.getLong(PREF_SECRET, 0);
        mServiceUrl = prefs.getString(PREF_SERVICE_URL, DEF_SERVICE_URL);
        mProto = prefs.getString(PREF_SERVICE_PROTO, "grpc");
        mTheme = prefs.getString(PREF_THEME, "light");
    }

    public Settings(Context context) {
        mContext = context;
        load();
    }

    public synchronized static Settings getSettings(Context context) {
        if (mSettings == null) {
            mSettings = new Settings(context);
        }
        return mSettings;
    }

}
