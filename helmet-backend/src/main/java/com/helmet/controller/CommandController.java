package com.helmet.controller;

import com.helmet.dto.CommandRequest;
import com.helmet.service.CommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/devices/{id}/buzzer")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> setBuzzer(
            @PathVariable String id,
            @RequestBody CommandRequest request) {
        boolean on = request.getOn() != null && request.getOn();
        boolean success = commandService.setBuzzer(id, on);
        return ResponseEntity.ok(Map.of("success", success, "buzzerOn", on));
    }
}
