package com.hoppin.infra.crawling.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoppin.infra.crawling.dto.request.AnalysisJobWebhookRequest;
import com.hoppin.infra.crawling.entity.AnalysisJobDispatchOutbox;
import com.hoppin.infra.crawling.repository.AnalysisJobDispatchOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisJobDispatchOutboxService {

    private final AnalysisJobDispatchOutboxRepository analysisJobDispatchOutboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(AnalysisJobWebhookRequest request) {
        analysisJobDispatchOutboxRepository.save(
                AnalysisJobDispatchOutbox.builder()
                        .analysisJobId(request.analysisJobId())
                        .promotionId(request.promotionId())
                        .payload(toPayload(request))
                        .build()
        );
    }

    private String toPayload(AnalysisJobWebhookRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("분석 작업 dispatch payload 직렬화에 실패했습니다.", exception);
        }
    }
}
