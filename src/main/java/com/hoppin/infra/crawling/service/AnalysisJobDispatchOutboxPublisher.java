package com.hoppin.infra.crawling.service;

import com.hoppin.global.config.AnalysisJobDispatchProperties;
import com.hoppin.infra.crawling.entity.AnalysisJobDispatchOutbox;
import com.hoppin.infra.crawling.enumtype.AnalysisJobDispatchOutboxStatus;
import com.hoppin.infra.crawling.repository.AnalysisJobDispatchOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobDispatchOutboxPublisher {

    private final AnalysisJobDispatchOutboxRepository analysisJobDispatchOutboxRepository;
    private final AnalysisJobDispatchProperties dispatchProperties;
    private final SqsClient sqsClient;

    @Scheduled(fixedDelayString = "${app.analysis-job-dispatch.outbox-publish-fixed-delay-millis:2000}")
    @Transactional
    public void publishPendingMessages() {
        if (!dispatchProperties.queueConfigured()) {
            return;
        }

        List<AnalysisJobDispatchOutbox> pendingMessages =
                analysisJobDispatchOutboxRepository.findByStatusOrderByIdAsc(
                        AnalysisJobDispatchOutboxStatus.PENDING,
                        PageRequest.of(0, dispatchProperties.outboxPublishBatchSize())
                );

        for (AnalysisJobDispatchOutbox outbox : pendingMessages) {
            try {
                sqsClient.sendMessage(
                        SendMessageRequest.builder()
                                .queueUrl(dispatchProperties.queueUrl())
                                .messageBody(outbox.getPayload())
                                .build()
                );
                outbox.markPublished();
            } catch (RuntimeException exception) {
                outbox.markPublishFailed(exception.getMessage());
                log.error(
                        "Failed to publish analysis job outbox message. outboxId={}, analysisJobId={}, message={}",
                        outbox.getId(),
                        outbox.getAnalysisJobId(),
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}
