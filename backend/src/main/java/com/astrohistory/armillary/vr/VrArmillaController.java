package com.astrohistory.armillary.vr;

import com.astrohistory.armillary.dto.VirtualOperationRequest;
import com.astrohistory.armillary.dto.VirtualOperationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/instruments/{instrumentId}/comparison")
@RequiredArgsConstructor
public class VrArmillaController {

    private final VrArmillaService vrArmillaService;

    @PostMapping("/virtual-operation")
    public ResponseEntity<VirtualOperationResponse> virtualOperation(
            @PathVariable UUID instrumentId,
            @RequestBody VirtualOperationRequest request) {
        request.setInstrumentId(instrumentId);
        return ResponseEntity.ok(
                vrArmillaService.simulateVirtualOperation(request));
    }
}
