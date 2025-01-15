package net.kravuar.vestnik.scrapping

interface Scrapper {
    fun scrap(url: String, xpath: String): String

    companion object {
        val IRRELEVANT_ELEMENTS = setOf(
            "img",
            "script",
            "style",
            "link",
            "picture"
        )
    }
}