package net.kravuar.vestnik.processor

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.PagingAndSortingRepository

internal interface AIArticleProcessingNodesRepository: PagingAndSortingRepository<AIArticleProcessingNode, Long>, JpaRepository<AIArticleProcessingNode, Long> {
    fun findBySourceAndModeAndParentIsNull(source: String, mode: String): AIArticleProcessingNode
    fun findAllByParentIsNull(): List<AIArticleProcessingNode>
    fun findAllBySourceAndParentIsNull(source: String): List<AIArticleProcessingNode>
}