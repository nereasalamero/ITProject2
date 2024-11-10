package iot.ai.ky.local;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Endpoint de autenticación que devuelve token y refreshToken
    @PostMapping("/auth/login")
    public ResponseEntity<JsonNode> authenticate() {
        System.out.println("Token given");
        return loadJsonResponse("classpath:static/token.json");
    }

    // Endpoint que devuelve los datos de temperatura
    @GetMapping("/plugins/telemetry/DEVICE/{deviceId}/values/timeseries")
    public ResponseEntity<JsonNode> getTemperatureData(
            @PathVariable String deviceId,
            @RequestParam String keys,
            @RequestParam long startTs,
            @RequestParam long endTs,
            @RequestParam int limit
    ) {
        System.out.println("Device Id: " + deviceId);
        System.out.println("Keys: " + keys);
        System.out.println("Start: " + startTs);
        System.out.println("End: " + endTs);
        System.out.println("Limit: " + limit);
        return loadJsonResponse("classpath:static/dia_temperature.json");
    }

    // Método auxiliar para cargar JSON desde un archivo
    private ResponseEntity<JsonNode> loadJsonResponse(String path) {
        try {
            File file = ResourceUtils.getFile(path);
            JsonNode jsonResponse = objectMapper.readTree(file);
            return ResponseEntity.ok(jsonResponse);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}
