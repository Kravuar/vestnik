package net.kravuar.vestnik.processor.nodes

import jakarta.transaction.Transactional
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
        WHERE c.parent IS NULL
          AND c.mode = :mode
    """
    )
    fun findRoot(
        @Param("mode") mode: String
    ): Optional<ChainedAIArticleProcessingNode>

    @Query(
        """
        SELECT c
        FROM ChainedAIArticleProcessingNode c
        WHERE c.parent IS NULL
    """
    )
    fun findRoots(pageable: Pageable): Page<ChainedAIArticleProcessingNode>

    @Query(
        """
        SELECT c
        FROM ChainedAIArticleProcessingNode c
        WHERE c.parent IS NULL
    """
    )
    fun findAllModes(
        pageable: Pageable
    ): Page<ChainedAIArticleProcessingNode>

    fun existsByMode(mode: String): Boolean
    fun deleteAllByMode(mode: String): Int
}