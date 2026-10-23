package com.tgnguyen.layoutlybe.controller;

import com.tgnguyen.layoutlybe.dto.BridgeImportResponse;
import com.tgnguyen.layoutlybe.service.BridgeImportService;
import com.tgnguyen.layoutlybe.service.BridgeSessionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bridge")
public class BridgeController {
    private final BridgeImportService bridgeImportService;
    private final BridgeSessionService sessionService;

    public BridgeController(BridgeImportService bridgeImportService, BridgeSessionService sessionService) {
        this.bridgeImportService = bridgeImportService;
        this.sessionService = sessionService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "READY", "tokenRequired", false);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BridgeImportResponse importFromPlugin(
            @RequestPart("metadata") MultipartFile metadata,
            @RequestPart("designJson") MultipartFile designJson,
            @RequestPart(value = "referenceImage", required = false) MultipartFile referenceImage,
            @RequestPart(value = "assets", required = false) List<MultipartFile> assets,
            @RequestParam(value = "sessionCode", required = false) String sessionCode) {
        boolean paired = sessionCode != null && !sessionCode.isBlank();
        if (paired) sessionService.require(sessionCode); // bao loi som neu ma sai/het han
        BridgeImportResponse response = bridgeImportService.importSnapshot(metadata, designJson, referenceImage, assets);
        if (paired) sessionService.attachSnapshot(sessionCode, response.snapshotId());
        return response;
    }

    // ---- Ghep cap web <-> plugin ----

    /** Web tao phien va hien ma cho nguoi dung nhap vao plugin. */
    @PostMapping("/sessions")
    public BridgeSessionService.SessionView createSession() {
        return sessionService.create();
    }

    /** Web hoi trang thai: plugin da ket noi chua, da co snapshotId chua. */
    @GetMapping("/sessions/{code}")
    public BridgeSessionService.SessionView getSession(@PathVariable String code) {
        return sessionService.get(code);
    }

    /** Web yeu cau plugin xuat thiet ke (scope: SELECTION | PAGE). */
    @PostMapping(value = "/sessions/{code}/sync", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BridgeSessionService.SessionView requestSync(@PathVariable String code,
                                                        @RequestBody(required = false) Map<String, String> body) {
        return sessionService.requestSync(code, body == null ? null : body.get("scope"));
    }

    /** Plugin poll: bao hieu dang online va nhan lenh dong bo neu co. */
    @GetMapping("/sessions/{code}/poll")
    public Map<String, Object> poll(@PathVariable String code) {
        BridgeSessionService.Command command = sessionService.poll(code);
        Map<String, Object> result = new HashMap<>();
        result.put("command", command);
        return result;
    }
}
