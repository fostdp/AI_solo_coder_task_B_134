package com.astrohistory.armillary.analyzer;

import com.astrohistory.armillary.dto.LubricantComparisonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/comparison")
@RequiredArgsConstructor
public class LubricantAnalyzerController {

    private final LubricantAnalyzer lubricantAnalyzer;

    @GetMapping("/lubricants")
    public ResponseEntity<LubricantComparisonDTO> compareLubricants(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "赤道轴") String axisName) {
        return ResponseEntity.ok(
                lubricantAnalyzer.compareLubricants(instrumentId, axisName));
    }
}
