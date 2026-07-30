package com.sni.bilanga.diagnosis.service.implementation;


import com.sni.bilanga.diagnosis.dto.response.DiagnosisReplay;
import com.sni.bilanga.diagnosis.dto.response.PointInTimeView;
import com.sni.bilanga.diagnosis.service.support.PointInTimeAssembler;
import com.sni.bilanga.diagnosis.service.support.PointInTimeResolver;
import com.sni.bilanga.farm.model.Crop;
import com.sni.bilanga.farm.model.Plot;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.farm.service.interfaces.PlotService;
import com.sni.bilanga.diagnosis.service.support.DiagnosisReplayer;
import com.sni.bilanga.diagnosis.client.dto.response.SoilPrediction;
import com.sni.bilanga.diagnosis.client.dto.response.VisionPrediction;
import com.sni.bilanga.diagnosis.client.interfaces.TabularClient;
import com.sni.bilanga.diagnosis.client.interfaces.VisionClient;
import com.sni.bilanga.diagnosis.dto.response.*;
import com.sni.bilanga.diagnosis.model.AiModel;
import com.sni.bilanga.diagnosis.model.Diagnostic;
import com.sni.bilanga.diagnosis.model.Recommendation;
import com.sni.bilanga.diagnosis.repository.AiModelRepository;
import com.sni.bilanga.diagnosis.repository.DiagnosticRepository;
import com.sni.bilanga.diagnosis.repository.RecommendationRepository;
import com.sni.bilanga.diagnosis.service.interfaces.AlertService;
import com.sni.bilanga.diagnosis.service.interfaces.DiagnosisService;
import com.sni.bilanga.diagnosis.service.support.ComparativeExplainer;
import com.sni.bilanga.diagnosis.service.support.ConfidenceEvaluator;
import com.sni.bilanga.diagnosis.service.support.ContextResolver;
import com.sni.bilanga.diagnosis.service.support.DiagnosisExplainer;
import com.sni.bilanga.diagnosis.service.support.DiagnosisMapper;
import com.sni.bilanga.enums.RecommendationPriority;
import com.sni.bilanga.enums.RecommendationType;
import com.sni.bilanga.enums.DiagnosticSource;
import com.sni.bilanga.enums.DomainEnums;
import com.sni.bilanga.enums.SensorHealth;
import com.sni.bilanga.exception.customs.ResourceNotFoundException;
import com.sni.bilanga.iot.model.SensorReading;
import com.sni.bilanga.knowledge.dto.response.DiseaseRisk;
import com.sni.bilanga.knowledge.dto.response.RecommendationItem;
import com.sni.bilanga.knowledge.dto.response.TrendFinding;
import com.sni.bilanga.knowledge.service.interfaces.KnowledgeService;
import com.sni.bilanga.utils.format.TimeRange;
import com.sni.bilanga.enums.Culture;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DiagnosisServiceImpl implements DiagnosisService {

    private static final int MAX_ALTERNATIVES = 3;

    /** Score de risque à partir duquel les mesures corroborent un diagnostic image. */
    private static final double CORROBORATION_THRESHOLD = 0.60;

    /** En deçà, les conditions mesurées contredisent la progression de la maladie. */
    private static final double DIVERGENCE_THRESHOLD = 0.20;

    private final AiModelRepository aiModelRepository;
    private final DiagnosticRepository diagnosticRepository;
    private final RecommendationRepository recommendationRepository;
    private final KnowledgeService knowledgeService;
    private final VisionClient visionClient;
    private final TabularClient tabularClient;
    private final ContextResolver contextResolver;
    private final ConfidenceEvaluator confidenceEvaluator;
    private final DiagnosisMapper diagnosisMapper;
    private final DiagnosisExplainer diagnosisExplainer;
    private final ComparativeExplainer comparativeExplainer;
    private final DiagnosisReplayer diagnosisReplayer;
    private final PointInTimeResolver pointInTimeResolver;
    private final PointInTimeAssembler pointInTimeAssembler;
    private final PlotService plotService;
    private final CropService cropService;
    private final AlertService alertService;

    // ============================================================
    // Chaîne image
    // ============================================================
    @Override
    @Transactional
    public DiagnosisResult diagnoseFromImage(Long plotId, String cropName,
                                             MultipartFile image, Long readingId) {
        DiagnosisContext ctx = contextResolver.resolve(plotId, cropName, readingId, false);

        VisionPrediction pred = visionClient.predict(ctx.getCropName(), image);
        String code = knowledgeService.normalizeDiseaseCode(pred.getDiseaseClass());

        List<ClassProbability> alternatives =
                confidenceEvaluator.topClasses(pred.getAllProbabilities(), MAX_ALTERNATIVES);

        Diagnostic diagnostic = persistDiagnostic(ctx, "IMAGE", code,
                pred.getConfidence(), visionModel(ctx.getCropName()), null);

        List<DiseaseRisk> risks = otherRisks(ctx, code);

        List<TrendFinding> trends = knowledgeService.assessTrends(ctx.getCropName(), ctx.getGrowthStage(), plotId);

        List<RecommendationItem> items = new ArrayList<>(knowledgeService
                .recommendForDisease(pred.getDiseaseClass(), ctx.getCropName(), ctx.getReading()));
        items.addAll(knowledgeService.analyzeAgronomic(ctx.getCropName(), ctx.getGrowthStage(), ctx.getReading()));
        items.addAll(knowledgeService.recommendForRisks(risks));
        items.addAll(knowledgeService.recommendForTrends(trends));
        // Sixième moteur : le seul qui regarde devant à partir d'une source
        // externe. Silencieux si la parcelle n'est pas géolocalisée ou si le
        // fournisseur ne répond pas — le système reste utilisable sans météo.
        items.addAll(knowledgeService.assessWeather(ctx.getPlot()));

        // Huitième moteur (V27) : le seul dont l'information ne peut venir
        // d'AUCUNE mesure locale. Une sonde parfaite ne dira jamais qu'un mildiou
        // progresse à huit cents mètres.
        //
        // Les risques LOCAUX lui sont transmis pour qu'il se taise sur les
        // maladies déjà signalées ici : deux conseils pour un même problème font
        // douter du système, pas de la maladie.
        items.addAll(knowledgeService.assessNeighbourhood(ctx.getPlot(), risks));

        return build(diagnostic, ctx, items, alternatives, trends,
                confidenceEvaluator.advisoryForImage(pred.getConfidence(), alternatives),
                corroborationFor(ctx, code),
                risks);
    }

    // ============================================================
    // Chaîne capteurs
    // ============================================================
    @Override
    @Transactional
    public DiagnosisResult diagnoseFromSensorReading(Long plotId, String cropName, Long readingId) {
        DiagnosisContext ctx = contextResolver.resolve(plotId, cropName, readingId, true);

        SoilPrediction pred = tabularClient.predict(toFeatureMap(ctx));

        Diagnostic diagnostic = persistDiagnostic(ctx, "CAPTEUR",
                pred.getCategory(), pred.getConfidence(),
                aiModelRepository.findFirstByModelType("TABULAR").orElse(null), null);

        List<DiseaseRisk> risks = knowledgeService.assessRisks(ctx.getCropName(), ctx.getReading());

        List<TrendFinding> trends = knowledgeService.assessTrends(ctx.getCropName(), ctx.getGrowthStage(), plotId);

        List<RecommendationItem> items = new ArrayList<>(knowledgeService
                .recommendForSensorDiagnostic(pred.getCategory(), ctx.getCropName()));
        items.addAll(knowledgeService.analyzeAgronomic(ctx.getCropName(), ctx.getGrowthStage(), ctx.getReading()));
        items.addAll(knowledgeService.recommendForRisks(risks));
        items.addAll(knowledgeService.recommendForTrends(trends));
        // Sixième moteur : le seul qui regarde devant à partir d'une source
        // externe. Silencieux si la parcelle n'est pas géolocalisée ou si le
        // fournisseur ne répond pas — le système reste utilisable sans météo.
        items.addAll(knowledgeService.assessWeather(ctx.getPlot()));

        // Huitième moteur (V27) : le seul dont l'information ne peut venir
        // d'AUCUNE mesure locale. Une sonde parfaite ne dira jamais qu'un mildiou
        // progresse à huit cents mètres.
        //
        // Les risques LOCAUX lui sont transmis pour qu'il se taise sur les
        // maladies déjà signalées ici : deux conseils pour un même problème font
        // douter du système, pas de la maladie.
        items.addAll(knowledgeService.assessNeighbourhood(ctx.getPlot(), risks));

        return build(diagnostic, ctx, items, List.of(), trends,
                confidenceEvaluator.advisoryForSensor(pred.getConfidence()),
                null, risks);
    }

    // ============================================================
    // Diagnostics fournis (aucun modèle n'est appelé)
    // ============================================================
    @Override
    @Transactional
    public DiagnosisResult processImageDiagnosis(Long plotId, String cropName,
                                                 String rawDiseaseClass, Double confidence,
                                                 Long readingId, String imageUrl) {
        DiagnosisContext ctx = contextResolver.resolve(plotId, cropName, readingId, false);
        String code = knowledgeService.normalizeDiseaseCode(rawDiseaseClass);

        Diagnostic diagnostic = persistDiagnostic(ctx, "IMAGE", code,
                confidence, visionModel(ctx.getCropName()), imageUrl);

        List<DiseaseRisk> risks = otherRisks(ctx, code);

        List<TrendFinding> trends = knowledgeService.assessTrends(ctx.getCropName(), ctx.getGrowthStage(), plotId);

        List<RecommendationItem> items = new ArrayList<>(knowledgeService
                .recommendForDisease(rawDiseaseClass, ctx.getCropName(), ctx.getReading()));
        items.addAll(knowledgeService.analyzeAgronomic(ctx.getCropName(), ctx.getGrowthStage(), ctx.getReading()));
        items.addAll(knowledgeService.recommendForRisks(risks));
        items.addAll(knowledgeService.recommendForTrends(trends));
        // Sixième moteur : le seul qui regarde devant à partir d'une source
        // externe. Silencieux si la parcelle n'est pas géolocalisée ou si le
        // fournisseur ne répond pas — le système reste utilisable sans météo.
        items.addAll(knowledgeService.assessWeather(ctx.getPlot()));

        // Huitième moteur (V27) : le seul dont l'information ne peut venir
        // d'AUCUNE mesure locale. Une sonde parfaite ne dira jamais qu'un mildiou
        // progresse à huit cents mètres.
        //
        // Les risques LOCAUX lui sont transmis pour qu'il se taise sur les
        // maladies déjà signalées ici : deux conseils pour un même problème font
        // douter du système, pas de la maladie.
        items.addAll(knowledgeService.assessNeighbourhood(ctx.getPlot(), risks));

        return build(diagnostic, ctx, items, List.of(), trends,
                confidenceEvaluator.advisoryForImage(confidence, List.of()),
                corroborationFor(ctx, code),
                risks);
    }

    @Override
    @Transactional
    public DiagnosisResult processSensorDiagnosis(Long plotId, String cropName,
                                                  String category, Double confidence,
                                                  Long readingId) {
        DiagnosisContext ctx = contextResolver.resolve(plotId, cropName, readingId, false);

        Diagnostic diagnostic = persistDiagnostic(ctx, "CAPTEUR", category, confidence,
                aiModelRepository.findFirstByModelType("TABULAR").orElse(null), null);

        List<DiseaseRisk> risks = knowledgeService.assessRisks(ctx.getCropName(), ctx.getReading());

        List<TrendFinding> trends = knowledgeService.assessTrends(ctx.getCropName(), ctx.getGrowthStage(), plotId);

        List<RecommendationItem> items = new ArrayList<>(
                knowledgeService.recommendForSensorDiagnostic(category, ctx.getCropName()));
        items.addAll(knowledgeService.analyzeAgronomic(ctx.getCropName(), ctx.getGrowthStage(), ctx.getReading()));
        items.addAll(knowledgeService.recommendForRisks(risks));
        items.addAll(knowledgeService.recommendForTrends(trends));
        // Sixième moteur : le seul qui regarde devant à partir d'une source
        // externe. Silencieux si la parcelle n'est pas géolocalisée ou si le
        // fournisseur ne répond pas — le système reste utilisable sans météo.
        items.addAll(knowledgeService.assessWeather(ctx.getPlot()));

        // Huitième moteur (V27) : le seul dont l'information ne peut venir
        // d'AUCUNE mesure locale. Une sonde parfaite ne dira jamais qu'un mildiou
        // progresse à huit cents mètres.
        //
        // Les risques LOCAUX lui sont transmis pour qu'il se taise sur les
        // maladies déjà signalées ici : deux conseils pour un même problème font
        // douter du système, pas de la maladie.
        items.addAll(knowledgeService.assessNeighbourhood(ctx.getPlot(), risks));

        return build(diagnostic, ctx, items, List.of(), trends,
                confidenceEvaluator.advisoryForSensor(confidence), null, risks);
    }

    // ============================================================
    // Historique
    // ============================================================
    @Override
    @Transactional(readOnly = true)
    public DiagnosticHistoryResponse findById(Long id) {
        Diagnostic diagnostic = diagnosticRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic introuvable : " + id));

        return diagnosisMapper.toHistory(diagnostic,
                recommendationRepository.findByDiagnostic_Id(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiagnosticHistoryResponse> findByPlot(Long plotId, int limit) {
        return diagnosticRepository
                .findByPlot_IdOrderByDiagnosedAtDesc(plotId, PageRequest.of(0, limit))
                .stream()
                .map(d -> diagnosisMapper.toHistory(d,
                        recommendationRepository.findByDiagnostic_Id(d.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DiagnosticHistoryResponse> search(Long plotId, DiagnosticSource source, String result,
                                                  Double minConfidence, Instant from, Instant to,
                                                  Pageable pageable) {
        return diagnosticRepository
                .search(plotId, DomainEnums.nameOf(source),
                        result == null || result.isBlank()
                                ? null
                                : result.trim().toUpperCase(Locale.ROOT),
                        minConfidence, TimeRange.from(from), TimeRange.to(to), pageable)
                .map(d -> diagnosisMapper.toHistory(d,
                        recommendationRepository.findByDiagnostic_Id(d.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public DiagnosisExplanation explain(Long id) {
        Diagnostic diagnostic = diagnosticRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic introuvable : " + id));

        return diagnosisExplainer.explain(diagnostic,
                recommendationRepository.findByDiagnostic_Id(id));
    }

    /**
     * Rejoue un diagnostic passé avec les seuils <strong>actuels</strong>.
     *
     * <p>La base de connaissance est pilotable par API, mais rien ne disait à
     * l'agronome <em>ce que change</em> un ajustement de seuil : il modifiait, puis
     * attendait le prochain relevé — sur des conditions différentes de celles qui
     * l'avaient interrogé, donc sans réponse à sa question. Le rejeu rend la
     * connaissance expérimentable sur des données déjà en base.
     *
     * <p><strong>{@code readOnly = true}, et ce n'est pas seulement une
     * optimisation.</strong> C'est la garantie qu'un rejeu ne peut rien écrire :
     * ni diagnostic, ni recommandation, ni alerte. Une simulation qui laisserait des
     * traces polluerait l'historique de la parcelle avec des situations qui n'ont pas
     * eu lieu, puis les compterait dans le taux de suivi des conseils — faussant
     * précisément l'indicateur qui sert à réviser les règles.
     */
    @Override
    @Transactional(readOnly = true)
    public DiagnosisReplay replay(Long id) {
        Diagnostic diagnostic = diagnosticRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnostic introuvable : " + id));

        return diagnosisReplayer.replay(diagnostic,
                recommendationRepository.findByDiagnostic_Id(id));
    }

    /**
     * Ce que le système voyait, avait conclu, et conclurait aujourd'hui — à un
     * instant choisi.
     *
     * <p>Comble le chaînon manquant de l'historique : {@code /plots/{id}/history} rend
     * des intervalles agrégés dont aucun point ne porte d'identifiant de relevé ni de
     * diagnostic. Cliquer sur un creux d'humidité ne menait nulle part.
     *
     * <p>La culture est résolue par la parcelle, sauf si l'appelant l'impose. C'est
     * une approximation assumée : la culture <em>en cours aujourd'hui</em> peut ne pas
     * être celle qui poussait à l'instant demandé, et l'assembleur le signale dans sa
     * réserve. La corriger demanderait un historique de succession — c'est le lot
     * suivant.
     *
     * <p>{@code readOnly = true} : rien n'est écrit, et la garantie est structurelle.
     */
    @Override
    @Transactional(readOnly = true)
    public PointInTimeView diagnosisAt(Long plotId, Instant at, String cropName,
                                       Integer toleranceMinutes) {

        // Passe par le service : c'est lui qui porte le contrôle d'accès par
        // AccessGuard, comme toutes les autres lectures de parcelle.
        Plot plot = plotService.require(plotId);

        PointInTimeResolver.ReadingChoice readingPick =
                pointInTimeResolver.resolveReading(plotId, at, toleranceMinutes);

        PointInTimeResolver.DiagnosticChoice diagPick =
                pointInTimeResolver.resolveDiagnostic(plotId, at, readingPick.reading());

        List<Recommendation> persisted = diagPick.isPresent()
                ? recommendationRepository.findByDiagnostic_Id(diagPick.diagnostic().getId())
                : List.of();

        // La culture d'époque prime sur celle d'aujourd'hui quand un diagnostic
        // existe : c'est la seule trace fiable de ce qui poussait alors.
        String crop = cropName != null ? cropName
                : diagPick.isPresent() ? diagPick.diagnostic().getCropName()
                : cropService.findActiveCrop(plotId).map(Crop::getCropName).orElse(null);

        return pointInTimeAssembler.assemble(plot, crop, at, readingPick, diagPick, persisted);
    }

    // ============================================================
    // Interne
    // ============================================================

    /**
     * Confronte le diagnostic du classifieur aux conditions mesurées.
     * Deux voies indépendantes qui concordent renforcent la conclusion ;
     * c'est particulièrement utile lorsque la confiance du modèle est modeste.
     */
    private String corroborationFor(DiagnosisContext ctx, String diseaseCode) {
        DiseaseRisk risk = knowledgeService.riskFor(ctx.getCropName(), diseaseCode, ctx.getReading());
        if (risk == null) return null;

        double score = risk.getRiskScore();

        if (score >= CORROBORATION_THRESHOLD) {
            return String.format(Locale.FRANCE,
                    "Les conditions mesurées corroborent ce diagnostic : elles réunissent %.0f %% "
                            + "des circonstances d'apparition de cette maladie.",
                    score * 100);
        }

        // Le symptôme est réel, mais l'environnement actuel ne le favorise pas.
        // L'infection est vraisemblablement antérieure et son extension peu probable
        // tant que ces conditions perdurent.
        if (score <= DIVERGENCE_THRESHOLD) {
            return String.format(Locale.FRANCE,
                    "Les conditions mesurées ne soutiennent pas la progression de cette maladie : "
                            + "elles n'en réunissent que %.0f %% des circonstances d'apparition. "
                            + "Le symptôme observé résulte vraisemblablement de conditions antérieures. "
                            + "Traiter les tissus atteints reste utile, mais le risque d'extension demeure "
                            + "faible tant que l'environnement ne change pas.",
                    score * 100);
        }

        return null; // zone intermédiaire : rien de concluant à énoncer
    }

    /**
     * Risques encourus par la parcelle en dehors de la maladie déjà diagnostiquée.
     *
     * Une feuille peut porter le symptôme d'une maladie que l'environnement actuel
     * ne favorise plus, pendant que ce même environnement réunit les conditions
     * d'une autre. La maladie diagnostiquée est écartée : la corroboration en
     * traite déjà.
     */
    private List<DiseaseRisk> otherRisks(DiagnosisContext ctx, String diagnosedCode) {
        return knowledgeService.assessRisks(ctx.getCropName(), ctx.getReading()).stream()
                .filter(risk -> !risk.getDiseaseCode().equals(diagnosedCode))
                .toList();
    }

    private Diagnostic persistDiagnostic(DiagnosisContext ctx, String source, String result,
                                         Double confidence, AiModel model, String imageUrl) {
        return diagnosticRepository.save(Diagnostic.builder()
                .plot(ctx.getPlot())
                .aiModel(model)
                .reading(ctx.getReading())
                .source(source)
                .result(result)
                .confidenceScore(confidence)
                .cropName(ctx.getCropName())
                .imageUrl(imageUrl)
                .build());
    }

    private AiModel visionModel(String cropName) {
        return aiModelRepository.findByModelTypeAndCropName("VISION", cropName).orElse(null);
    }

    private Map<String, Object> toFeatureMap(DiagnosisContext ctx) {
        SensorReading r = ctx.getReading();
        Map<String, Object> f = new LinkedHashMap<>();
        // « temperature » reste la température de l'AIR : c'est sous cette clé
        // que le microservice l'attend, et la renommer romprait l'encodage des
        // modèles déjà entraînés.
        f.put("temperature", r.getTemperature());
        f.put("humidite_sol", r.getHumiditeSol());
        f.put("humidite_air", r.getHumiditeAir());
        f.put("ph", r.getPh());
        f.put("azote", r.getAzote());
        f.put("phosphore", r.getPhosphore());
        f.put("potassium", r.getPotassium());
        f.put("luminosite", r.getLuminosite());
        f.put("culture", ctx.getCropName());
        f.put("type_sol", ctx.getPlot().getSoilType());

        // Clés ajoutées, aucune renommée : le microservice les ignore tant qu'il
        // ne les lit pas, et il pourra les exploiter sans changement ici.
        f.put("temperature_sol", r.getTemperatureSol());
        f.put("pluviometrie", r.getPluviometrie());
        f.put("conductivite_electrique", r.getConductiviteElectrique());
        return f;
    }

    private DiagnosisResult build(Diagnostic diagnostic, DiagnosisContext ctx,
                                  List<RecommendationItem> items,
                                  List<ClassProbability> alternatives,
                                  List<TrendFinding> trends,
                                  String advisory, String corroboration,
                                  List<DiseaseRisk> risks) {

        List<RecommendationItem> merged = deduplicate(items);
        merged.addAll(knowledgeService.arbitrate(ctx.getCropName(), merged));

        // Dernière étape avant l'ordonnancement : le conseil doit être réalisable
        // sur CETTE parcelle. Un « irriguez » adressé à une parcelle pluviale est
        // ce qui apprend à l'exploitant que le système ignore sa situation.
        List<RecommendationItem> applicable =
                knowledgeService.adaptToPlot(ctx.getPlot(), merged);

        List<RecommendationItem> ordered = sortByPriority(applicable);

        for (RecommendationItem item : ordered) {
            recommendationRepository.save(Recommendation.builder()
                    .diagnostic(diagnostic)
                    .content(item.getContent())
                    .recommendationType(item.getType())
                    .priority(item.getPriority())
                    .status("ACTIVE")
                    .sourceRuleId(item.getSourceRuleId())
                    // V26 (A11) : le coût descend de la règle jusqu'au conseil.
                    // La colonne existait depuis la V16 et était exposée au
                    // frontend, mais rien ne la remplissait — le champ sortait
                    // toujours à null.
                    .estimatedCost(item.getEstimatedCost())
                    .measureField(item.getMeasureField())
                    .observedValue(item.getObservedValue())
                    .thresholdValue(item.getThresholdValue())
                    .build());
        }

        boolean reliable = confidenceEvaluator.isReliable(diagnostic.getConfidenceScore());
        alertService.raiseIfNeeded(diagnostic, ordered, reliable);

        return DiagnosisResult.builder()
                .diagnosticId(diagnostic.getId())
                .source(diagnostic.getSource())
                .result(diagnostic.getResult())
                .confidenceScore(diagnostic.getConfidenceScore())
                .cropName(Culture.canonical(diagnostic.getCropName()))
                .confidenceLevel(confidenceEvaluator.level(diagnostic.getConfidenceScore()))
                .reliable(reliable)
                .alternatives(alternatives)
                .comparison(comparativeExplainer.compare(
                        ctx.getCropName(), diagnostic.getResult(), diagnostic.getConfidenceScore(),
                        alternatives, ctx.getReading()))
                .advisory(advisory)
                .corroboration(corroboration)
                .dataQualityNote(dataQualityNoteFor(ctx.getReading()))
                .cropAutoResolved(ctx.isCropResolved())
                .readingAutoResolved(ctx.isReadingResolved())
                .indicators(knowledgeService.indicators(ctx.getCropName(), ctx.getGrowthStage(), ctx.getReading()))
                .risks(risks)
                .trends(trends)
                .recommendations(ordered)
                .build();
    }

    /**
     * Réserve à porter quand le relevé vient d'un boîtier dont les sondes sont
     * suspectes.
     *
     * <p>Un boîtier <em>défaillant</em> n'arrive jamais ici : l'ingestion inhibe
     * le diagnostic en amont. Reste le cas intermédiaire — l'écart aux voisins
     * est net sans être disqualifiant. Renoncer alors priverait l'exploitant de
     * conseils probablement justes ; les livrer sans réserve lui laisserait
     * croire qu'ils reposent sur des mesures sûres. La réserve est la seule
     * réponse honnête aux deux.
     */
    private String dataQualityNoteFor(SensorReading reading) {
        if (reading == null || reading.getDevice() == null) {
            return null;
        }

        SensorHealth health = SensorHealth.from(reading.getDevice().getSensorHealth());
        if (health == null || !health.warrantsCaution()) {
            return null;
        }

        String reason = reading.getDevice().getSensorHealthReason();
        return "À lire avec réserve : les mesures viennent d'un boîtier dont les sondes "
               + "s'écartent de celles de la parcelle. "
               + (reason == null ? health.getExplanation() : reason)
               + " La confiance affichée porte sur la conclusion du modèle, pas sur "
               + "l'exactitude des mesures qui l'ont produite.";
    }

    /** Deux règles distinctes peuvent aboutir au même conseil : on ne le répète pas. */
    private List<RecommendationItem> deduplicate(List<RecommendationItem> items) {
        Set<String> seen = new HashSet<>();
        List<RecommendationItem> unique = new ArrayList<>();
        for (RecommendationItem item : items) {
            if (seen.add(normalizeContent(item.getContent()))) {
                unique.add(item);
            }
        }
        return unique;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim().toLowerCase(Locale.FRANCE);
    }

    /** Les conseils urgents doivent apparaître en tête de réponse. */
    private List<RecommendationItem> sortByPriority(List<RecommendationItem> items) {
        return items.stream()
                .sorted(Comparator.<RecommendationItem>comparingInt(i -> rank(i.getPriority()))
                        .thenComparingInt(this::typeRank))
                .toList();
    }

    /** À priorité égale, la synthèse d'arbitrage précède les conseils qu'elle concilie. */
    private int typeRank(RecommendationItem item) {
        return RecommendationType.isArbitration(item.getType()) ? 0 : 1;
    }

    private int rank(String priority) {
        return RecommendationPriority.rankOf(priority);
    }
}
