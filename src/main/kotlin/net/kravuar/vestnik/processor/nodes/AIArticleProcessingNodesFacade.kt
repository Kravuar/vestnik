package net.kravuar.vestnik.processor.nodes

import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import java.util.Optional

interface AIArticleProcessingNodesFacade {
    data class ChainInfo(
        val id: Long,
        val source: Source?,
        val mode: String,
    )
    fun getChains(source: Source? = null, page: Int): Page<ChainInfo>
    fun getChain(source: Source? = null, mode: String): List<ChainedAIArticleProcessingNode>
    fun getChain(source: Source? = null, mode: String, page: Int): Page<ChainedAIArticleProcessingNode>
    fun getModes(source: Source? = null, page: Int): Page<String>

    fun createChain(source: Source? = null, mode: String): List<ChainedAIArticleProcessingNode>
    fun deleteChain(source: Source? = null, mode: String): Boolean

    data class AIArticleProcessingNodeInput(
        var prompt: Optional<String> = Optional.empty(),
        var model: Optional<String> = Optional.empty(),
        var temperature: Optional<Double> = Optional.empty(),
    )
    fun getReprocessNode(): AIArticleProcessingNode
    fun insertNode(prevNodeId: Long, input: AIArticleProcessingNodeInput): ChainedAIArticleProcessingNode
    fun deleteNode(nodeId: Long): Boolean
    fun updateNode(nodeId: Long, input: AIArticleProcessingNodeInput): ChainedAIArticleProcessingNode
}