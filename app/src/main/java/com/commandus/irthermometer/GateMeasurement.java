package com.commandus.irthermometer;

public class GateMeasurement {
    public Measurement measurement;
    public long gateId;
    public long secret;
    public long id;
    public String url;
    public int port;
    public double e;

    public GateMeasurement(Measurement measurement,
                           long id, long gateId, long secret, String url, int port, double e) {
        this.measurement = measurement;
        this.gateId = gateId;
        this.secret = secret;
        this.id = id;
        this.url = url;
        this.port = port;
        this.e = e;
    }
}
