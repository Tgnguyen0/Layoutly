package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.dto.BridgeImportResponse;
import com.tgnguyen.layoutlybe.service.BridgeImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bridge")
public class BridgeController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.tgnguyen.layoutlybe.service.SavedProjectService savedProjects;
    @org.springframework.beans.factory.annotation.Autowired
    private com.tgnguyen.layoutlybe.service.FigmaSnapshotService snapshots;
    @org.springframework.beans.factory.annotation.Autowired
    private com.tgnguyen.layoutlybe.service.AccountService accounts;
    private final BridgeImportService bridgeImportService;

    public BridgeController(BridgeImportService bridgeImportService) {
        this.bridgeImportService = bridgeImportService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "READY", "tokenRequired", true, "pairingRequired", true);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BridgeImportResponse importFromPlugin(
            @RequestPart("metadata") MultipartFile metadata,
            @RequestPart("designJson") MultipartFile designJson,
            @RequestPart(value = "referenceImage", required = false) MultipartFile referenceImage,
            @RequestPart(value = "assets", required = false) List<MultipartFile> assets,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Layoutly-Bridge-Token", required = false) String bridgeToken) {
        BridgeImportResponse result = bridgeImportService.importSnapshot(metadata, designJson, referenceImage, assets);
        if (savedProjects != null) savedProjects.registerSnapshot(com.tgnguyen.layoutlybe.security.CurrentUser.id(), snapshots.requireSnapshot(result.snapshotId()));
        if (accounts != null) accounts.bindBridge(bridgeToken, result.snapshotId());
        return result;
    }
}
