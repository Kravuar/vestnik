package net.kravuar.vestnik.commons

import io.ktor.util.escapeHTML

fun String.escapeHtmlExcept(allowedTags: List<String> = listOf("b", "i", "a", "u")): String {
    val allowedTagsRegex = allowedTags.joinToString("|") { tag ->
        """<\s*/?\s*${Regex.escape(tag)}.*?>"""
    }

    val tagRegex = """<[^>]+>""".toRegex()

    return this.replace(tagRegex) { matchResult ->
        val tag = matchResult.value
        if (tag.matches(allowedTagsRegex.toRegex())) {
            tag
        } else {
            tag.escapeHTML()
        }
    }
}