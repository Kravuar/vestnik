package net.kravuar.vestnik.scrapping

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.apache.logging.log4j.LogManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration

internal class BrightDataScrapper(
    private val token: String,
    private val zone: String,
    private val webClient: WebClient,
): Scrapper {

    override fun scrap(url: String, xpath: String): String {
        try {
            LOG.info("Читаем страницу $url, xpath $xpath")
            val response = webClient.post()
                .uri("https://api.brightdata.com/request")
                .header("Authorization", "Bearer $token")
                .bodyValue(getRequestNode(url))
                .retrieve()
                .bodyToMono<String>()
                .timeout(Duration.ofSeconds(90))
                .block() ?: throw RuntimeException("При получении страницы '$url' был получен пустой ответ")

            val doc: Document = Jsoup.parse(response)
            val element = doc.selectFirst(xpath) ?: throw RuntimeException("На странице $url не найден элемент по xpath: $xpath")
            return element.text()
        } catch (e: Exception) {
            throw RuntimeException("Не удалось получить данные из страницы $url с xpath: $xpath", e)
        }
    }

    private fun getRequestNode(url: String): JsonNode {
        return JsonNodeFactory.instance.objectNode()
            .put("zone", zone)
            .put("url", url)
            .put("format", "raw")
    }

    companion object {
        private val LOG = LogManager.getLogger(BrightDataScrapper::class.java)
    }
}