package com.commandus.irthermometer;

public class Measurement {
    int temperature0;
    int temperature1;
    int maxT;
    int minT;
    int ambientT;
    int dt;
    long currentTime;
    long startTime;
    long sentTime;
    int sentTemperature;
    boolean maxOnly;

    public void reset() {
        maxT = 0;
        minT = temperature1;
        ambientT = 0;
        dt = 1000; // ms
        currentTime = 0;
        startTime = 0;
        sentTime = 0;
        sentTemperature = 0;
        maxOnly = false;
        // measureMode = MEASURE_MODE.MODE_AMBIENT;
    }

    public Measurement() {
        temperature0 = 27315 + 3000;    // 30C
        temperature1 = 27315 + 4200;    // 42C
        reset();
    }

    public Measurement(
            int temperature0,
            int temperature1

    ) {
        reset();
        this.temperature0 = temperature0;
        this.temperature1 = temperature1;
    }

    public Measurement(Measurement m, int temperature) {
        maxT = temperature;
        minT = m.minT;
        ambientT = m.ambientT;
        dt = m.dt;
        currentTime = m.currentTime;
        startTime = m.startTime;
        sentTime = m.sentTime;
        sentTemperature = m.sentTemperature;
        maxOnly = m.maxOnly;
    }

    @Override
    public String toString() {
        return "Measurement {" +
                ", \"t\": " + maxT +
                ", \"tmin\": " + minT +
                ", \"tambient\": " + ambientT +
                ", \"time\": " + startTime +
                '}';
    }
}
