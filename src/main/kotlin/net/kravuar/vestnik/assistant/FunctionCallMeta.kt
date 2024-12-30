package net.kravuar.vestnik.assistant

import java.util.function.Function

data class FunctionCallMeta<T>(
    val name: String,
    val usageInstruction: String,
    val description: String,
    val inputType: Class<T>,
    val function: Function<T, Any>
)