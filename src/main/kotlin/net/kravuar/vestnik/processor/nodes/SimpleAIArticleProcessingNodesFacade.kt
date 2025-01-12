package net.kravuar.vestnik.processor.nodes

import com.google.common.util.concurrent.Striped
import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODEL
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_TEMPERATURE
import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest
import kotlin.concurrent.withLock

internal open class SimpleAIArticleProcessingNodesFacade(
    private val chainedAiArticleProcessingNodesRepository: ChainedAiArticleProcessingNodesRepository
) : AIArticleProcessingNodesFacade {
    private val locks = Striped.lazyWeakLock(7)

    override fun getChains(source: Source?, page: Int): Page<AIArticleProcessingNodesFacade.ChainInfo> {
        return chainedAiArticleProcessingNodesRepository
            .findRoots(
                source,
                PageRequest.of(
                    page - 1,
                    Page.DEFAULT_PAGE_SIZE
                )
            ).let {
                Page(
                    it.totalPages,
                    it.content.map { rootNode ->
                        AIArticleProcessingNodesFacade.ChainInfo(
                            rootNode.id!!,
                            rootNode.source,
                            rootNode.mode
                        )
                    }
                )
            }
    }

    override fun getChain(source: Source?, mode: String): List<ChainedAIArticleProcessingNode> {
        return generateSequence(
            chainedAiArticleProcessingNodesRepository
                .findRoot(source, mode)
                .orElseThrow { IllegalArgumentException("Цепочка для источника $source, режим $mode не найдена") }
        ) { it.child }.toList()
    }

    override fun getModes(source: Source?, page: Int): Page<String> {
        return chainedAiArticleProcessingNodesRepository
            .findModes(
                source,
                PageRequest.of(
                    page - 1,
                    Page.DEFAULT_PAGE_SIZE
                )
            )
            .let {
                Page(
                    it.totalPages,
                    it.content.map { rootNode -> rootNode.mode }
                )
            }
    }

    override fun getReprocessNode(): AIArticleProcessingNode {
        return REPROCESS_NODE
    }

    @Transactional
    override fun createChain(
        source: Source?,
        mode: String
    ): List<ChainedAIArticleProcessingNode> {
        val lock = locks.get(Pair(source?.id, mode))

        lock.withLock {
            LOG.info("Создание цепочки для источника $source, режим $mode")
            val rootNode = createInitialRootNode(source, mode)
            val formattingNode = createInitialFormattingNode(source, mode)

            rootNode.child = formattingNode
            formattingNode.parent = rootNode

            return chainedAiArticleProcessingNodesRepository.saveAll(
                listOf(rootNode, formattingNode)
            ).also {
                LOG.info("Создана цепочка для источника $it")
            }
        }
    }

    @Transactional
    override fun deleteChain(
        source: Source?,
        mode: String
    ): Boolean {
        LOG.info("Удаление цепочки для источника $source, режим $mode")

        return (chainedAiArticleProcessingNodesRepository
            .deleteAllBySourceAndMode(source, mode) > 0).also {
            LOG.info("Удалена цепочка для источника $it")
        }
    }

    @Transactional
    override fun insertNode(
        prevNodeId: Long,
        input: AIArticleProcessingNodesFacade.AIArticleProcessingNodeInput
    ): ChainedAIArticleProcessingNode {
        LOG.info("Добавление узла в цепочку обработки")

        // Find existing
        val previousNode = chainedAiArticleProcessingNodesRepository
            .findById(prevNodeId)
            .orElseThrow { IllegalArgumentException("Предшествующий узел с id=${prevNodeId} не найден") }
        val nextNode = previousNode.child
        LOG.info("Добавление узла $input, идущего за $previousNode")

        // Lock whole chain
        val lock = locks.get(Pair(previousNode.source?.id, previousNode.mode))

        lock.withLock {
            // Insert new
            val newNode = ChainedAIArticleProcessingNode(
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

            return chainedAiArticleProcessingNodesRepository.saveAll(
                listOfNotNull(previousNode, newNode, nextNode)
            )[1].also {
                LOG.info("Добавлен узел $newNode после узла $previousNode")
            }
        }
    }


    @Transactional
    override fun deleteNode(nodeId: Long): Boolean {
        LOG.info("Удаление узла $nodeId")

        // Find existing
        val existingNode = chainedAiArticleProcessingNodesRepository
            .findById(nodeId)
            .orElseThrow { IllegalArgumentException("Узел с id=${nodeId} не найден") }
        val previousNode = existingNode.parent
        val nextNode = existingNode.child
        LOG.info("Удаляемый узел $existingNode")

        // Lock whole chain
        val lock = locks.get(Pair(existingNode.source?.id, existingNode.mode))

        lock.withLock {
            // Update links
            if (previousNode != null && nextNode != null) {
                previousNode.child = nextNode
                nextNode.parent = previousNode
            } else if (nextNode != null) {
                nextNode.parent = null
            }
            chainedAiArticleProcessingNodesRepository.saveAll(listOfNotNull(previousNode, nextNode))

            // Delete node
            chainedAiArticleProcessingNodesRepository.delete(existingNode)

            return true.also {
                LOG.info(
                    "Удалён узел $existingNode${
                        if (previousNode != null) {
                            "после узла $previousNode"
                        } else {
                            ""
                        }
                    }"
                )
            }
        }
    }

    @Transactional
    override fun updateNode(
        nodeId: Long,
        input: AIArticleProcessingNodesFacade.AIArticleProcessingNodeInput
    ): ChainedAIArticleProcessingNode {
        LOG.info("Обновление узла $nodeId: $input")

        // Find existing
        val existingNode = chainedAiArticleProcessingNodesRepository
            .findById(nodeId)
            .orElseThrow { IllegalArgumentException("Узел с id=${nodeId} не найден") }
        LOG.info("Обновляемый узел: $existingNode")

        // Update
        input.prompt.ifPresent { existingNode.prompt = it }
        input.model.ifPresent { existingNode.model = it }
        input.temperature.ifPresent { existingNode.temperature = it }

        return chainedAiArticleProcessingNodesRepository.save(existingNode).also {
            LOG.info("Обновлён узел $existingNode")
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(SimpleAIArticleProcessingNodesFacade::class.java)
        private val REPROCESS_NODE = ReprocessNode()

        data class ReprocessNode(
            override var model: String = DEFAULT_MODEL,
            override var temperature: Double = DEFAULT_TEMPERATURE,
            override var prompt: String =
                """Ты высококвалифицированный редактор новостей для новостного канала в социальной сети. Твоя роль заключается в доработке новостного контента для публикации в Telegram, строго придерживаясь предоставленных замечаний и инструкций.
                |Твоя задача - отредактировать новость или ее оформление в соответствии с замечаниями и предоставить окончательный текст статьи, готовый к публикации, в качестве ответа.
                |Ответ должен содержать только окончательную версию новостного сообщения для канала.
                """.trimIndent().trimMargin()
        ) : AIArticleProcessingNode

        private fun createInitialRootNode(source: Source?, mode: String): ChainedAIArticleProcessingNode {
            return ChainedAIArticleProcessingNode(
                source,
                mode,
                DEFAULT_MODEL,
                DEFAULT_TEMPERATURE,
                """Ты профессиональный менеджер по социальным сетям Российского новостного канала, и твоя задача - создать сокращенную версию новостной статьи, написанной на английском языке, используя исключительно информацию, представленную в статье, сохранив при этом только самую важную информацию, а также перевести текст на Русский язык.

                |1. Оставь только самое основное из статьи, основные положения;
                |2. Удали всю не имеющую связи со смыслом статьи информацию, такую как: данные об авторе, взаимодействие автора с аудиторией, дисклеймеры, просьбы подписаться и другое;
                |3. Переведи новость на четкий и понятный широкой аудитории русский язык;
                |4. Перепиши статью более привлекательно, используя вовлекающие формулировки и приемы, такие как кликбейт, например "Криптовалюте конец!";
                |5. Если в статье есть прямая речь или цитаты кого-либо, кроме самих авторов статьи, оставьте их без изменений, включая пунктуацию и стиль;
                |6. Убедитесь в точности изложения фактов.
                
                |Твой окончательный ответ должен содержать исключительно текст сжатой новостной статьи, переписанной на Русском языке.
                """.trimIndent().trimMargin()
            )
        }

        private fun createInitialFormattingNode(source: Source?, mode: String): ChainedAIArticleProcessingNode {
            return ChainedAIArticleProcessingNode(
                source,
                mode,
                DEFAULT_MODEL,
                DEFAULT_TEMPERATURE,
                """Ты являешься создателем контента в социальной сети и специализируешься на форматировании новостных сообщений с помощью тегов.
                |Твоя цель - сделать текст более привлекательным с помощью тегов форматирования текста и эмодзи.

                |1. Отформатируй предоставленный текст исключительно с помощью HTML тегов <b>, <i>, <u> и <a>, а также UNICODE эмодзи.
                |2. Используйте символы, такие как "•" или "—", для структурирования контента.
                |3. Убедитесь, что после каждого заголовка есть пробел.
                |4. Добавляйте эмодзи в небольшом количестве, либо только в начале заголовков, либо только в следующих за заголовком абзацах.
                
                |Твой окончательный ответ должен содержать исключительно отформатированный текст статьи.
                """.trimIndent().trimMargin()
            )
        }
    }
}