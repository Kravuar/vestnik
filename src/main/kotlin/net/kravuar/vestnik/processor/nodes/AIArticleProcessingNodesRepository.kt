package net.kravuar.vestnik.processor.nodes

import jakarta.transaction.Transactional
import net.kravuar.vestnik.source.Source
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
@Transactional
internal interface AIArticleProcessingNodesRepository :
    PagingAndSortingRepository<ChainedAIArticleProcessingNode, Long>,
    JpaRepository<ChainedAIArticleProcessingNode, Long> {
    fun findBySourceAndModeAndParentIsNullAndSourceDeletedIsFalse(
        source: Source,
        mode: String
    ): Optional<ChainedAIArticleProcessingNode>

    fun findAllByParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(): List<ChainedAIArticleProcessingNode>
    fun findAllByParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(pageable: Pageable): Page<ChainedAIArticleProcessingNode>
    fun findAllBySourceAndParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(source: Source): List<ChainedAIArticleProcessingNode>
    fun findAllBySourceAndParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(
        source: Source,
        pageable: Pageable
    ): Page<ChainedAIArticleProcessingNode>

    fun deleteAllBySourceAndMode(source: Source, mode: String): Int
}