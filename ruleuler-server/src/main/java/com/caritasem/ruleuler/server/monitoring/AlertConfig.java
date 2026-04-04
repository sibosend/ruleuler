package com.caritasem.ruleuler.server.monitoring;

public class AlertConfig {
    private double missingRateMax = 0.05;
    private double missingRateSpikeDelta = 0.1;
    private double outlierRateMax = 0.03;
    private double skewnessAbsMax = 2.0;
    private double psiWarning = 0.1;
    private double psiAlert = 0.2;
    private double enumDriftThreshold = 0.15;

    public double getMissingRateMax() { return missingRateMax; }
    public void setMissingRateMax(double missingRateMax) { this.missingRateMax = missingRateMax; }

    public double getMissingRateSpikeDelta() { return missingRateSpikeDelta; }
    public void setMissingRateSpikeDelta(double missingRateSpikeDelta) { this.missingRateSpikeDelta = missingRateSpikeDelta; }

    public double getOutlierRateMax() { return outlierRateMax; }
    public void setOutlierRateMax(double outlierRateMax) { this.outlierRateMax = outlierRateMax; }

    public double getSkewnessAbsMax() { return skewnessAbsMax; }
    public void setSkewnessAbsMax(double skewnessAbsMax) { this.skewnessAbsMax = skewnessAbsMax; }

    public double getPsiWarning() { return psiWarning; }
    public void setPsiWarning(double psiWarning) { this.psiWarning = psiWarning; }

    public double getPsiAlert() { return psiAlert; }
    public void setPsiAlert(double psiAlert) { this.psiAlert = psiAlert; }

    public double getEnumDriftThreshold() { return enumDriftThreshold; }
    public void setEnumDriftThreshold(double enumDriftThreshold) { this.enumDriftThreshold = enumDriftThreshold; }
}
