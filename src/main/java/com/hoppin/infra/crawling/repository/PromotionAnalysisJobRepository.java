package com.hoppin.infra.crawling.repository;

import com.hoppin.infra.crawling.entity.PromotionAnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionAnalysisJobRepository extends JpaRepository<PromotionAnalysisJob, Long> {

    Optional<PromotionAnalysisJob> findTopByPromotion_IdOrderByCreatedAtDesc(Long promotionId);

    List<PromotionAnalysisJob> findByPromotion_IdAndStatusInOrderByCreatedAtDesc(
            Long promotionId,
            Collection<com.hoppin.infra.crawling.enumtype.AnalysisJobStatus> statuses
    );

    @Modifying
    @Query("""
    delete from PromotionAnalysisJob j
    where j.promotion.id = :promotionId
""")
    void deleteByPromotionId(@Param("promotionId") Long promotionId);

    @Modifying
    @Query("""
    update PromotionAnalysisJob j
    set j.status = com.hoppin.infra.crawling.enumtype.AnalysisJobStatus.DISPATCHING,
        j.errorMessage = null
    where j.id = :analysisJobId
      and j.status = com.hoppin.infra.crawling.enumtype.AnalysisJobStatus.PENDING
""")
    int markDispatchingIfPending(@Param("analysisJobId") Long analysisJobId);

}
