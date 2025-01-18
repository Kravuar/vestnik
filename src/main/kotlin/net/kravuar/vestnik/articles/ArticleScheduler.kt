package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.Source
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.TaskScheduler
import java.util.concurrent.ScheduledFuture
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

internal class ArticleScheduler(
    private val scheduler: TaskScheduler,
    private val articlesFacade: ArticlesFacade,
    private val sourcesFacade: SourcesFacade,
) {
    private val tasksBySource = mutableMapOf<String, ScheduledFuture<*>>()

    fun startPollingAll() {
        sourcesFacade.getSources().forEach(this::startPolling)
    }

    fun stopPollingAll() {
        tasksBySource.keys.forEach(this::stopPolling)
    }

    @Synchronized
    fun startPolling(source: Source) {
        LOG.info("Запуск polling'а для ${source.name}")
        source.run {
            tasksBySource[name]?.let {
                it.cancel(true).also {
                    LOG.info("Прекращён polling источника $source для перезапуска")
                }
            }
            tasksBySource[name] = scheduler.scheduleWithFixedDelay({
                articlesFacade.fetchAndStoreLatestNews(name, scheduleDelay)
            }, scheduleDelay)
            LOG.info("Запущен polling источника $this")
        }
    }

    fun stopPolling(source: String) {
        LOG.info("Прекращение polling'а для $source")
        tasksBySource[source]?.cancel(true).also {
            LOG.info("Прекращён polling источника $source")
        } ?: LOG.warn("Polling источника $source для прекращения не найден")
    }

    companion object {
        private val LOG = LogManager.getLogger(ArticleScheduler::class.java)
    }
}