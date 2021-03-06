package com.commandus.irthermometer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements ServiceConnection, ServiceListener {

    private static final String TAG = "irthermometer-activity";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String FMT_TEMPERATURE = "#0.0";
    private static final String FMT_TEMPERATURE_SPEECH = "#0";
    private static final String FMT_TIME = "HH:mm:ss";
    private static final int RET_SETTINGS = 1;
    private int SOUND_ALARM;
    private int SOUND_BEEP;
    private int SOUND_OFF;
    private int SOUND_ON;
    private int SOUND_SHOT;

    private static final int SOUND_PRIORITY_1 = 1;

    private BroadcastReceiver broadcastReceiver;
    private UsbDeviceConnection usbConnection;
    private UsbSerialPort usbSerialPort;
    private UsbSerialDriver usbDriver;
    private SerialService service;

    private RFIDReader rfidReader;
    OkHttpClient serviceClient;

    private boolean connected = false;
    private SimpleDateFormat dateFormat;
    private DecimalFormat temperatureFormat;
    private DecimalFormat temperatureSpeechFormat;
    private Measurement lastMeasurement;

    TextToSpeech textToSpeech;

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        log("service connect..");
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectUSBNSendMeasureAmbient();
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        log("disconnected");
        service = null;
    }

    private SoundPool soundPool;

    private TextView tvTemperatureIR;
    private TextView tvTemperatureCurrent;
    private TextView tvTemperatureAmbient;
    private TextView tvTemperatureMin;
    private TextView tvPerson;
    private TextView tvTime;
    private ImageView ivUSB;
    private ImageView ivMenu;

    private Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // usbConnection = null;
        // usbSerialPort = null;

        // so not show title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // do not turn off screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // request full screen
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        getSupportActionBar().hide();

        rfidReader = new RFIDReader();
        serviceClient = new OkHttpClient();

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(10)
                .build();

        // load sounds
        SOUND_ALARM = soundPool.load(this, R.raw.alarm, SOUND_PRIORITY_1);
        SOUND_BEEP = soundPool.load(this, R.raw.beep, SOUND_PRIORITY_1);
        SOUND_OFF = soundPool.load(this, R.raw.off, SOUND_PRIORITY_1);
        SOUND_ON = soundPool.load(this, R.raw.on, SOUND_PRIORITY_1);
        SOUND_SHOT = soundPool.load(this, R.raw.shot, SOUND_PRIORITY_1);

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    log("TTS initialized");
                }
            }
        });

        settings = Settings.getSettings(this);

        // bind GUI
        setContentView(R.layout.activity_main);
        tvTemperatureIR = findViewById(R.id.tvTemperatureIR);
        tvTemperatureCurrent = findViewById(R.id.tvTcurrent);
        tvTemperatureAmbient = findViewById(R.id.tvTAmbient);
        tvTemperatureMin = findViewById(R.id.tvTmin);
        tvTime = findViewById(R.id.tvTime);
        tvPerson = findViewById(R.id.tvPerson);
        ivUSB = findViewById(R.id.ivUSB);
        ivMenu = findViewById(R.id.ivMenu);

        // hide all
        showMeasurementWidgets(false);

        ivMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });

        // date formatter
        dateFormat = new SimpleDateFormat(FMT_TIME);
        // temperature formatters
        temperatureFormat = new DecimalFormat(FMT_TEMPERATURE);
        temperatureSpeechFormat = new DecimalFormat(FMT_TEMPERATURE_SPEECH);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log("broadcastReceiver action: " + intent.getAction());
                if (intent.getAction().equals(Settings.INTENT_ACTION_GRANT_USB)) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    log("granted: " + Boolean.toString(granted) + ", try connect..");
                    connectUSBNSendMeasureAmbient(granted);
                }
            }
        };

        startService(new Intent(this, SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change

        checkTheme();
        log("main activity created");
    }

    /**
     * Prevent re-start activity on USB keyboard plugged in
     * @param config new configuration
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration config) {
        super.onConfigurationChanged(config);
        log("new configuration: " + config.toString());
    }

    private void checkTheme() {
        if (settings.getTheme() == "dark")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void showMenu(View v) {
        PopupMenu menu = new PopupMenu(this, v);
        menu.inflate(R.menu.main);
        MenuItem it = menu.getMenu().findItem(R.id.menu_send_log);
        if (it != null) {
            it.setEnabled(settings.isForceLog());
        }
        menu.show();
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_start:
                        connectUSBNSendMeasureAmbient();
                        return true;
                    case R.id.menu_info:
                        return true;
                    case R.id.menu_send_log:
                        sendLogByMail();
                        return true;
                    case R.id.menu_settings:
                        Intent intent = new Intent(MainActivity.this,
                                SettingsActivity.class);
                        startActivityForResult(intent, RET_SETTINGS);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RET_SETTINGS:
                settings.load();
                checkTheme();
                break;
            default:
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getAction().equals("android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
            // TODO
            soundPool.play(SOUND_ON, 1.0f, 1.0f, SOUND_PRIORITY_1, 0, 1.0f);
            log("new intent USB_DEVICE_ATTACHED, new device attached");
            log("trying to connect");
            connectUSBNSendMeasureAmbient();
        }
        super.onNewIntent(intent);
    }

    @Override
    public void onDestroy() {
        if (connected)
            disconnect();
        stopService(new Intent(this, SerialService.class));
        super.onDestroy();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        long id = rfidReader.put(keyCode);
        if (id > 0) {
            //
            tvPerson.setText(Long.toString(id));
            sendMeasurement(id, lastMeasurement);
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onStart() {
        super.onStart();
        log("onstart");
        bindService(new Intent(this, SerialService.class), this, Context.BIND_AUTO_CREATE);
        registerReceiver(broadcastReceiver, new IntentFilter(Settings.INTENT_ACTION_GRANT_USB));
    }

    @Override
    public void onStop() {
        super.onStop();
        log("onstop");
        if (service != null) {
            try {
                unbindService(this);
            } catch (Exception ignored) {

            }
        }
        unregisterReceiver(broadcastReceiver);
    }

    private int serialDevicesCount() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        int c = 0;
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            if (v.getVendorId() == Settings.USB_VENDOR_ID && v.getProductId() == Settings.USB_PRODUCT_ID)
                c++;
        }
        return c;
    }

    private void connectUSBNSendMeasureAmbient() {
        connectUSBNSendMeasureAmbient(null);
    }

    private void connectUSBNSendMeasureAmbient(Boolean permissionGranted) {
        if (usbConnection == null) {
            validateUSBConnection(permissionGranted);
            if (usbConnection == null) {
                tryToFindDeviceInFuture(permissionGranted, 5000);
                return;
            }
        }

        log("USB connection established..");

        if (!service.hasSocket()) {
            SerialSocket socket = mkSerialSocket();
            log("connect socket..");
            try {
                service.connect(socket);
            } catch (Exception e) {
                log("connect exception: " + e.getMessage());
            }
        }

        // start with ambient temperature
        log("start with ambient temperature");
        service.startMeasure();

        // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
        // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
        soundPool.play(SOUND_ON, 1.0f, 1.0f, SOUND_PRIORITY_1, 0, 1.0f);
    }

    private SerialSocket mkSerialSocket() {
        try {
            // TODO
            int portIndex = 0;
            usbSerialPort = usbDriver.getPorts().get(portIndex);
            usbSerialPort.open(usbConnection);
            // TODO
            int baudRate = 115200;
            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            SerialSocket socket = new SerialSocket(getApplicationContext(), usbConnection, usbSerialPort);
            return socket;
        } catch (Exception e) {
            log("connect exception: " + e.getMessage());
        }
        return null;
    }

    /**
     * Set usbConnection, usbDriver
     * @param permissionGranted
     */
    private void validateUSBConnection(Boolean permissionGranted) {
        if (usbConnection != null)
            return;
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            log(Integer.toHexString(v.getVendorId()) + ":" + Integer.toHexString(v.getProductId()));
            if (v.getVendorId() == Settings.USB_VENDOR_ID && v.getProductId() == Settings.USB_PRODUCT_ID) {
                device = v;
                break;
            }
        }
        if (device == null) {
            log("connection failed: device not found");
            return;
        }

        usbDriver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (usbDriver == null) {
            log("no driver found, probe custom driver..");
            usbDriver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (usbDriver == null) {
            log("connection failed: no [custom] driver for device");
            return;
        }
        if (usbDriver.getPorts().size() == 0) {
            log("connection failed: not enough ports at device");
            return;
        }
        log("Check USB permission..");

        usbConnection = usbManager.openDevice(usbDriver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(usbDriver.getDevice())) {
            log("request USB permission..");
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(Settings.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(usbDriver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(usbDriver.getDevice()))
                log("connection failed: permission denied");
            else
                log("connection failed: open failed");
            return;
        }
}

    private void tryToFindDeviceInFuture(final Boolean permissionGranted, int ms) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectUSBNSendMeasureAmbient(permissionGranted);
                    }
                });
            }
        }, ms);
    }

    private void disconnect() {
        connected = false;
        service.disconnect();
        usbSerialPort = null;
    }

    void log(String s) {
        Log.d(TAG, s);
        if (settings.isForceLog()) {
            try {
                String path = "/irthermometer/";
                File docs = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), path);
                FileOutputStream output = new FileOutputStream(docs.getAbsoluteFile()
                        + Settings.LOG_FILE_NAME, true);
                output.write((new Date().toString() + ": " + s + "\r\n").getBytes());
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    long previousStartTime = 0;

    // Measure listener
    @Override
    public void onValue(final Measurement value) {
        int tFixed = calcT(value);
        double t = (tFixed - 27315) / 100.;
        boolean highTemperature = (tFixed > settings.getCriticalMaxTemperature());
        String ts = temperatureFormat.format(t);
        String ts2 = temperatureSpeechFormat.format(t);

        showMeasurementWidgets(true);

        tvTemperatureIR.setText(ts);
        String tis = dateFormat.format(new Date(value.startTime));
        tvTime.setText(tis);
        if (value.startTime != previousStartTime) {
            if (highTemperature) {
                soundPool.play(SOUND_ALARM, 1.0f, 1.0f, SOUND_PRIORITY_1, 0, 1.0f);
            } else {
                soundPool.play(SOUND_BEEP, 1.0f, 1.0f, SOUND_PRIORITY_1, 0, 1.0f);
            }
            textToSpeech.speak(ts2, TextToSpeech.QUEUE_ADD, null, tis);
            previousStartTime = value.startTime;
        }
        double tMin = (value.minT - 27315) / 100.;
        tvTemperatureMin.setText(temperatureFormat.format(tMin));
        double tAmbient = (value.ambientT - 27315) / 100.;
        tvTemperatureAmbient.setText(temperatureFormat.format(tAmbient));
        lastMeasurement = value;
        log("new value shown " + value.toString());
    }

    @Override
    public void onCurrentTemperature(final int currentTemperature) {
        double t = (currentTemperature - 27315) / 100.;
        tvTemperatureCurrent.setText(temperatureFormat.format(t));
    }

    @Override
    public void onConnect() {
        connected = true;
        ivUSB.setVisibility(connected ? View.VISIBLE : View.INVISIBLE);
        log("serial port connected ");
    }

    @Override
    public void onConnectError(Exception e) {
        connected = false;
        ivUSB.setVisibility(connected ? View.VISIBLE : View.INVISIBLE);
        log("serial port connect error " + e.toString());
    }

    @Override
    public void onReadError(Exception e) {
        log("service serial port read exception: " + e.toString());
    }

    @Override
    public void onWriteError(Exception e) {
        log("service serial port write exception: " + e.toString());
    }

    @Override
    public void onInfo(String msg) {
        log("service info: " + msg);
    }

    private void sendMeasurement(long id, final Measurement value) {
        GateMeasurement gateMeasurement = new GateMeasurement(
                value, settings.getGate(), settings.getSecret(), id,
                settings.getHost(), settings.getPort(), settings.getEmissivityCoefficient());
        if (settings.getProto() == "json") {
            sendMeasurementJSON(gateMeasurement);
        } else {
            sendMeasurementJSON(gateMeasurement);
            // sendMeasurementGRPC(gateMeasurement);
        }
    }

    private void sendMeasurementGRPC(final GateMeasurement value) {
        new GrpcTask(this).execute(value);
    }

    private static class GrpcTask extends AsyncTask<GateMeasurement, Void, String> {
        private final WeakReference<Activity> activityReference;
        private ManagedChannel channel;

        private GrpcTask(Activity activity) {
            this.activityReference = new WeakReference<Activity>(activity);
        }

        @Override
        protected String doInBackground(GateMeasurement ... values) {
            GateMeasurement value = values[0];
            String host = value.url;
            int port = value.port;
            try {
                int t = calcT(value);
                channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
                IRThermometerGrpc.IRThermometerBlockingStub stub = IRThermometerGrpc.newBlockingStub(channel);
                MeasurementRequest request = MeasurementRequest.newBuilder()
                        .setT(t)
                        .setGate(Gate.newBuilder()
                                .setGateid(value.gateId)
                                .setSecret(value.secret))
                        .setTime(value.measurement.startTime)
                        .setTir(value.measurement.maxT - 27315)
                        .setTmin(value.measurement.minT - 27315)
                        .setTambient(value.measurement.ambientT - 27315)
                        .build();
                MeasurementResponse reply = stub.measurement(request);
                return reply.getMsg();
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                return String.format("Failed... : %n%s", sw);
            }
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            // TextView resultText = (TextView) activity.findViewById(R.id.grpc_response_text);
            // resultText.setText(result);
        }
    }

    private void sendMeasurementJSON(final GateMeasurement value) {
        if (value == null)
            return;

        int t = calcT(value);

        JSONObject js = new JSONObject();
        try {
            js.put("gate", value.gateId);
        } catch (JSONException e) {
        }
        try {
            js.put("secret", value.secret);
        } catch (JSONException e) {
        }
        try {
            js.put("time", value.measurement.startTime);
        } catch (JSONException e) {
        }
        try {
            js.put("t", t);
        } catch (JSONException e) {
        }
        try {
            js.put("tir", value.measurement.maxT - 27315);
        } catch (JSONException e) {
        }
        try {
            js.put("tmin", value.measurement.minT - 27315);
        } catch (JSONException e) {
        }
        try {
            js.put("tambient", value.measurement.ambientT - 27315);
        } catch (JSONException e) {
        }
        try {
            js.put("id", value.id);
        } catch (JSONException e) {
        }
        String sJson = js.toString();
        RequestBody json = RequestBody.create(sJson, JSON);
        log("send to the server https://" + settings.getHost());
        Request request = new Request.Builder()
                .url("https://" + settings.getHost())
                .post(json)
                .build();

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
                    r = 0;
                }
                String msg = "";
                if (r == 0) {
                    try {
                        msg = responseObject.getString("msg");
                    } catch (JSONException e) {
                        msg = "Hi;)";
                    }
                } else {
                    msg = "E: " + Long.toString(r);
                }

                log("sent to the server successfully " + value.toString() + ", response: " + msg);

                final String finalMsg = msg;
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvPerson.setText(finalMsg);
                    }
                });
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                final String msg = e.getMessage();
                log("error get response from the server " + value.toString());
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvPerson.setText(msg);
                    }
                });
            }
        });
    }

    private void showMeasurementWidgets(boolean visible) {
        if (tvTemperatureIR.getVisibility() == (visible ? View.VISIBLE : View.INVISIBLE))
            return;
        tvTemperatureIR.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        tvTemperatureCurrent.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        tvTemperatureAmbient.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        tvTemperatureMin.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        tvTime.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public int calcT(Measurement value) {
        return calcT(value, settings.getEmissivityCoefficient());
    }

    private static int calcT(GateMeasurement value) {
        return calcT(value.measurement, value.e);
    }

    /**
     * @param value
     * @return
     * @see "https://www.apogeeinstruments.com/emissivity-correction-for-infrared-radiometer-sensors/"
     */
    private static int calcT(Measurement value, double emissivityK) {
        double e = emissivityK;
        double temperatureSensor = value.maxT / 100.0;
        double temperatureBackground;
        if (value.minT <= 0)
            temperatureBackground = value.ambientT / 100.0;
        else
            temperatureBackground = value.minT / 100.0;

        return (int) Math.round( 100. *
                Math.sqrt(Math.sqrt(
                        (temperatureSensor * temperatureSensor * temperatureSensor * temperatureSensor
                                - (1 - e) * temperatureBackground * temperatureBackground * temperatureBackground * temperatureBackground)
                                / e
                )));
    }

    public void sendLogByMail(){
        // save logcat in file

        String path = "/irthermometer/" + Settings.LOG_FILE_NAME;
        File outputFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), path);
        // File outputFile = new File(Environment.getExternalStorageDirectory(), Settings.LOG_FILE_NAME);
        // send file using email
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        // Set type to "email"
        emailIntent.setType("vnd.android.cursor.dir/email");
        String to[] = { Settings.EMAIL_SUPPORT };
        emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
        // the attachment
        // emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(outputFile));
        Uri upath = Uri.parse( "file://" + outputFile.getAbsolutePath());
        emailIntent.putExtra(Intent.EXTRA_STREAM, path);
        // the mail subject
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "IRThermometer log gate#"
                + Long.toString(settings.getGate()));
        startActivity(Intent.createChooser(emailIntent , "?????????????????? ?????????? ???? ??????????.."));
    }

}
