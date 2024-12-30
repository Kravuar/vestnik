package net.kravuar.vestnik.assistant

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class FunctionsConfig(
    private val functions: List<FunctionCallMeta<*>>
) {

    @Bean
    fun showCommands(): FunctionCallMeta<Void> = FunctionCallMeta(
        "showCommands",
        "Если нужно показать список имеющихся команд/функций",
        "Показывает список имеющихся команд/функций",
        Void::class.java,
    ) {
        try {
            return@FunctionCallMeta JsonNodeFactory.instance.arrayNode().apply {
                functions.forEach {
                    add(JsonNodeFactory.instance.objectNode().apply {
                        put("name", it.name)
                        put("description", it.description)
                    })
                }
            }
        } catch (e: Exception) {
            return@FunctionCallMeta JsonNodeFactory.instance.objectNode().apply {
                put("error", "Ошибка при создании цепочки обработки: " + e.message)
            }
        }
    }
}