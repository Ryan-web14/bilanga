package com.sni.bilanga.farm.controller;


import com.sni.bilanga.audit.aop.Audited;
import com.sni.bilanga.idempotency.aop.Idempotent;
import com.sni.bilanga.farm.dto.request.CropCloneRequest;
import com.sni.bilanga.farm.dto.request.CropClosureRequest;
import com.sni.bilanga.farm.dto.response.CropCalendar;
import com.sni.bilanga.farm.dto.response.CropClosureResponse;
import com.sni.bilanga.farm.dto.response.CropComparison;
import com.sni.bilanga.farm.dto.response.CropItinerary;
import com.sni.bilanga.farm.dto.response.CropJournalEntry;
import com.sni.bilanga.farm.dto.response.CropThresholds;
import com.sni.bilanga.farm.dto.request.PlannedOperationRequest;
import com.sni.bilanga.farm.dto.response.PlannedOperationResponse;
import java.util.List;
import com.sni.bilanga.enums.CropStatus;
import com.sni.bilanga.enums.Culture;
import com.sni.bilanga.enums.GrowthStage;
import com.sni.bilanga.farm.dto.request.CropRequest;
import com.sni.bilanga.farm.dto.response.CropResponse;
import com.sni.bilanga.farm.service.interfaces.CropService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.templateResponse.PaginatedResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/crops")
public class CropController {

    private final CropService cropService;

