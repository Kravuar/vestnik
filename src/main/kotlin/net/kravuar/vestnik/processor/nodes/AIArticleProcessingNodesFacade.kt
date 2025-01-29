package net.kravuar.vestnik.processor.nodes

import net.kravuar.vestnik.commons.Page
import java.util.Optional

interface AIArticleProcessingNodesFacade {
    data class ChainInfo(
        val id: Long,
        val mode: String,
    )

    fun getChains(page: Int): Page<ChainInfo>
    fun getChain(mode: String): List<ChainedAIArticleProcessingNode>
    fun getChain(mode: String, page: Int): Page<ChainedAIArticleProcessingNode>
    fun getModes(page: Int): Page<String>

    fun createChain(mode: String): List<ChainedAIArticleProcessingNode>
    fun deleteChain(mode: String): Boolean

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