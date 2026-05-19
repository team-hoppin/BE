package com.hoppin.infra.crawling.service;

import com.hoppin.domain.MusicPromotion.entity.MusicPromotion;
import com.hoppin.domain.MusicPromotion.repository.MusicPromotionRepository;
import com.hoppin.domain.PromotionTrackingLink.entity.PromotionTrackingLink;
import com.hoppin.domain.PromotionTrackingLink.repository.PromotionTrackingLinkRepository;
import com.hoppin.domain.mypage.service.MyPageSseService;
import com.hoppin.infra.crawling.entity.PromotionAnalysisCrawledPost;
import com.hoppin.infra.crawling.entity.PromotionAnalysisJob;
import com.hoppin.infra.crawling.enumtype.AnalysisJobStatus;
import com.hoppin.infra.crawling.repository.PromotionAnalysisCrawledPostRepository;
import com.hoppin.infra.crawling.repository.PromotionAnalysisJobRepository;
import com.hoppin.domain.musician.entity.Musician;
import com.hoppin.global.exception.ResourceNotFoundException;
import com.hoppin.infra.crawling.dto.request.AnalysisCrawlerResultRequest;
import com.hoppin.infra.crawling.dto.request.AnalysisJobWebhookRequest;
import com.hoppin.infra.ai.dto.request.AnalysisCreateRequest;
import com.hoppin.infra.crawling.dto.response.AnalysisCrawledPostResponse;
import com.hoppin.infra.crawling.dto.response.AnalysisCrawlerResultResponse;
import com.hoppin.infra.crawling.dto.response.AnalysisJobCreateResponse;
import com.hoppin.infra.crawling.dto.response.AnalysisJobContextResponse;
import com.hoppin.infra.crawling.dto.response.AnalysisJobStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PromotionAnalysisJobService {

    private final PromotionAnalysisJobRepository promotionAnalysisJobRepository;
    private final PromotionAnalysisCrawledPostRepository promotionAnalysisCrawledPostRepository;
    private final MusicPromotionRepository musicPromotionRepository;
    private final PromotionTrackingLinkRepository promotionTrackingLinkRepository;
    private final AnalysisJobDispatchOutboxService analysisJobDispatchOutboxService;
    private final MyPageSseService myPageSseService;

    public AnalysisJobCreateResponse createJob(Long musicianId, Long promotionId, AnalysisCreateRequest request) {
        validateCreateRequest(request);

        MusicPromotion promotion = musicPromotionRepository.findById(promotionId)
                .orElseThrow(() -> new IllegalArgumentException("프로모션이 존재하지 않습니다. id=" + promotionId));

        validateOwner(promotion, musicianId);

        Musician musician = promotion.getMusician();

        PromotionAnalysisJob job = promotionAnalysisJobRepository.save(
                PromotionAnalysisJob.builder()
                        .promotion(promotion)
                        .musician(musician)
                        .sinceDate(request.getSinceDate())
                        .instagramUsername(request.getInstagramUsername())
                        .mainPainPoint(request.getMainPainPoint())
                        .mainResourceConstraint(request.getMainResourceConstraint())
                        .status(AnalysisJobStatus.PENDING)
                        .build()
        );

        analysisJobDispatchOutboxService.enqueue(new AnalysisJobWebhookRequest(job.getId(), promotion.getId()));
        myPageSseService.publishPromotionUpdatedAfterCommit(musicianId, promotionId);

        return new AnalysisJobCreateResponse(job.getId(), job.getStatus().name());
    }

    @Transactional(readOnly = true)
    public AnalysisJobStatusResponse getJobStatus(Long musicianId, Long analysisJobId) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new IllegalArgumentException("분석 작업이 존재하지 않습니다. id=" + analysisJobId));

        if (!job.getMusician().getId().equals(musicianId)) {
            throw new AccessDeniedException("본인의 분석 작업만 조회할 수 있습니다.");
        }

        return new AnalysisJobStatusResponse(
                job.getId(),
                job.getPromotion().getId(),
                job.getSinceDate(),
                job.getInstagramUsername(),
                job.getStatus().name(),
                job.getMainPainPoint(),
                job.getMainResourceConstraint(),
                job.getContentCount(),
                job.getFollowerCount(),
                job.getTotalLikeCount(),
                job.getTotalCommentCount(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage()
        );
    }

    @Transactional(readOnly = true)
    public AnalysisJobContextResponse getJobContext(Long analysisJobId) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new ResourceNotFoundException("분석 작업이 존재하지 않습니다."));

        PromotionTrackingLink trackingLink = promotionTrackingLinkRepository.findFirstByPromotionId(job.getPromotion().getId())
                .orElseThrow(() -> new ResourceNotFoundException("홍보에 연결된 스마트 링크를 찾을 수 없습니다."));

        return new AnalysisJobContextResponse(
                job.getId(),
                job.getPromotion().getId(),
                job.getMusician().getId(),
                job.getInstagramUsername(),
                job.getSinceDate(),
                job.getMainPainPoint(),
                job.getMainResourceConstraint(),
                trackingLink.getTrackingUrl(),
                job.getPromotion().getReleaseDate()
        );
    }

    public void saveCrawlerResult(Long analysisJobId, AnalysisCrawlerResultRequest request) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new ResourceNotFoundException("분석 작업이 존재하지 않습니다."));

        promotionAnalysisCrawledPostRepository.deleteByAnalysisJobId(analysisJobId);

        if (request.getPosts() != null) {
            for (AnalysisCrawlerResultRequest.CrawledPost post : request.getPosts()) {
                promotionAnalysisCrawledPostRepository.save(
                        PromotionAnalysisCrawledPost.builder()
                                .analysisJob(job)
                                .mediaId(post.getMediaId())
                                .caption(post.getCaption())
                                .mediaType(post.getMediaType())
                                .permalink(post.getPermalink())
                                .timestamp(post.getTimestamp())
                                .likeCount(post.getLikeCount())
                                .commentCount(post.getCommentCount())
                                .build()
                );
            }
        }
        System.out.println("follower count : " + request.getFollowerCount());

        job.updateCrawlerSummary(
                request.getContentCount(),
                request.getFollowerCount(),
                request.getTotalLikeCount(),
                request.getTotalCommentCount()
        );
        job.markCompleted();
        myPageSseService.publishPromotionUpdatedAfterCommit(job.getMusician().getId(), job.getPromotion().getId());
    }

    public void markJobRunning(Long analysisJobId) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new ResourceNotFoundException("분석 작업이 존재하지 않습니다."));
        job.markRunning();
        myPageSseService.publishPromotionUpdatedAfterCommit(job.getMusician().getId(), job.getPromotion().getId());
    }

    public void markJobFailed(Long analysisJobId, String errorMessage) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new ResourceNotFoundException("분석 작업이 존재하지 않습니다."));
        job.markFailed(errorMessage);
        myPageSseService.publishPromotionUpdatedAfterCommit(job.getMusician().getId(), job.getPromotion().getId());
    }

    @Transactional(readOnly = true)
    public AnalysisCrawlerResultResponse getCrawlerResult(Long musicianId, Long analysisJobId) {
        PromotionAnalysisJob job = promotionAnalysisJobRepository.findById(analysisJobId)
                .orElseThrow(() -> new ResourceNotFoundException("분석 작업이 존재하지 않습니다."));

        if (!job.getMusician().getId().equals(musicianId)) {
            throw new AccessDeniedException("본인의 분석 작업만 조회할 수 있습니다.");
        }

        List<AnalysisCrawledPostResponse> posts = promotionAnalysisCrawledPostRepository
                .findByAnalysisJobIdOrderByTimestampDesc(analysisJobId)
                .stream()
                .map(AnalysisCrawledPostResponse::from)
                .toList();

        return new AnalysisCrawlerResultResponse(
                job.getId(),
                job.getContentCount(),
                job.getFollowerCount(),
                job.getTotalLikeCount(),
                job.getTotalCommentCount(),
                posts
        );
    }

    private void validateOwner(MusicPromotion promotion, Long musicianId) {
        if (!promotion.getMusician().getId().equals(musicianId)) {
            throw new AccessDeniedException("본인의 프로모션만 분석할 수 있습니다.");
        }
    }

    private void validateCreateRequest(AnalysisCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문은 필수입니다.");
        }
        if (request.getSinceDate() == null) {
            throw new IllegalArgumentException("sinceDate는 필수입니다.");
        }
        if (request.getInstagramUsername() == null || request.getInstagramUsername().isBlank()) {
            throw new IllegalArgumentException("instagramUsername은 필수입니다.");
        }
    }

}