    @PostMapping
    public ResponseEntity<ApiResponse<CropResponse>> create(@Valid @RequestBody CropRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Culture enregistrée.", cropService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CropResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody CropRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Culture mise à jour.", cropService.update(id, request)));
    }

    /**
     * Calendrier cultural : les stades franchis et ceux <strong>à venir</strong>.
     *
     * <p>La seule vue du système qui <em>annonce</em> au lieu de constater. Tout le
     * reste est réactif par construction — une mesure, un symptôme, un écart. Ici,
     * « floraison attendue dans neuf jours, prévoyez le traitement préventif ».
     *
     * <p>⚠️ {@code limitation} est toujours renseigné et doit être affiché à côté
     * des dates : ce sont des projections sur des proportions de cycle indicatives,
     * et un exploitant qui prépare un traitement pour une date fausse perd le
     * produit et la fenêtre.
     */
    @GetMapping("/{id}/calendar")
    public ResponseEntity<ApiResponse<CropCalendar>> calendar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.calendarFor(id)));
    }

    /**
     * Clôture riche : date de fin réelle, motif, et bilan économique <strong>figé</strong>.
     *
     * <p>S'ajoute à {@code DELETE /crops/{id}}, qui reste inchangé — celui-ci se
     * contente de passer le statut à {@code TERMINEE} sans dire ni quand ni pourquoi, ni
     * ce que la campagne a rapporté. Casser une route n'étant pas additif, les deux
     * coexistent : le {@code DELETE} archive, ce {@code POST} clôt.
     *
     * <p>Le motif est <strong>obligatoire</strong>. C'est lui qui rend l'historique
     * interprétable : un rendement nul après {@code RECOLTE_NORMALE} signale un problème
     * agronomique, le même rendement nul après {@code PERTE_CLIMATIQUE} ne signale que
     * la météo.
     *
     * <p>Non rejouable : une seconde clôture répond <strong>400</strong>. Un bilan figé
     * qu'on réécrirait ne serait plus un bilan figé.
     *
     * <p>⚠️ Expose des marges — à masquer sur {@code HARVEST:READ} côté client, comme
     * {@code /plots/{id}/economics}.
     */
    @PostMapping("/{id}/close")
    @Audited(module = "FARM", action = "CROP_CLOSE", ressource = "crop")
    @Idempotent(operation = "CROP_CLOSE", required = false)
    public ResponseEntity<ApiResponse<CropClosureResponse>> close(
            @PathVariable Long id, @Valid @RequestBody CropClosureRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Campagne clôturée.",
                cropService.close(id, request)));
    }

    /**
     * Le bilan arrêté à la clôture, le bilan recalculé aujourd'hui, et leur écart.
     *
     * <p><strong>Les deux côte à côte, et c'est le point.</strong>
     * {@code MarginCalculator} pose que rien n'est stocké, au motif qu'« un total en
     * cache diverge dès la première correction de saisie, et personne ne sait plus lequel
     * croire ». La réponse n'est pas de renoncer au figé — un bilan de campagne qui bouge
     * n'est pas un bilan de campagne — mais de rendre les deux, datés, avec leur écart
     * expliqué. Le chiffre arrêté est la référence ; l'écart <em>devient</em> le signal
     * d'audit.
     *
     * <p>Le cas concret : la suppression d'une récolte est <strong>réelle</strong> dans
     * ce projet. Une récolte supprimée après clôture rend le bilan figé faux, et
     * {@code divergenceStatement} est exactement ce qui le rend visible.
     *
     * <p>⚠️ {@code divergenceStatement} n'est <strong>jamais nul</strong>, y compris
     * quand rien n'a bougé : « identique à celui arrêté à la clôture » est une
     * information rassurante, et un blanc obligerait le client à l'interpréter.
     */
    @GetMapping("/{id}/closure")
    public ResponseEntity<ApiResponse<CropClosureResponse>> closure(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.closureOf(id)));
    }

    /**
     * Cette campagne comparée à la précédente de la <strong>même culture</strong>.
     *
     * <p>Même culture, parce qu'opposer une tomate à un manioc ne dit rien : ni les
     * rendements, ni les cycles, ni les charges ne sont commensurables. C'est ce qui
     * permet de lire « 2 300 kg/ha contre 1 900 l'an dernier » comme une information et
     * non comme un artefact.
     *
     * <p>Porte sur les bilans <strong>figés</strong> des deux campagnes — c'est ce qui
     * rend la comparaison stable. Les recalculer à chaque appel les ferait bouger dès
     * qu'une récolte est saisie, et deux consultations donneraient des écarts différents.
     *
     * <p>{@code comparable: false} ⇒ première campagne de cette culture sur la parcelle.
     * {@code summary} l'énonce explicitement plutôt que de rendre un tableau vide.
     *
     * <p>⚠️ {@code limitation} doit être affiché : un écart constate une évolution, il
     * n'en donne pas la cause. La météo, la variété et l'attention portée à la parcelle
     * varient toutes ensemble.
     */
    @GetMapping("/{id}/compare-previous")
    public ResponseEntity<ApiResponse<CropComparison>> comparePrevious(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.compareWithPrevious(id)));
    }

    /**
     * Journal des révisions d'une campagne, du plus récent au plus ancien.
     *
     * <p>Les entrées d'effacement — {@code before} renseigné vers {@code after: null} —
     * résultent d'un {@code clearFields} <strong>explicite</strong>. La mise à jour est
     * partielle : un champ absent n'est plus touché, et ne produit donc plus d'entrée.
     * {@code changeCount} permet de replier les entrées volumineuses.
     *
     * <p>{@code humanAction: false} distingue les {@code STADE_RECALCULE} — le temps qui
     * passe, non une décision — des vraies modifications. Les afficher au même rang
     * ferait porter à un utilisateur des changements qui ne sont pas les siens.
     */
    @GetMapping("/{id}/journal")
    public ResponseEntity<ApiResponse<List<CropJournalEntry>>> journal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.journalOf(id)));
    }

    /**
     * Sur quoi le moteur agronomique juge cette campagne — stade par stade.
     *
     * <p><strong>Le manque comblé.</strong> Le système compare chaque mesure à des seuils
     * et en tire un conseil ; l'exploitant voyait le conseil et jamais le seuil. Quand le
     * système annonce un stress hydrique à 34 % d'humidité, rien ne lui disait que le
     * minimum retenu est 35, ni qu'il avait changé au passage en fructification.
     *
     * <p><strong>{@code origin} est le champ à afficher.</strong> {@code GENERALE} = seuil
     * de la culture ; {@code STADE} = propre à cette phase. C'est ce qui explique qu'une
     * même mesure déclenche un conseil à un stade et pas à un autre — sans quoi le
     * système paraît changer d'avis.
     *
     * <p>Tous les stades sont rendus, futurs compris : lire les seuils à venir permet
     * d'anticiper, ce qui est l'usage le plus utile de cette vue.
     *
     * <p>⚠️ {@code limitation} doit être affiché : les valeurs semées sont
     * <strong>indicatives</strong> et n'ont pas été validées par une source agronomique.
     */
    @GetMapping("/{id}/thresholds")
    public ResponseEntity<ApiResponse<CropThresholds>> thresholds(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.thresholdsOf(id)));
    }

    /**
     * Relance une campagne sur le modèle de celle-ci.
     *
     * <p><strong>Le geste que cela remplace.</strong> Ressaisir une campagne identique à
     * celle de l'an dernier demandait de retrouver l'ancienne fiche et de recopier une
     * dizaine de champs, plus autant de lignes d'itinéraire. Chaque recopie est une
     * occasion de se tromper, et une densité mal reportée fausse le rendement à l'hectare
     * pour toute la campagne.
     *
     * <p><strong>Seule {@code plantingDate} est obligatoire</strong> ; tout le reste est
     * repris, et chaque champ envoyé surcharge la source.
     *
     * <p>⚠️ {@code seedLot} n'est <strong>jamais</strong> repris — un lot est consommé, et
     * le reporter serait un mensonge de traçabilité. Prévoyez le champ dans le formulaire
     * de clonage, vide, avec la mention de son usage.
     *
     * <p>Répond <strong>400</strong> si une campagne est déjà en cours sur la parcelle
     * d'accueil : proposez de clôturer la précédente.
     */
    @PostMapping("/{id}/clone")
    @Idempotent(operation = "CROP_CLONE", required = false)
    public ResponseEntity<ApiResponse<CropResponse>> clone(
            @PathVariable Long id, @Valid @RequestBody CropCloneRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Campagne relancée par clonage.", cropService.cloneFrom(id, request)));
    }

    /**
     * L'itinéraire technique : ce qui était <strong>prévu</strong>, et ce qui a suivi.
     *
     * <p><strong>Le troisième terme.</strong> Le système savait ce qui a été fait
     * ({@code /interventions}) et ce qu'il conseille ({@code /recommendations}), jamais
     * ce qui était prévu. Sans lui, une opération oubliée est indiscernable d'une
     * opération jamais planifiée, et le coût d'une campagne ne se connaît qu'après la
     * récolte — trop tard pour arbitrer.
     *
     * <p><strong>Deux natures de rapprochement, à distinguer visuellement.</strong>
     * {@code matchConfirmed: true} est un fait — quelqu'un l'a validé. {@code false} est
     * une <em>hypothèse</em> du système, recalculée à chaque appel : elle peut changer si
     * une date est corrigée ou une intervention saisie après coup. Les afficher au même
     * rang ferait passer une inférence pour un constat.
     *
     * <p>{@code late} et {@code resolvedDate} sont <strong>calculés</strong> et n'existent
     * pas en base : le projet n'a pas d'ordonnanceur, un statut « en retard » persisté
     * serait faux dès le lendemain.
     */
    @GetMapping("/{id}/itinerary")
    public ResponseEntity<ApiResponse<CropItinerary>> itinerary(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.itineraryOf(id)));
    }

    /**
     * Ajoute une opération à l'itinéraire.
     *
     * <p>Seuls {@code type} et <strong>l'une des deux datations</strong> sont
     * obligatoires. Un itinéraire se saisit en amont, avec des produits et des doses qui
     * ne seront arrêtés qu'au moment de faire : refuser la ligne parce que la dose manque
     * reviendrait à n'avoir aucun plan plutôt qu'un plan incomplet.
     *
     * <p>Préférez {@code daysAfterPlanting} à {@code plannedOn} : c'est la forme qui
     * survit au clonage vers une campagne plantée un autre jour.
     */
    @PostMapping("/{id}/itinerary")
    public ResponseEntity<ApiResponse<PlannedOperationResponse>> addOperation(
            @PathVariable Long id, @Valid @RequestBody PlannedOperationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Opération ajoutée à l'itinéraire.", cropService.addOperation(id, request)));
    }

    /** Remplacement complet de l'opération — non partiel, contrairement à {@code PUT /crops/{id}}. */
    @PutMapping("/{id}/itinerary/{operationId}")
    public ResponseEntity<ApiResponse<PlannedOperationResponse>> updateOperation(
            @PathVariable Long id, @PathVariable Long operationId,
            @Valid @RequestBody PlannedOperationRequest request) {

        return ResponseEntity.ok(ApiResponse.success("Opération mise à jour.",
                cropService.updateOperation(id, operationId, request)));
    }

    /**
     * Suppression <strong>réelle</strong>, comme pour les interventions et les récoltes.
     *
     * <p>Une opération planifiée par erreur fausse le coût prévisionnel et le taux de
     * réalisation — les deux chiffres qui font la valeur de l'itinéraire. L'archiver
     * reviendrait à conserver une erreur dans un calcul.
     */
    @DeleteMapping("/{id}/itinerary/{operationId}")
    public ResponseEntity<ApiResponse<Void>> deleteOperation(
            @PathVariable Long id, @PathVariable Long operationId) {

        cropService.deleteOperation(id, operationId);
        return ResponseEntity.ok(ApiResponse.success("Opération supprimée.", null));
    }

    /**
     * Confirme à la main qu'une intervention satisfait une opération prévue.
     *
     * <p>Le <strong>seul</strong> chemin par lequel un rapprochement s'écrit en base. Les
     * rapprochements automatiques restent recalculés à chaque lecture : un mauvais
     * appariement persisté se propage au coût constaté et au clonage, là où un mauvais
     * appariement recalculé disparaît dès que la donnée s'améliore.
     *
     * <p>{@code interventionId} <strong>omis ou nul</strong> défait la confirmation et
     * rend l'opération à l'inférence — sans quoi une erreur de saisie serait définitive.
     */
    @PostMapping("/{id}/itinerary/{operationId}/match")
    public ResponseEntity<ApiResponse<PlannedOperationResponse>> confirmMatch(
            @PathVariable Long id, @PathVariable Long operationId,
            @RequestParam(required = false) Long interventionId) {

        return ResponseEntity.ok(ApiResponse.success(
                interventionId == null ? "Rapprochement défait." : "Rapprochement confirmé.",
                cropService.confirmMatch(id, operationId, interventionId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CropResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(cropService.findById(id)));
    }

    /** {@code plotId} n'est plus obligatoire : la liste complète est consultable, paginée. */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<CropResponse>>> search(
            @RequestParam(required = false) Long plotId,
            @RequestParam(required = false) Culture cropName,
            @RequestParam(required = false) CropStatus status,
            @RequestParam(required = false) GrowthStage growthStage,
            @PageableDefault(size = 20, sort = "plantingDate", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(new PaginatedResponse<>(
                cropService.search(plotId, cropName, status, growthStage, pageable))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        cropService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Culture clôturée.", null));
    }
}
