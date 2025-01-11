package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.Source
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import org.springframework.scheduling.TaskScheduler
import java.util.concurrent.ScheduledFuture

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
        source.run {
            tasksBySource[name]?.cancel(true)
            tasksBySource[name] = scheduler.scheduleWithFixedDelay({
                articlesFacade.fetchAndStoreLatestNews(name, scheduleDelay)
            }, scheduleDelay)
            LOG.info("Запущен polling источника $source")
        }
    }

    fun stopPolling(source: String) {
        tasksBySource[source]?.cancel(true).also {
            LOG.info("Прекращён polling источника $source")
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(ArticleScheduler::class.java)
    }
}