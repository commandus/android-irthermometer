package com.commandus.irthermometer;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    private static final String TAG = "TEST";

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertEquals("com.commandus.irthermometer", appContext.getPackageName());
    }

    @Test
    public void sendMeasurement() {
        OkHttpClient serviceClient = new OkHttpClient();
        long id = 0;
        Measurement value = new Measurement();

        long gate = Settings.getGate();
        long uid = Settings.getUid();
        long secret = Settings.getSecret();

        // double k = Settings.getEmissivityCoefficient();
        int t = Settings.calcT(value);

        JSONObject js = new JSONObject();
        try {
            js.put("uid", uid);
        } catch (JSONException e) {
        }
        try {
            js.put("secret", secret);
        } catch (JSONException e) {
        }
        try {
            js.put("gate", gate);
        } catch (JSONException e) {
        }
        try {
            js.put("time", value.startTime);
        } catch (JSONException e) {
        }
        try {
            js.put("t", t);
        } catch (JSONException e) {
        }
        try {
            js.put("tir", value.maxT - 27315);
        } catch (JSONException e) {
        }
        try {
            js.put("tmin", value.minT - 27315);
        } catch (JSONException e) {
        }
        try {
            js.put("tambient", value.ambientT - 27315);
        } catch (JSONException e) {
        }
        try {
            js.put("id", id);
        } catch (JSONException e) {
        }
        String sJson = js.toString();
        Log.d(TAG, sJson);
        RequestBody json = RequestBody.create(sJson, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Settings.getHost())
                .post(json)
                .build();

        try {
            Response r = serviceClient.newCall(request).execute();
            Log.d(TAG, r.body().toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        serviceClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                JSONObject responseObject = null;
                try {
                    responseObject = new JSONObject(response.body().string());
                } catch (JSONException e) {
                    return;
                }
                long r = -1;
                try {
                    r = responseObject.getLong("r");
                } catch (JSONException e) {
                    return;
                }
                String msg = "";
                try {
                    msg = responseObject.getString("msg");
                } catch (JSONException e) {
                    return;
                }
                if (r == 0) {
                    Log.d(TAG, msg);
                } else {
                    Log.d(TAG, Long.toString(r));
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                System.out.println(e.getMessage());
            }
        });

    }

}
