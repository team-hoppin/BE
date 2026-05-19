package com.hoppin.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.analysis-job-dispatch")
public record AnalysisJobDispatchProperties(
        String queueUrl,
        int outboxPublishBatchSize,
        long outboxPublishFixedDelayMillis,
        int sqsConsumeMaxMessages,
        int sqsConsumeWaitSeconds,
        long sqsConsumeFixedDelayMillis
) {
    public boolean queueConfigured() {
        return queueUrl != null && !queueUrl.isBlank();
    }
}
