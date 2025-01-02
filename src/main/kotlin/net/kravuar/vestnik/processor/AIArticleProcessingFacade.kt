package net.kravuar.vestnik.processor

import java.util.Optional

interface AIArticleProcessingFacade {
    data class SequenceInfo(
        val id: Long,
        val source: String,
        val mode: String,
    )

    fun findRoot(source: String, mode: String): AIArticleProcessingNode

    fun getSequences(): List<SequenceInfo>
    fun getSequence(source: String, mode: String): List<AIArticleProcessingNode>
    fun getModes(source: String): List<String>

    fun createSequence(sourceName: String, mode: String): List<AIArticleProcessingNode>
    fun deleteSequence(sourceName: String, mode: String): List<AIArticleProcessingNode>

    data class AIArticleProcessingNodeInput(
        val prompt: Optional<String>,
        val model: Optional<String>,
        val temperature: Optional<Double>,
    )

    fun insertNode(prevNodeId: Long, input: AIArticleProcessingNodeInput): AIArticleProcessingNode
    fun deleteNode(nodeId: Long): AIArticleProcessingNode
    fun updateNode(nodeId: Long, input: AIArticleProcessingNodeInput): AIArticleProcessingNode
}