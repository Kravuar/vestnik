package net.kravuar.vestnik.scrapping

import korlibs.time.millisecondsInt
import org.apache.logging.log4j.LogManager
import org.jsoup.Connection
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

            val response = Jsoup.connect("https://api.brightdata.com/request")
                .data("zone", zone)
                .data("url", url)
                .data("format", "raw")
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .timeout(90.seconds.millisecondsInt)
                .header("Authorization", "Bearer $token")
                .method(Connection.Method.POST)
                .execute()

            if (response.statusCode() !in 200..299) {
                throw RuntimeException("""
                |Запрос провалился.
                |Статус ответа: ${response.statusCode()}
                |Тело: ${response.body()}
                """.trimIndent().trimMargin())
            }

            return response.parse()
        } catch (e: Throwable) {
            throw RuntimeException("Не удалось получить данные из страницы $url: ${e.message}", e)
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(BrightDataScrapper::class.java)
    }
}