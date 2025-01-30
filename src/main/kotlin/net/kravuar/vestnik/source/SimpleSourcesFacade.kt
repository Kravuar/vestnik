package net.kravuar.vestnik.source

import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import com.apptasticsoftware.rssreader.util.ItemComparator
import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Page
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.ZonedDateTime

internal open class SimpleSourcesFacade(
    private val sourcesRepository: SourcesRepository
) : SourcesFacade {

    override fun fetchLatestNews(sourceName: String, delta: Duration): List<Item> {
        with(getSourceByName(sourceName)) {
            if (suspended == true) {
                LOG.info("Источник $sourceName приостановлен, fetch не будет произведён")
                return emptyList()
            }
            return RssReader()
                .read(this.url)
                .sorted(ItemComparator.oldestPublishedItemFirst())
                .filter { article -> article.pubDateZonedDateTime.map { it > ZonedDateTime.now() - delta }.orElse(false) }
                .toList().also {
                    LOG.info(
                        if (it.isNotEmpty()) {
                            "Получены новости из источника $sourceName: " + it.joinToString { article -> "${article.link} | ${article.title}" }
                        } else {
                            "Новостей из источника $sourceName не найдено"
                        }
                    )
                }
        }
    }

    override fun getSources(): List<Source> {
        return sourcesRepository.findAllByDeletedIsFalse()
    }

    override fun getSources(page: Int): Page<Source> {
        return sourcesRepository.findAllByDeletedIsFalse(
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

    override fun getAllSources(): List<Source> {
        return sourcesRepository.findAll()
    }

    override fun getAllSources(page: Int): Page<Source> {
        return sourcesRepository.findAll(
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

    override fun getSourceByName(sourceName: String): Source {
        return sourcesRepository
            .findByNameAndDeletedIsFalse(sourceName)
            .orElseThrow { IllegalArgumentException("Источник $sourceName не найден") }
    }

    @Transactional
    override fun addSource(source: SourcesFacade.SourceInput): Source {
        LOG.info("Добавление источника $source")
        return sourcesRepository.save(
            Source(
                source.name.orElseThrow { IllegalArgumentException("Имя источника обязательно") },
                source.url.orElseThrow { IllegalArgumentException("URL обязателен") },
                source.scheduleDelay.orElseThrow { IllegalArgumentException("Периодичность источника обязательна") },
            ).apply {
                source.suspended.ifPresent { this.suspended = it }
            }
        ).also {
            LOG.info(
                "Добавлен новый источник: $it"
            )
        }
    }

    @Transactional
    override fun updateSource(sourceName: String, input: SourcesFacade.SourceInput): Source {
        LOG.info("Обновление источника $sourceName: $input")
        return sourcesRepository.findByNameAndDeletedIsFalse(sourceName)
            .orElseThrow { IllegalArgumentException("Источник $sourceName не найден") }
            .apply {
                input.url.ifPresent { this.url = it }
                input.name.ifPresent { this.name = it }
                input.scheduleDelay.ifPresent { this.scheduleDelay = it }
                input.suspended.ifPresent { this.suspended = it }
            }.also {
                LOG.info("Обновлён источник $it")
            }
    }

    @Transactional
    override fun deleteSource(sourceName: String): Boolean {
        LOG.info("Удаление источника $sourceName")
        return sourcesRepository.deleteByName(sourceName).also {
            LOG.info("Удалён источник $it")
        } > 0
    }

    companion object {
        private val LOG = LogManager.getLogger(SimpleSourcesFacade::class.java)
    }
}