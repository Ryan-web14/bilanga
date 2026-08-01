package com.sni.bilanga.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Rend l'exécution asynchrone réellement asynchrone.
 *
 * <p><strong>Le défaut corrigé.</strong> {@code AuditServiceImpl.save} porte
 * {@code @Async} depuis l'origine, mais {@code @EnableAsync} n'était déclaré
 * nulle part. Spring ignore alors l'annotation sans le dire : chaque écriture
 * d'audit s'exécutait <em>dans</em> la transaction de l'appelant, et ralentissait
 * l'opération administrative qu'elle ne faisait que journaliser.
 *
 * <p>Le pire n'est pas la lenteur, c'est la fausse lecture : quelqu'un lisant le
 * code concluait que l'audit ne coûtait rien au chemin critique. Une annotation
 * qui n'a aucun effet est plus trompeuse que son absence.
 *
 * <h2>Trois décisions à connaître</h2>
 *
 * <p><strong>Le contexte de sécurité est propagé.</strong> Passer réellement sur
 * un autre fil fait perdre le {@code SecurityContext}, qui est porté par un
 * {@code ThreadLocal}. Aujourd'hui {@code AspectAudit} résout l'acteur
 * <em>avant</em> d'appeler {@code save} — l'entité arrive complète, et rien n'est
 * relu côté asynchrone. Mais compter là-dessus rendrait fragile toute tâche
 * asynchrone future, qui perdrait son acteur silencieusement.
 * {@link DelegatingSecurityContextAsyncTaskExecutor} ferme la question une fois
 * pour toutes.
 *
 * <p><strong>Saturation ⇒ exécution par l'appelant</strong>
 * ({@link ThreadPoolExecutor.CallerRunsPolicy}). Le comportement par défaut
 * lève une {@code RejectedExecutionException} quand la file est pleine : une
 * rafale d'écritures administratives ferait alors échouer des requêtes
 * <em>abouties</em>, pour un défaut de journalisation. Retomber en synchrone est
 * le repli juste — on perd le bénéfice de performance, jamais la trace.
 *
 * <p><strong>Une exception asynchrone ne remonte à personne.</strong> Sur une
 * méthode {@code void}, l'appelant est déjà parti. Sans gestionnaire, l'échec
 * disparaît : un audit qui cesse d'être écrit ne se remarquerait qu'au moment où
 * l'on chercherait une trace absente.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Volontairement modeste : l'audit est la seule charge asynchrone du projet,
     * et elle consiste en un unique {@code INSERT}. Un pool large ne ferait que
     * multiplier les connexions prises au pool Hikari — dimensionné à 10.
     */
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 500;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("bilanga-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Laisse les écritures en file se terminer à l'arrêt : sinon un
        // redéploiement perdrait les dernières traces, précisément celles qui
        // documentent ce qui a été fait juste avant.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();

        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("Échec d'une tâche asynchrone {} : la trace correspondante n'a pas été "
                    + "écrite. L'opération métier elle-même a abouti.",
                    method.getName(), throwable);
            new SimpleAsyncUncaughtExceptionHandler()
                    .handleUncaughtException(throwable, method, params);
        };
    }
}
