package com.commandus.irthermometer;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service
        implements SerialListener, ParserListener {

    private static final String TAG = "irthermometer-service";;

    public boolean hasSocket() {
        return socket != null;
    }

    class SerialBinder extends Binder {
        SerialService getService() {
            return SerialService.this;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;

    private SerialSocket socket;
    private boolean connected;

    private ThermometerParser parser;
    private ServiceListener listener;

    /**
     * Lifecylce
     */
    public SerialService() {
        log("serial service created");
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        log("serial service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log("serial service bind");
        return binder;
    }

    /**
     * Api
     */
    public void connect(SerialSocket socket) throws IOException {
        log("service connect serial socket " + socket.getName());
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        log("service disconnect serial socket");
        connected = false; // ignore data,errors while disconnecting
        cancelNotification();
        if(socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected) {
            log("not connected on write");
            throw new IOException("not connected");
        }
        socket.write(data);
    }

    public void attach(ServiceListener listener) {
        log("attach serial listener");
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        cancelNotification();
        synchronized (this) {
            this.listener = listener;
            parser = new ThermometerParser(this);
            // parser.setDtSeconds(0);
        }
    }

    public void detach() {
        log("detach service, connected: " + Boolean.toString(connected));
        listener = null;
        parser = null;
    }

    private void log(
            final String message
    ) {
        Log.d(TAG, message);
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onInfo(message);
                        }
                    }
                });
            }
        }
    }

    private void cancelNotification() {
        // stopForeground(true);
    }

    /**
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        connected = true;
        log("serial connected");
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onConnect();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onSerialConnectError(final Exception e) {
        log("serial connect error");
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onReadError(e);
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onSerialRead(final byte[] data) {
        if (connected) {
            if (parser != null) {
                parser.put(data);
            }
        }
    }

    @Override
    public void onSerialIoError(final Exception e) {
        log("serial IO error " + e.toString());
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onReadError(e);
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onValue(final Measurement value) {
        synchronized (this) {
            if (listener != null) {
                mainLooper.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onValue(value);
                        }
                    }
                });
            }
        }
        // get first value ambient
        parser.setMeasureMode(ThermometerParser.MEASURE_MODE.MODE_AMBIENT);
    }

    @Override
    public void onCurrentTemperature(final int currentTemperature) {

        nextMeasure();

        synchronized (this) {
            if (listener != null) {
                mainLooper.post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) {
                            listener.onCurrentTemperature(currentTemperature);
                        }
                    }
                });
            }
        }
    }

    private static byte[] sequenceMeasureIR = {'0'};
    private static byte[] sequenceMeasureAmbient = {'1'};

    public void nextMeasure() {
        try {
            write(parser.getMeasureMode() == ThermometerParser.MEASURE_MODE.MODE_IR
                    ? sequenceMeasureIR : sequenceMeasureAmbient);
            parser.setMeasureMode(ThermometerParser.MEASURE_MODE.MODE_IR);
        } catch (final IOException e) {
            log("measure IR send '0' I/O error " + e.getMessage());
            if (connected) {
                synchronized (this) {
                    if (listener != null) {
                        mainLooper.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onWriteError(e);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    public void startMeasure() {
        parser.setMeasureMode(ThermometerParser.MEASURE_MODE.MODE_AMBIENT);
        try {
            write(sequenceMeasureAmbient);
        } catch (final IOException e) {
            log("measure ambient send '1' I/O error "  + e.getMessage());
            if (connected) {
                synchronized (this) {
                    if (listener != null) {
                        mainLooper.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onWriteError(e);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

}
