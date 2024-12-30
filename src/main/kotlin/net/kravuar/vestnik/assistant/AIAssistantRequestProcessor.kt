package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODEL
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_TEMPERATURE
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.openai.OpenAiChatOptions

internal class AIAssistantRequestProcessor(
    private val chatModel: ChatModel,
    functions: List<FunctionCallMeta<*>>,
) : AssistantRequestProcessor {
    private val instructions = functions.joinToString("\n") {
        "- ${it.name}, ${it.usageInstruction}"
    }
    private val functionCallbacks: List<FunctionCallback> = functions.map { FunctionCallback.builder()
        .description(it.description)
        .function(it.name, it.function)
        .inputType(it.inputType)
        .build()
    }

    override fun process(request: String): String {
        return chatModel.call(
            Prompt(
                listOf<Message>(
                    SystemMessage(SYSTEM_PROMPT.replace(
                        "{{instructions}}", instructions
                    )),
                    UserMessage(request)
                ),
                OpenAiChatOptions.builder()
                    // No parallel, so it completes the task one step at a time sequentially.
                    .withParallelToolCalls(false)
                    .withFunctionCallbacks(functionCallbacks)
                    .withModel(DEFAULT_MODEL)
                    .withTemperature(DEFAULT_TEMPERATURE)
                    .build()
            )
        ).result.output.content
    }

    companion object {
        const val SYSTEM_PROMPT = """Ты ассистент по имени Вестник, который получает пользовательский запрос и выполняет необходимые для его удовлетворения действия через function-calls. 
        Следуй данным инструкциям:
        
        1. На основе запроса пользователя определи, какие действия (функции) необходимо выполнить.
        2. Выполняйте действия строго в соответствии с порядком в запросе пользователя.
        3. Если выполнение любого действия возвращает ошибку, немедленно закончи работу и сообщи об ошибке пользователю.
        4. Используй только предусмотренные вызовы функций.
        5. После успешного выполнения всех действий создай краткую сводку с результатами. 
        6. Если все действия выполнены успешно, предоставьте пользователю краткую сводку о том, что было сделано, и основные данные результата каждого действия, формат следующий:
            - [Действие 1]: результат
            - [Действие 2]: результат
           Результаты отображай в человеко-читаемом формате, без json формата.
            
        Инструкции для всех существующих функций:
        {{instructions}}
        """
    }
}