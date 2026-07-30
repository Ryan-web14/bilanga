package com.sni.bilanga.config;

import com.sni.bilanga.config.properties.BilangaProperties;
import com.sni.bilanga.diagnosis.client.support.MlWarmupTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;

/**
 * Active l'ordonnancement — et c'est le <strong>premier</strong> du projet.
 *
 * <h2>Ce que cela change, et ce que cela ne change pas</h2>
 *
 * <p>La documentation du dépôt répète que Bilanga n'a « ni ordonnanceur, ni file de
 * messages, ni tâche de fond », et plusieurs décisions en découlent : l'outbox de
 * notification se dépêche après commit plutôt que par minuterie, le stade de croissance
 * se recalcule là où il est consommé, le retard d'une opération planifiée est calculé à la
 * lecture. <strong>Ces décisions restent valides et ne doivent pas être revues à la faveur
 * de ce fichier.</strong>
 *
 * <p>Elles ne tenaient pas au dogme mais à un principe : un état dérivé qu'on persiste
 * diverge, et un ordonnanceur qui recalcule ce qu'on peut déduire à la lecture est du
 * travail pour rien. Le réveil du microservice d'inférence est d'une autre nature — il n'y
 * a <em>rien à déduire</em>, il faut réellement émettre un appel à intervalle régulier, et
 * aucun autre mécanisme du projet ne peut le faire.
 *
 * <p><strong>Avant d'ajouter une tâche ici</strong>, vérifiez qu'elle ne peut pas se
 * calculer au moment où sa valeur est lue. Si elle le peut, elle doit l'être : c'est ce
 * qui garantit qu'elle n'est jamais périmée.
 *
 * <h2>Pourquoi l'enregistrement est programmatique</h2>
 *
 * <p>Plutôt qu'un {@code @Scheduled(fixedDelayString = "#{…}")}. Une expression ne se
 * vérifie qu'au démarrage, et une erreur d'expression <em>empêche l'application de
 * démarrer</em>. Pour une tâche de confort, dont l'échec ne coûte qu'un réveil manqué,
 * c'est un risque sans contrepartie. Ici l'intervalle est calculé en Java, validé par
 * Bean Validation, et lisible.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig implements SchedulingConfigurer {

    private final BilangaProperties.Ml ml;

    /**
     * {@code ObjectProvider} et non injection directe : la tâche porte un
     * {@code @ConditionalOnProperty} et peut donc ne pas exister. L'exiger ferait échouer
     * le démarrage de tout le contexte au seul motif qu'un réveil est désactivé.
     */
    private final ObjectProvider<MlWarmupTask> warmup;

    /**
     * Un ordonnanceur dédié, et non celui par défaut.
     *
     * <p>Sans bean explicite, Spring emploie un exécuteur à <strong>un seul fil</strong> :
     * une tâche lente y bloquerait toutes les autres. Le réveil de l'inférence attend
     * jusqu'à soixante secondes qu'un service endormi réponde — c'est exactement le profil
     * de tâche qui monopoliserait ce fil unique.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("bilanga-sched-");

        // Ne pas retarder l'arrêt pour une tâche de confort : sur une plateforme qui
        // recycle les conteneurs, un arrêt qui traîne est tué de force.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(taskScheduler());

        MlWarmupTask task = warmup.getIfAvailable();
        if (task == null) {
            log.info("Réveil du microservice d'inférence désactivé "
                    + "(bilanga.ml.warmup.enabled=false). Le premier diagnostic après une "
                    + "mise en veille expirera probablement.");
            return;
        }

        Duration interval = Duration.ofMinutes(ml.getWarmup().getIntervalMinutes());

        // Délai initial court : le premier réveil a lieu au démarrage, sans attendre un
        // intervalle entier. C'est le cas qui compte le plus — au redéploiement, le
        // service d'inférence dort presque toujours, et le premier appel métier arrive
        // dans les secondes qui suivent.
        //
        // fixedDelay et non fixedRate : l'intervalle court à partir de la FIN de l'appel
        // précédent. Avec fixedRate, un réveil de soixante secondes sur un service
        // endormi chevaucherait le suivant.
        registrar.addFixedDelayTask(new org.springframework.scheduling.config.FixedDelayTask(
                task::keepAwake, interval, Duration.ofSeconds(15)));

        log.info("Réveil du microservice d'inférence actif : {} toutes les {} min "
                        + "(premier appel dans 15 s).",
                ml.getBaseUrl() + ml.getWarmup().getPath(), interval.toMinutes());
    }
}
