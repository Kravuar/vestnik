package net.kravuar.vestnik.processor

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.kravuar.vestnik.assistant.FunctionCallMeta
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODE
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODEL
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_TEMPERATURE
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class FunctionsConfig(
    private val aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade,
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonClassDescription("Данные создаваемой цепочки обработки статьи")
    data class CreateSequenceInput(
        @JsonPropertyDescription("Имя источника статьи")
        val source: String,
        @JsonPropertyDescription("Режим обработки статьи")
        val mode: String = DEFAULT_MODE
    )
    @Bean
    fun createSequence(): FunctionCallMeta<CreateSequenceInput> = FunctionCallMeta(
        "createSequence",
        "Если нужно создать новую цепочку обработки статьи",
        "Добавление нового узла обработки статьи в цепочку",
        CreateSequenceInput::class.java,
    ) {
        try {
            val node = aiArticleProcessingNodesFacade.createSequence(it.source, it.mode)
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("id", node.id)
                put("source", node.source.name)
                put("mode", node.mode)
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при создании цепочки обработки: " + e.message)
            }
        }
    }

    @JsonClassDescription("Данные добавляемого в узла обработки статьи ")
    data class InsertNodeInput(
        @JsonPropertyDescription("ID Предшествующего узла цепочки")
        val prevNodeId: Long,
        @JsonPropertyDescription("Промпт AI модели для обработки статьи")
        val prompt: String,
        @JsonPropertyDescription("Имя AI модели")
        val model: String = DEFAULT_MODEL,
        @JsonPropertyDescription("Температура AI модели")
        val temperature: Double = DEFAULT_TEMPERATURE,
    )
    @Bean
    fun insertNode(): FunctionCallMeta<InsertNodeInput> = FunctionCallMeta(
        "insertNode",
        "Если нужно добавить новый узел обработки статьи в цепочку",
        "Добавление нового узла обработки статьи в цепочку",
        InsertNodeInput::class.java,
    ) {
        try {
            val node = aiArticleProcessingNodesFacade.insertNode(it.prevNodeId, it.prompt, it.model, it.temperature)
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("id", node.id)
                put("source", node.source.name)
                put("mode", node.mode)
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при добавлении узла обработки: " + e.message)
            }
        }
    }

    @JsonClassDescription("Данные удаляемого узла обработки статьи")
    data class DeleteNodeInput(
        @JsonPropertyDescription("ID удаляемого узла цепочки")
        val nodeId: Long,
    )
    @Bean
    fun deleteNode(): FunctionCallMeta<DeleteNodeInput> = FunctionCallMeta(
        "deleteNode",
        "Если нужно удалить узел обработки статьи из цепочки",
        "Удаление узла обработки статьи из цепочки",
        DeleteNodeInput::class.java,
    ) {
        try {
            aiArticleProcessingNodesFacade.deleteNode(it.nodeId)
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("successfullyDeleted", true)
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при удалении узла обработки: " + e.message)
            }
        }
    }

    @Bean
    fun getAllSequences(): FunctionCallMeta<Void> = FunctionCallMeta(
        "getAllSequences",
        "Если нужно получить основную информацию о существующих цепочках обработки статьи",
        "Получение основной информация о всех цепочках обработки статьи",
        Void::class.java,
    ) {
       try {
           val sequences = aiArticleProcessingNodesFacade.getSequences()
           return@FunctionCallMeta JsonNodeFactory.instance.arrayNode().apply {
               sequences.forEach {
                   add(JsonNodeFactory.instance.objectNode().apply {
                       put("id", it.id)
                       put("source", it.source)
                       put("mode", it.mode)
                   })
               }
           }
       } catch (e: Exception) {
           return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
               put("error", "Ошибка при получении информации о существующих цепочках: " + e.message)
           }
       }
    }

    @JsonClassDescription("Данные для получения информации о цепочке обработки статьи")
    data class GetSequenceInput(
        @JsonPropertyDescription("Имя источника статьи")
        val source: String,
        @JsonPropertyDescription("Режим обработки статьи")
        val mode: String = DEFAULT_MODE,
    )
    @Bean
    fun getSequence(): FunctionCallMeta<GetSequenceInput> = FunctionCallMeta(
        "getSequence",
        "Если нужно получить информацию о конкретной цепочке обработки статьи",
        "Получение информация о цепочке обработки статьи",
        GetSequenceInput::class.java,
    ) {
        try {
            val nodes = aiArticleProcessingNodesFacade.getSequence(it.source, it.mode)
            return@FunctionCallMeta JsonNodeFactory.instance.arrayNode().apply {
                nodes.forEach {
                    add(JsonNodeFactory.instance.objectNode().apply {
                        put("id", it.id)
                        put("source", it.source.name)
                        put("mode", it.mode)
                        put("prompt", it.prompt)
                        put("model", it.model)
                        put("temperature", it.temperature)
                    })
                }
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при получении информации о цепочке с source=${it.source}, mode=${it.mode}: " + e.message)
            }
        }
    }

    @JsonClassDescription("Данные для получения списка режимов для конкретного источника")
    data class GetModesInput(
        @JsonPropertyDescription("Имя источника статьи")
        val source: String,
    )
    @Bean
    fun getModes(): FunctionCallMeta<GetModesInput> = FunctionCallMeta(
        "getModes",
        "Если нужно получить список режимов для конкретного источника",
        "Получение списка существующих режимов для конкретного источника",
        GetModesInput::class.java,
    ) {
        try {
            val modes = aiArticleProcessingNodesFacade.getModes(it.source)
            return@FunctionCallMeta JsonNodeFactory.instance.arrayNode().apply {
                modes.forEach { mode ->
                    add(mode)
                }
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при получении списка режимов для источника ${it.source}: " + e.message)
            }
        }
    }


    @JsonClassDescription("Данные для обновления узла обработки статьи")
    data class UpdateNodeInput(
        @JsonPropertyDescription("ID узла обработки статьи")
        val nodeId: Long,
        @JsonPropertyDescription("Промпт AI модели для обработки статьи")
        val prompt: String?,
        @JsonPropertyDescription("Имя AI модели")
        val model: String?,
        @JsonPropertyDescription("Температура AI модели")
        val temperature: Double?,
    )
    @Bean
    fun updateNode(): FunctionCallMeta<UpdateNodeInput> = FunctionCallMeta(
        "updateNode",
        "Если нужно обновить узел обработки статьи",
        "Обновление узла обработки статьи",
        UpdateNodeInput::class.java,
    ) {
        try {
            val node = aiArticleProcessingNodesFacade.updateNode(it.nodeId, it.prompt, it.model, it.temperature)
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("id", node.id)
                put("successfullyUpdated", true)
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при обновлении узла обработки: " + e.message)
            }
        }
    }
}