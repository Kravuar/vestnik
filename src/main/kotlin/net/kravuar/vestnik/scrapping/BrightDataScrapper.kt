package net.kravuar.vestnik.scrapping

import korlibs.time.millisecondsInt
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

internal class BrightDataScrapper(
    private val token: String,
    private val zone: String,
) : AbstractScrapper() {

    override fun scrapContent(url: String): Document {
        try {
            LOG.info("Читаем страницу $url")

            return Jsoup.connect("https://api.brightdata.com/request")
                .data("zone", zone)
                .data("url", url)
                .data("format", "raw")
                .ignoreContentType(true)
                .timeout(90.seconds.millisecondsInt)
                .header("Authorization", "Bearer $token")
                .post()
        } catch (e: Throwable) {
            throw RuntimeException("Не удалось получить данные из страницы $url", e)
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(BrightDataScrapper::class.java)
    }
}