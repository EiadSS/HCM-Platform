package com.portfolio.hcm.analytics;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {
    @Query("""
            select event
            from AnalyticsEvent event
            where event.deleted = false
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            order by event.occurredAt desc
            """)
    List<AnalyticsEvent> findRecent(
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant,
            Pageable pageable
    );

    @Query("""
            select count(event)
            from AnalyticsEvent event
            where event.deleted = false
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            """)
    long countEvents(@Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant);

    @Query("""
            select count(event)
            from AnalyticsEvent event
            where event.deleted = false
              and event.eventType = :eventType
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            """)
    long countByEventType(
            @Param("eventType") String eventType,
            @Param("fromInstant") Instant fromInstant,
            @Param("toInstant") Instant toInstant
    );

    @Query("""
            select count(distinct event.visitorHash)
            from AnalyticsEvent event
            where event.deleted = false
              and event.visitorHash is not null
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            """)
    long countUniqueVisitors(@Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant);

    @Query("""
            select max(event.occurredAt)
            from AnalyticsEvent event
            where event.deleted = false
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            """)
    Optional<Instant> findLastUsedAt(@Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant);

    @Query("""
            select count(distinct event.visitorHash)
            from AnalyticsEvent event
            where event.deleted = false
              and event.visitorHash is not null
              and event.occurredAt >= :since
            """)
    long countActiveVisitors(@Param("since") Instant since);

    @Query("""
            select event.path, count(event)
            from AnalyticsEvent event
            where event.deleted = false
              and event.eventType = 'PAGE_VIEW'
              and event.path is not null
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            group by event.path
            order by count(event) desc, event.path asc
            """)
    List<Object[]> topPages(@Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant, Pageable pageable);

    @Query("""
            select event.accountRole, count(event)
            from AnalyticsEvent event
            where event.deleted = false
              and event.eventType = 'LOGIN_SUCCESS'
              and event.accountRole is not null
              and event.occurredAt >= :fromInstant
              and event.occurredAt <= :toInstant
            group by event.accountRole
            order by count(event) desc, event.accountRole asc
            """)
    List<Object[]> loginRoles(@Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant, Pageable pageable);
}
