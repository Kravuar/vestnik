package net.kravuar.vestnik.processor

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository

internal interface AIArticleProcessingNodesRepository: PagingAndSortingRepository<AIArticleProcessingNode, Long>, JpaRepository<AIArticleProcessingNode, Long> {
    fun findBySourceAndModeAndParentIsNullAndSourceDisabledIsFalse(source: String, mode: String): AIArticleProcessingNode
    fun findAllByParentIsNullAndSourceDisabledIsFalse(): List<AIArticleProcessingNode>
    fun findAllBySourceAndParentIsNullAndSourceDisabledIsFalse(source: String): List<AIArticleProcessingNode>
}