package com.hoppin.infra.crawling.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppin.global.config.AnalysisJobDispatchProperties;
import com.hoppin.infra.crawling.client.AnalysisAutomationWebhookClient;
import com.hoppin.infra.crawling.dto.request.AnalysisJobWebhookRequest;
import com.hoppin.infra.crawling.entity.PromotionAnalysisJob;
import com.hoppin.infra.crawling.repository.PromotionAnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobSqsDispatchConsumer {

    private final AnalysisJobDispatchProperties dispatchProperties;
    private final PromotionAnalysisJobRepository promotionAnalysisJobRepository;
    private final AnalysisAutomationWebhookClient analysisAutomationWebhookClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.analysis-job-dispatch.sqs-consume-fixed-delay-millis:1000}")
    public void consumeDispatchMessages() {
        if (!dispatchProperties.queueConfigured()) {
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(
                        ReceiveMessageRequest.builder()
                                .queueUrl(dispatchProperties.queueUrl())
                                .maxNumberOfMessages(dispatchProperties.sqsConsumeMaxMessages())
                                .waitTimeSeconds(dispatchProperties.sqsConsumeWaitSeconds())
                                .build()
                )
                .messages();

        for (Message message : messages) {
            handleMessage(message);
        }
    }

    private void handleMessage(Message message) {
        AnalysisJobWebhookRequest payload = deserialize(message.body());

        if (payload == null) {
            deleteMessage(message.receiptHandle());
            return;
        }

        try {
            boolean claimed = markDispatching(payload.analysisJobId());
            if (!claimed) {
                deleteMessage(message.receiptHandle());
                return;
            }

            analysisAutomationWebhookClient.trigger(payload.analysisJobId(), payload.promotionId());
            markDispatched(payload.analysisJobId());
            deleteMessage(message.receiptHandle());
        } catch (RuntimeException exception) {
            revertToPending(payload.analysisJobId(), exception.getMessage());
            log.error(
                    "Failed to dispatch analysis job webhook from SQS. analysisJobId={}, promotionId={}, message={}",
                    payload.analysisJobId(),
                    payload.promotionId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    @Transactional
    protected boolean markDispatching(Long analysisJobId) {
        return promotionAnalysisJobRepository.markDispatchingIfPending(analysisJobId) > 0;
    }

    @Transactional
    protected void markDispatched(Long analysisJobId) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new IllegalArgumentException("분석 작업이 존재하지 않습니다. id=" + analysisJobId));
        job.markDispatched();
    }

    @Transactional
    protected void revertToPending(Long analysisJobId, String errorMessage) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new IllegalArgumentException("분석 작업이 존재하지 않습니다. id=" + analysisJobId));
        job.revertToPending(errorMessage);
    }

    private void deleteMessage(String receiptHandle) {
        sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(dispatchProperties.queueUrl())
                        .receiptHandle(receiptHandle)
                        .build()
        );
    }

    private AnalysisJobWebhookRequest deserialize(String body) {
        try {
            return objectMapper.readValue(body, AnalysisJobWebhookRequest.class);
        } catch (JsonProcessingException exception) {
            log.error("Failed to deserialize analysis job dispatch message. body={}", body, exception);
            return null;
        }
    }
}
