package net.kravuar.vestnik.processor.nodes

import jakarta.transaction.Transactional
import net.kravuar.vestnik.source.Source
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
@Transactional
internal interface ChainedAiArticleProcessingNodesRepository :
    PagingAndSortingRepository<ChainedAIArticleProcessingNode, Long>,
    JpaRepository<ChainedAIArticleProcessingNode, Long> {

    @Query(
        """
        SELECT c
        FROM ChainedAIArticleProcessingNode c
        LEFT JOIN c.source s
        WHERE c.parent IS NULL
          AND c.mode = :mode
          AND (
                (:source IS NULL AND s IS NULL) OR
                (:source IS NOT NULL AND s = :source AND s.deleted = false)
          )
    """
    )
    fun findRoot(
        @Param("source") source: Source?,
        @Param("mode") mode: String
    ): Optional<ChainedAIArticleProcessingNode>

    @Query(
        """
        SELECT c
        FROM ChainedAIArticleProcessingNode c
        LEFT JOIN c.source s
        WHERE c.parent IS NULL
          AND (
                (:source IS NULL AND s IS NULL) OR
                (:source IS NOT NULL AND s = :source AND s.deleted = false)
          )
    """
    )
    fun findRoots(source: Source?, pageable: Pageable): Page<ChainedAIArticleProcessingNode>

    @Query(
        """
        SELECT c
        FROM ChainedAIArticleProcessingNode c
        LEFT JOIN c.source s
        WHERE c.parent IS NULL
            AND (
                    s IS NULL OR                    
                    s = :source AND s.deleted = FALSE
            )
    """
    )
    fun findAllModes(
        @Param("source") source: Source?,
        pageable: Pageable
    ): Page<ChainedAIArticleProcessingNode>

    fun existsByMode(mode: String): Boolean
    fun deleteAllBySourceAndMode(source: Source?, mode: String): Int
}