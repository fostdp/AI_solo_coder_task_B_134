package com.astrohistory.armillary.controller;

import com.astrohistory.armillary.dto.*;
import com.astrohistory.armillary.service.ComparisonAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/comparison")
@RequiredArgsConstructor
public class ComparisonController {

    private final ComparisonAnalysisService comparisonService;

    @GetMapping("/instruments")
    public ResponseEntity<List<InstrumentComparisonDTO>> compareInstrumentTypes(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "赤道轴") String axisName) {
        return ResponseEntity.ok(
                comparisonService.compareInstrumentBearings(instrumentId, axisName));
    }

    @GetMapping("/cross-era")
    public ResponseEntity<CrossEraComparisonDTO> compareAcrossEras(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "赤道轴") String axisName) {
        return ResponseEntity.ok(
                comparisonService.compareAcrossEras(instrumentId, axisName));
    }

    @GetMapping("/lubricants")
    public ResponseEntity<LubricantComparisonDTO> compareLubricants(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "赤道轴") String axisName) {
        return ResponseEntity.ok(
                comparisonService.compareLubricants(instrumentId, axisName));
    }

    @PostMapping("/virtual-operation")
    public ResponseEntity<VirtualOperationResponse> virtualOperation(
            @PathVariable UUID instrumentId,
            @RequestBody VirtualOperationRequest request) {
        request.setInstrumentId(instrumentId);
        return ResponseEntity.ok(
                comparisonService.simulateVirtualOperation(request));
    }
}
