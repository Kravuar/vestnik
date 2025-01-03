package net.kravuar.vestnik.processor

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODEL
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_TEMPERATURE
import net.kravuar.vestnik.source.Source
import net.kravuar.vestnik.source.SourcesFacade

internal class SimpleAIArticleProcessingFacade(
    private val sourcesFacade: SourcesFacade,
    private val entityManager: EntityManager,
    private val aiArticleProcessingNodesRepository: AIArticleProcessingNodesRepository
) : AIArticleProcessingFacade {
    override fun findRoot(source: String, mode: String): AIArticleProcessingNode {
        return aiArticleProcessingNodesRepository.findBySourceAndModeAndParentIsNullAndSourceDisabledIsFalse(source, mode)
    }


    override fun getSequences(): List<AIArticleProcessingFacade.SequenceInfo> {
        return aiArticleProcessingNodesRepository.findAllByParentIsNullAndSourceDisabledIsFalse()
            .map { AIArticleProcessingFacade.SequenceInfo(it.id!!, it.source.name, it.mode) }
    }

    override fun getSequence(source: String, mode: String): List<AIArticleProcessingNode> {
        return generateSequence(aiArticleProcessingNodesRepository
            .findBySourceAndModeAndParentIsNullAndSourceDisabledIsFalse(source, mode)) { it.child }
            .toList()
    }

    override fun getModes(source: String): List<String> {
        return aiArticleProcessingNodesRepository.findAllBySourceAndParentIsNullAndSourceDisabledIsFalse(source)
            .map { it.mode }
    }

    @Transactional
    override fun createSequence(
        sourceName: String,
        mode: String
    ): List<AIArticleProcessingNode> {
        val source = sourcesFacade.getSource(sourceName)
        val rootNode = createInitialRootNode(source, mode)
        val formattingNode = createInitialFormattingNode(source, mode)

        rootNode.child = formattingNode
        formattingNode.parent = rootNode

        return aiArticleProcessingNodesRepository.saveAll(
            listOf(rootNode, formattingNode)
        )
    }

    @Transactional
    override fun deleteSequence(
        sourceName: String,
        mode: String
    ): List<AIArticleProcessingNode> {
        // Find sequence
        val sequence = getSequence(sourceName, mode)

        // Delete it
        aiArticleProcessingNodesRepository.deleteAll(sequence)

        // Delete it
        return sequence
    }

    @Transactional
    override fun insertNode(
        prevNodeId: Long,
        input: AIArticleProcessingFacade.AIArticleProcessingNodeInput
    ): AIArticleProcessingNode {
        // Find existing
        val previousNode = aiArticleProcessingNodesRepository
            .findById(prevNodeId)
            .orElseThrow { IllegalArgumentException("Предшествующий узел с id=${prevNodeId} не найден") }
        val nextNode = previousNode.child

        // Lock them
        entityManager.lock(previousNode, LockModeType.PESSIMISTIC_WRITE)
        nextNode?.let { entityManager.lock(it, LockModeType.PESSIMISTIC_WRITE) }

        // Insert new
        val newNode = AIArticleProcessingNode(
            previousNode.source,
            previousNode.mode,
            input.model.orElse(DEFAULT_MODEL),
            input.temperature.orElse(DEFAULT_TEMPERATURE),
            input.prompt.orElseThrow { IllegalArgumentException("Промпт не указан") },
            previousNode,
            nextNode
        )

        // Update links
        previousNode.child = newNode
        if (nextNode != null) {
            nextNode.parent = newNode
        }

        return aiArticleProcessingNodesRepository.saveAll(
            listOfNotNull(previousNode, newNode, nextNode)
        )[1]
    }


    @Transactional
    override fun deleteNode(nodeId: Long): AIArticleProcessingNode {
        // Find existing
        val existingNode = aiArticleProcessingNodesRepository
            .findById(nodeId)
            .orElseThrow { IllegalArgumentException("Узел с id=${nodeId} не найден") }
        val previousNode = existingNode.parent
        val nextNode = existingNode.child

        // Lock them
        entityManager.lock(existingNode, LockModeType.PESSIMISTIC_WRITE)
        entityManager.lock(previousNode, LockModeType.PESSIMISTIC_WRITE)
        nextNode?.let { entityManager.lock(it, LockModeType.PESSIMISTIC_WRITE) }

        // Delete node
        aiArticleProcessingNodesRepository.delete(existingNode)

        // Update links
        if (previousNode != null && nextNode != null) {
            previousNode.child = nextNode
            nextNode.parent = previousNode
        } else if (nextNode != null) {
            nextNode.parent = null
        }

        aiArticleProcessingNodesRepository.saveAll(listOfNotNull(previousNode, nextNode))
        return existingNode
    }

    @Transactional
    override fun updateNode(nodeId: Long, input: AIArticleProcessingFacade.AIArticleProcessingNodeInput): AIArticleProcessingNode {
        // Find existing
        val existingNode = aiArticleProcessingNodesRepository
            .findById(nodeId)
            .orElseThrow { IllegalArgumentException("Узел с id=${nodeId} не найден") }

        // Update
        input.prompt.ifPresent { existingNode.prompt = it }
        input.model.ifPresent { existingNode.model = it }
        input.temperature.ifPresent { existingNode.temperature = it }

        aiArticleProcessingNodesRepository.save(existingNode)
        return existingNode
    }

    companion object {
        private fun createInitialRootNode(source: Source, mode: String): AIArticleProcessingNode {
            return AIArticleProcessingNode(
                source,
                mode,
                DEFAULT_MODEL,
                DEFAULT_TEMPERATURE,
                """Ты профессиональный менеджер по социальным сетям Российского новостного канала, и твоя задача - создать сокращенную версию новостной статьи, написанной на английском языке, используя исключительно информацию, представленную в статье, сохранив при этом только самую важную информацию, а также перевести текст на Русский язык.

                1. Оставь только самое основное из статьи, основные положения;
                2. Удали всю не имеющую связи со смыслом статьи информацию, такую как: данные об авторе, взаимодействие автора с аудиторией, дисклеймеры, просьбы подписаться и другое;
                3. Переведи новость на четкий и понятный широкой аудитории русский язык;
                4. Перепиши статью более привлекательно, используя вовлекающие формулировки и приемы, такие как кликбейт, например "Криптовалюте конец!";
                5. Если в статье есть прямая речь или цитаты кого-либо, кроме самих авторов статьи, оставьте их без изменений, включая пунктуацию и стиль;
                6. Убедитесь в точности изложения фактов.
                
                Твой окончательный ответ должен содержать исключительно текст сжатой новостной статьи, переписанной на Русском языке.
                """.trimIndent()
            )
        }

        private fun createInitialFormattingNode(source: Source, mode: String): AIArticleProcessingNode {
            return AIArticleProcessingNode(
                source,
                mode,
                DEFAULT_MODEL,
                DEFAULT_TEMPERATURE,
                """Ты являешься создателем контента в социальной сети и специализируешься на форматировании новостных сообщений с помощью тегов.
                Твоя цель - сделать текст более привлекательным с помощью тегов форматирования текста и эмодзи.
                1. Отформатируй предоставленный текст исключительно с помощью HTML тегов <b>, <i>, <u> и <a>, а также UNICODE эмодзи.
                2. Используйте символы, такие как "•" или "—", для структурирования контента.
                3. Убедитесь, что после каждого заголовка есть пробел.
                4. Добавляйте эмодзи в небольшом количестве, либо только в начале заголовков, либо только в следующих за заголовком абзацах.
                
                Твой окончательный ответ должен содержать исключительно отформатированный текст статьи.
                """.trimIndent()
            )
        }
    }
}