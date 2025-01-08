package net.kravuar.vestnik.processor.nodes

import net.kravuar.vestnik.source.Source
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository

internal interface AIArticleProcessingNodesRepository: PagingAndSortingRepository<ChainedAIArticleProcessingNode, Long>, JpaRepository<ChainedAIArticleProcessingNode, Long> {
    fun findBySourceAndModeAndParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(source: Source, mode: String): ChainedAIArticleProcessingNode
    fun findAllByParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(): List<ChainedAIArticleProcessingNode>
    fun findAllBySourceAndParentIsNullAndSourceDeletedIsFalseAndSourceSuspendedIsFalse(source: Source): List<ChainedAIArticleProcessingNode>
}