package net.kravuar.vestnik.commons

import io.ktor.util.escapeHTML

fun String.escapeHtmlExcept(allowedTags: List<String> = listOf("b", "i", "a", "u")): String {
    val allowedTagsRegex = allowedTags.joinToString("|") { tag ->
        """<\s*/?\s*${Regex.escape(tag)}.*?>"""
    }

    val tagRegex = """<\s*/?\s*.*?>""".toRegex()

    return this.replace(tagRegex) { matchResult ->
        val tag = matchResult.value
        if (tag.matches(allowedTagsRegex.toRegex())) {
            tag
        } else {
            tag.escapeHTML()
        }
    }
}

fun <K, V> Map<K, V>.tryGet(key: K, vararg otherKeys: K): V? {
    return this[key] ?: otherKeys.firstNotNullOfOrNull { this[it] }
}