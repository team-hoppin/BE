package com.hoppin.infra.crawling.repository;

import com.hoppin.infra.crawling.entity.AnalysisJobDispatchOutbox;
import com.hoppin.infra.crawling.enumtype.AnalysisJobDispatchOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisJobDispatchOutboxRepository extends JpaRepository<AnalysisJobDispatchOutbox, Long> {

    List<AnalysisJobDispatchOutbox> findByStatusOrderByIdAsc(
            AnalysisJobDispatchOutboxStatus status,
            Pageable pageable
    );
}
