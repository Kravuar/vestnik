package net.kravuar.vestnik.scrapping

import korlibs.time.millisecondsInt
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

internal class BrightDataScrapper(
    private val token: String,
    private val zone: String,
) : Scrapper {

    override fun scrap(url: String, xpath: String): String {
        try {
            LOG.info("Читаем страницу $url, xpath $xpath")

            val doc: Document = Jsoup.connect("https://api.brightdata.com/request")
                .data("zone", zone)
                .data("url", url)
                .data("format", "raw")
                .timeout(90.seconds.millisecondsInt)
                .header("Authorization", "Bearer $token")
                .post()

            val element = doc.selectXpath(xpath).first()
            return element?.text() ?: "Не найден элемент на странице $url, путь $xpath"
        } catch (e: Exception) {
            throw RuntimeException("Не удалось получить данные из страницы $url с xpath: $xpath", e)
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(BrightDataScrapper::class.java)
    }
}