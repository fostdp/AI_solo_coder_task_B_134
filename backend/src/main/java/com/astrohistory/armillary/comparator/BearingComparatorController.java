package com.astrohistory.armillary.comparator;

import com.astrohistory.armillary.dto.InstrumentComparisonDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/comparison")
@RequiredArgsConstructor
public class BearingComparatorController {

    private final BearingComparator bearingComparator;

    @GetMapping("/instruments")
    public ResponseEntity<List<InstrumentComparisonDTO>> compareInstrumentTypes(
            @PathVariable UUID instrumentId,
            @RequestParam(defaultValue = "赤道轴") String axisName) {
        return ResponseEntity.ok(
                bearingComparator.compareInstrumentBearings(instrumentId, axisName).join());
    }
}
