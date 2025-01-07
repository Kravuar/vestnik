package net.kravuar.vestnik.processor.ai

import net.kravuar.vestnik.source.Source
import java.util.Optional

interface AIArticleProcessingNodesFacade {
    data class SequenceInfo(
        val id: Long,
        val source: Source,
        val mode: String,
    )
    fun getSequences(): List<SequenceInfo>
    fun getSequence(source: Source, mode: String): List<ChainedAIArticleProcessingNode>
    fun getModes(source: Source): List<String>

    fun createSequence(source: Source, mode: String): List<ChainedAIArticleProcessingNode>
    fun deleteSequence(source: Source, mode: String): List<ChainedAIArticleProcessingNode>

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