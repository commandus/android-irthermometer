package com.commandus.irthermometer;

import java.util.Date;

public class ThermometerParser {
    enum MEASURE_MODE {MODE_IR, MODE_AMBIENT};
    private MEASURE_MODE measureMode;

    private Measurement measurement;
    private boolean stopped;
    private ParserListener listener;
    int c;
    StringBuilder buf;

    void setMeasureMode(MEASURE_MODE value) {
        measureMode = value;
    }

    public ThermometerParser(
            ParserListener measure_listener
    ) {
        listener = measure_listener;
        measurement = new Measurement();
        reset();
    }

    private void reset() {
        buf = new StringBuilder(32);
        c = 0;
        stopped = false;
        measurement.startTime = 0;
        measurement.maxT = 0;
    }

    public void put(
        byte[] data
    ) {
        put(new String(data));
    }

    public void flush() {
        putTemperature(measurement.maxT);
    }

    public void put(
        String data
    ) {
        buf.append(data);
        String s = buf.toString();
        int e = 0;
        int t = 0;
        while (!s.isEmpty()) {
            t = parseObjectTemp(s);
            if (t < 0)
                break;
            // control mode
            if (measureMode == MEASURE_MODE.MODE_AMBIENT) {
                measurement.ambientT = t;
                listener.onCurrentTemperature(t);
                measureMode = MEASURE_MODE.MODE_IR;
                return;
            }
            measurement.currentTime = new Date().getTime();
            processWindow(t);
            int l = s.indexOf('\n');
            if (l > 0) {
                s = s.substring(l + 1);
                e += l + 1;
            }
        }
        if (e > 0) {
            buf.delete(0, e);
            listener.onCurrentTemperature(t);
        }
    }

    private int parseObjectTemp(
        String s
    )
    {
        int l = s.indexOf('\n');
        if (l > 1) {
            if (s.charAt(l) == '\n') {
                int l2d;
                if (s.charAt(l - 1) == '\r') {
                    l2d = l - 1;
                } else {
                    l2d = l;
                }
                try {
                    return Integer.parseInt(s.substring(0, l2d));
                } catch (Exception ignored) {
                }
            }
        }
        return -1;
    }

    private void putTemperature(
            int t
    ) {
        listener.onValue(new Measurement(measurement, t));
    }

    private void processWindow(
        int temperature
    )
    {
        if (measurement.dt <= 0) {
            // just log data
            putTemperature(temperature);
            return;
        }

        // temperature is too low or too high. It is not a human.
        if (temperature < measurement.temperature0 || temperature > measurement.temperature1) {
            // process min
            if (temperature < measurement.minT) {
                measurement.minT = temperature;
            }
            if (measurement.startTime > 0) {
                // Object is lost
                // Report about last object
                // options.maxT is correct value (because startTime > 0)
                putTemperature(measurement.maxT);
            } else {
                // Did not see any object yet
                return;
            }
            // reset time
            measurement.startTime = 0;
            measurement.sentTime = 0;
            measurement.sentTemperature = 0;
            // reset max
            measurement.maxT = 0;
            return;
        }

        // temperature is OK

        if (measurement.startTime == 0) {
            // New measurement, just started
            measurement.startTime = new Date().getTime();
            measurement.maxT = temperature;
            // Ready for new measurements
            return;
        }

        // Have old measurements

        if (temperature > measurement.maxT) {
            // Store new max temperature
            measurement.maxT = temperature;
        }

        if (measurement.maxOnly) {
            // output if object is lost only
            return;
        }

        if (measurement.sentTime == 0) {
            // not sent yet
            // Check is it time to report
            if (measurement.currentTime - measurement.startTime > measurement.dt) {
                // need to report first time
                putTemperature(measurement.maxT);
                // remember time of last report
                measurement.sentTime = measurement.currentTime;
                measurement.sentTemperature = measurement.maxT; // remember, check on flush
            }
        } else {
            // Sent one or more report(s) already
            if (measurement.currentTime - measurement.sentTime > measurement.dt) {
                // need to re-report if temperature is higher
                if (measurement.maxT > measurement.sentTemperature) {
                    putTemperature(measurement.maxT);
                    measurement.sentTemperature = measurement.maxT; // remember, check on flush
                }
            }
            // remember time of last report
            measurement.sentTime = measurement.currentTime;
        }
    }

    private void sendRequestObjectTemperature()
    {
        char c = '0';
        // write(options.fd, &c, 1);
    }

    public void setDtSeconds(int value) {
        measurement.dt = 1000 * value;
    }
}
