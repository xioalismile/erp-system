package com.erp.ai.controller;

import com.erp.ai.service.AiSpeechService;
import com.erp.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/speech")
@RequiredArgsConstructor
public class AiSpeechController {

    private final AiSpeechService aiSpeechService;

    @PostMapping("/recognize")
    public Result<Map<String, String>> recognize(@RequestParam("file") MultipartFile file) {
        Result<String> result = aiSpeechService.recognize(file);
        if (result.getCode() == 200) {
            return Result.success(Map.of("text", result.getData()));
        }
        return Result.error(result.getMessage());
    }
}
