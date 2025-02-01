package net.kravuar.vestnik.scrapping

import net.kravuar.vestnik.scrapping.Scrapper.Companion.IRRELEVANT_ELEMENTS
import org.jsoup.nodes.Document

abstract class AbstractScrapper : Scrapper {
    protected abstract fun scrapContent(url: String): Document

    final override fun scrap(url: String, xpath: String): String {
        return scrapContent(url).let { document ->
            val elements = document.selectXpath(xpath)

            if (elements.isEmpty()) {
                throw IllegalArgumentException("Поиск контента не вернул результата на странице $url, путь $xpath")
            }

            elements.flatMap { element -> IRRELEVANT_ELEMENTS.map { element.getElementsByTag(it) } }
                .forEach {
                    it.remove()
                }

            elements.text()
        }
    }
}