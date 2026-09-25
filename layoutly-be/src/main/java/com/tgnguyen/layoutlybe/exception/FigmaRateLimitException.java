package com.tgnguyen.layoutlybe.exception;

public class FigmaRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    private final String planTier;
    private final String rateLimitType;
    private final String upgradeUrl;

    public FigmaRateLimitException(long retryAfterSeconds) {
        this(retryAfterSeconds, null, null, null);
    }

    public FigmaRateLimitException(long retryAfterSeconds, String planTier,
                                   String rateLimitType, String upgradeUrl) {
        super("Figma dang gioi han request. Co the thu lai sau " + retryAfterSeconds + " giay.");
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
        this.planTier = planTier;
        this.rateLimitType = rateLimitType;
        this.upgradeUrl = upgradeUrl;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getPlanTier() {
        return planTier;
    }

    public String getRateLimitType() {
        return rateLimitType;
    }

    public String getUpgradeUrl() {
        return upgradeUrl;
    }
}
