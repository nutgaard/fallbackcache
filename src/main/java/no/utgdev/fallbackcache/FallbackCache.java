package no.utgdev.fallbackcache;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import static no.utgdev.fallbackcache.FallbackCache.RunnableJob.KEY;
import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class FallbackCache<REQUESTTYPE, DATATYPE> {
    private final Fetcher<REQUESTTYPE, DATATYPE> fetcher;
    private final DATATYPE fallback;
    private final ForkJoinPool executorPool;
    private final ConcurrentHashMap<REQUESTTYPE, CompletableFuture<DATATYPE>> cache;
    private Scheduler scheduler;

    public FallbackCache(Fetcher<REQUESTTYPE, DATATYPE> fetcher, DATATYPE fallback) {
        this(fetcher, fallback, new Configuration(1));
    }

    public FallbackCache(Fetcher<REQUESTTYPE, DATATYPE> fetcher, DATATYPE fallback, Configuration configuration) {
        this.fetcher = fetcher;
        this.fallback = fallback;
        this.executorPool = new ForkJoinPool(1);
        this.cache = new ConcurrentHashMap<>();

        try {
            if (configuration.refreshExpression != null || configuration.recoverExpression != null) {
                this.scheduler = new StdSchedulerFactory().getScheduler();
                initializeJob("refresh", configuration.refreshExpression, this::refreshAll);
                initializeJob("recover", configuration.recoverExpression, this::fix);
                this.scheduler.start();
            }
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    public DATATYPE get(REQUESTTYPE request) {
        CompletableFuture<DATATYPE> data = cache.computeIfAbsent(request, this::getFromFetcher);

        if (data.isCompletedExceptionally()) {
            return fallback;
        }
        return data.getNow(fallback);
    }

    public void refresh(REQUESTTYPE request) {
        final CompletableFuture<DATATYPE> newData = getFromFetcher(request);
        newData.thenRun(() -> {
            cache.put(request, newData);
        });
    }

    public void refreshAll() {
        this.cache.keySet().forEach(this::refresh);
    }


    public void fix() {
        this.cache.entrySet()
                .stream()
                .filter((entry) -> entry.getValue().isCompletedExceptionally())
                .forEach((entry) -> this.refresh(entry.getKey()));
    }

    private CompletableFuture<DATATYPE> getFromFetcher(final REQUESTTYPE request) {
        CompletableFuture<DATATYPE> data = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                data.complete(fetcher.fetch(request));
            } catch (Exception e) {
                data.completeExceptionally(e);
            }
        }, executorPool);

        return data;
    }

    private void initializeJob(String name, CronExpression cron, Runnable task) throws SchedulerException {
        if (cron == null) {
            return;
        }

        JobDetail job = newJob(RunnableJob.class)
                .withIdentity(name, "fallbackcache")
                .build();

        job.getJobDataMap().put(KEY, task);

        Trigger trigger = newTrigger()
                .withIdentity(name, "fallbackcache")
                .startNow()
                .withSchedule(cronSchedule(cron))
                .build();

        this.scheduler.scheduleJob(job, trigger);
    }

    public interface Fetcher<REQUESTTYPE, DATATYPE> {
        DATATYPE fetch(REQUESTTYPE request) throws Exception;
    }

    public static class Configuration {
        public final int parallelism;
        public final CronExpression refreshExpression;
        public final CronExpression recoverExpression;

        public Configuration(int parallelism) {
            this(parallelism, null, null);
        }

        public Configuration(int parallelism, CronExpression refreshExpression, CronExpression recoverExpression) {
            this.parallelism = parallelism;
            this.refreshExpression = refreshExpression;
            this.recoverExpression = recoverExpression;
        }
    }

    public static class RunnableJob implements Job {
        static final String KEY = "runnable";

        @Override
        public void execute(JobExecutionContext context) {
            Runnable task = (Runnable) context.getJobDetail().getJobDataMap().get(KEY);
            task.run();
        }
    }
}
