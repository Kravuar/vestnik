package net.kravuar.vestnik.scrapping

import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Page
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest

internal open class SimpleScrappingFacade(
    private val scrapper: Scrapper,
    private val scrapInfoRepository: ScrapInfoRepository
): ScrappingFacade {
    override fun scrap(url: String): String {
        return scrapInfoRepository.findFirstByPattern(url).orElseThrow {
            IllegalStateException("Нет подходящей конфигурации парсинга для ссылки $url")
        }.let { scrapInfo ->
            scrapper.scrap(url, scrapInfo.contentXPath)
        }
    }

    override fun getScrapInfos(page: Int): Page<ScrapInfo> {
        return scrapInfoRepository.findAll(
            PageRequest.of(
                page - 1,
                Page.DEFAULT_PAGE_SIZE
            )
        ).let {
            Page(
                it.totalPages,
                it.content
            )
        }
    }

    @Transactional
    override fun addScrapInfo(input: ScrappingFacade.ScrapInfoInput): ScrapInfo {
        return scrapInfoRepository.save(
            ScrapInfo(
                input.urlPattern.orElseThrow { IllegalArgumentException("Шаблон для URL обязателен") },
                input.contentXPath.orElseThrow { IllegalArgumentException("XPath для страницы обязателен") }
            )
        ).also {
            LOG.info("Создана конфигурация парсинга $it")
        }
    }

    @Transactional
    override fun updateScrapInfo(id: Long, input: ScrappingFacade.ScrapInfoInput): ScrapInfo {
        return scrapInfoRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Конфигурация парсинга $id не найдена") }
            .apply {
                input.urlPattern.ifPresent { urlPattern = it }
                input.contentXPath.ifPresent { contentXPath = it }
            }.also {
                LOG.info("Обновлёна конфигурация парсинга $it")
            }
    }

    @Transactional
    override fun deleteScrapInfo(id: Long): Boolean {
        LOG.info("Удаление конфигурации парсинга $id")
        return scrapInfoRepository.findById(id).map {
            scrapInfoRepository.delete(it).also {
                LOG.info("Удалена конфигурация парсинга $id")
            }
            true
        }.orElseGet {
            LOG.info("Конфигурация парсинга $id для удаления не найдена")
            false
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(SimpleScrappingFacade::class.java)
    }
}