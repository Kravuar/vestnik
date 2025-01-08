package net.kravuar.vestnik.processor.nodes

import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import java.util.Optional

interface AIArticleProcessingNodesFacade {
    data class ChainInfo(
        val id: Long,
        val source: Source,
        val mode: String,
    )
    fun getChains(): List<ChainInfo>
    fun getChains(page: Int): Page<ChainInfo>
    fun getChain(source: Source, mode: String): List<ChainedAIArticleProcessingNode>
    fun getModes(source: Source): List<String>
    fun getModes(source: Source, page: Int): Page<String>

    fun createChain(source: Source, mode: String): List<ChainedAIArticleProcessingNode>
    fun deleteChain(source: Source, mode: String): List<ChainedAIArticleProcessingNode>

    data class AIArticleProcessingNodeInput(
        var prompt: Optional<String> = Optional.empty(),
        var model: Optional<String> = Optional.empty(),
        var temperature: Optional<Double> = Optional.empty(),
    )
    fun getReprocessNode(): AIArticleProcessingNode
    fun insertNode(prevNodeId: Long, input: AIArticleProcessingNodeInput): ChainedAIArticleProcessingNode
    fun deleteNode(nodeId: Long): ChainedAIArticleProcessingNode
    fun updateNode(nodeId: Long, input: AIArticleProcessingNodeInput): ChainedAIArticleProcessingNode
}