package com.astrohistory.armillary.comparator;

import com.astrohistory.armillary.dto.CrossEraComparisonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/comparison")
@RequiredArgsConstructor
public class EraComparatorController {

    private final EraComparator eraComparator;

    @GetMapping("/cross-era")
    public ResponseEntity<CrossEraComparisonDTO> compareAcrossEras(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "赤道轴") String axisName) {
        return ResponseEntity.ok(
                eraComparator.compareAcrossEras(instrumentId, axisName));
    }
}
