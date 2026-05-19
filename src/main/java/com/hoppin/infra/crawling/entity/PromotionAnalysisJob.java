package com.hoppin.infra.crawling.entity;

import com.hoppin.domain.MusicPromotion.entity.MusicPromotion;
import com.hoppin.infra.crawling.enumtype.AnalysisJobStatus;
import com.hoppin.domain.common.entity.BaseEntity;
import com.hoppin.domain.musician.entity.Musician;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "promotion_analysis_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromotionAnalysisJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_job_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private MusicPromotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "musician_id", nullable = false)
    private Musician musician;

    @Column(name = "since_date", nullable = false)
    private LocalDate sinceDate;

    @Column(name = "instagram_username", nullable = false, length = 100)
    private String instagramUsername;

    @Column(name = "main_pain_point", length = 500)
    private String mainPainPoint;

    @Column(name = "main_resource_constraint", length = 500)
    private String mainResourceConstraint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisJobStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "content_count")
    private Integer contentCount;

    @Column(name = "follower_count")
    private Integer followerCount;

    @Column(name = "total_like_count")
    private Integer totalLikeCount;

    @Column(name = "total_comment_count")
    private Integer totalCommentCount;

    @Builder
    private PromotionAnalysisJob(
            MusicPromotion promotion,
            Musician musician,
            LocalDate sinceDate,
            String instagramUsername,
            String mainPainPoint,
            String mainResourceConstraint,
            AnalysisJobStatus status
    ) {
        this.promotion = promotion;
        this.musician = musician;
        this.sinceDate = sinceDate;
        this.instagramUsername = instagramUsername;
        this.mainPainPoint = mainPainPoint;
        this.mainResourceConstraint = mainResourceConstraint;
        this.status = status;
    }

    public void markRunning() {
        this.status = AnalysisJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markDispatching() {
        this.status = AnalysisJobStatus.DISPATCHING;
        this.errorMessage = null;
    }

    public void markDispatched() {
        this.status = AnalysisJobStatus.DISPATCHED;
        this.errorMessage = null;
    }

    public void revertToPending(String errorMessage) {
        this.status = AnalysisJobStatus.PENDING;
        this.errorMessage = errorMessage;
    }

    public void markCompleted() {
        this.status = AnalysisJobStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void updateCrawlerSummary(
            int contentCount,
            int followerCount,
            int totalLikeCount,
            int totalCommentCount
    ) {
        this.contentCount = contentCount;
        this.followerCount = followerCount;
        this.totalLikeCount = totalLikeCount;
        this.totalCommentCount = totalCommentCount;
    }

    public void markFailed(String errorMessage) {
        this.status = AnalysisJobStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
