package net.kravuar.vestnik.scrapping

import net.kravuar.vestnik.scrapping.Scrapper.Companion.IRRELEVANT_ELEMENTS
import org.jsoup.nodes.Document

abstract class AbstractScrapper : Scrapper {
    protected abstract fun scrapContent(url: String): Document

    final override fun scrap(url: String, xpath: String): String {
        return scrapContent(url).let { document ->
            val element = document.selectXpath(xpath).first()
                ?: throw IllegalArgumentException("Не найден элемент на странице $url, путь $xpath")

            IRRELEVANT_ELEMENTS.forEach {
                element.getElementsByTag(it).remove()
            }

            element.text()
        }
    }
}