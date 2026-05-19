package com.hoppin.infra.crawling.entity;

import com.hoppin.domain.common.entity.BaseEntity;
import com.hoppin.infra.crawling.enumtype.AnalysisJobDispatchOutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "analysis_job_dispatch_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJobDispatchOutbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long id;

    @Column(name = "analysis_job_id", nullable = false)
    private Long analysisJobId;

    @Column(name = "promotion_id", nullable = false)
    private Long promotionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisJobDispatchOutboxStatus status;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Builder
    private AnalysisJobDispatchOutbox(
            Long analysisJobId,
            Long promotionId,
            String payload
    ) {
        this.analysisJobId = analysisJobId;
        this.promotionId = promotionId;
        this.payload = payload;
        this.status = AnalysisJobDispatchOutboxStatus.PENDING;
        this.publishAttemptCount = 0;
    }

    public void markPublished() {
        this.status = AnalysisJobDispatchOutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markPublishFailed(String errorMessage) {
        this.publishAttemptCount += 1;
        this.lastError = errorMessage;
    }
}
