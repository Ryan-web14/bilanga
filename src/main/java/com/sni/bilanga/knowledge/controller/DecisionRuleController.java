package com.sni.bilanga.knowledge.controller;


import com.sni.bilanga.knowledge.dto.request.ArbitrationRequest;
import com.sni.bilanga.knowledge.dto.request.CorrelationRuleRequest;
import com.sni.bilanga.knowledge.dto.request.KnowledgeRuleRequest;
import com.sni.bilanga.knowledge.dto.response.ArbitrationResponse;
import com.sni.bilanga.knowledge.dto.response.CorrelationRuleResponse;
import com.sni.bilanga.knowledge.dto.response.KnowledgeRuleResponse;
import com.sni.bilanga.knowledge.service.interfaces.DecisionRuleService;
import com.sni.bilanga.templateResponse.ApiResponse;
import com.sni.bilanga.utils.path.ApiPath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Administration des trois familles de règles du système expert :
 * conseils attachés à un diagnostic capteur ({@code /rules}), corrélations
 * maladie / mesures ({@code /correlations}) et arbitrages entre conseils qui
 * se contrarient ({@code /arbitrations}). Toutes déléguées au
 * {@link DecisionRuleService}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPath.V1 + "/knowledge")
public class DecisionRuleController {

    private final DecisionRuleService service;

    // ============================================================
    // Règles attachées à un diagnostic capteur
    // ============================================================
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<KnowledgeRuleResponse>> createRule(
            @Valid @RequestBody KnowledgeRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Règle enregistrée.", service.createRule(request)));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<KnowledgeRuleResponse>> updateRule(
            @PathVariable Long id, @Valid @RequestBody KnowledgeRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Règle mise à jour.", service.updateRule(id, request)));
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<KnowledgeRuleResponse>>> findRules(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.success(service.findRules(category)));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        service.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success("Règle supprimée.", null));
    }

    // ============================================================
    // Corrélations maladie / mesures
    // ============================================================
    @PostMapping("/correlations")
    public ResponseEntity<ApiResponse<CorrelationRuleResponse>> createCorrelation(
            @Valid @RequestBody CorrelationRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Corrélation enregistrée.", service.createCorrelation(request)));
    }

    @PutMapping("/correlations/{id}")
    public ResponseEntity<ApiResponse<CorrelationRuleResponse>> updateCorrelation(
            @PathVariable Long id, @Valid @RequestBody CorrelationRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Corrélation mise à jour.",
                service.updateCorrelation(id, request)));
    }

    @GetMapping("/correlations")
    public ResponseEntity<ApiResponse<List<CorrelationRuleResponse>>> findCorrelations() {
        return ResponseEntity.ok(ApiResponse.success(service.findCorrelations()));
    }

    @DeleteMapping("/correlations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCorrelation(@PathVariable Long id) {
        service.deleteCorrelation(id);
        return ResponseEntity.ok(ApiResponse.success("Corrélation supprimée.", null));
    }

    // ============================================================
    // Arbitrages entre conseils contradictoires
    // ============================================================
    @PostMapping("/arbitrations")
    public ResponseEntity<ApiResponse<ArbitrationResponse>> createArbitration(
            @Valid @RequestBody ArbitrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Arbitrage enregistré.", service.createArbitration(request)));
    }

    @PutMapping("/arbitrations/{id}")
    public ResponseEntity<ApiResponse<ArbitrationResponse>> updateArbitration(
            @PathVariable Long id, @Valid @RequestBody ArbitrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Arbitrage mis à jour.",
                service.updateArbitration(id, request)));
    }

    @GetMapping("/arbitrations")
    public ResponseEntity<ApiResponse<List<ArbitrationResponse>>> findArbitrations() {
        return ResponseEntity.ok(ApiResponse.success(service.findArbitrations()));
    }

    @DeleteMapping("/arbitrations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteArbitration(@PathVariable Long id) {
        service.deleteArbitration(id);
        return ResponseEntity.ok(ApiResponse.success("Arbitrage supprimé.", null));
    }
}
