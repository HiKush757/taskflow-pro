package com.taskflowpro.controller;

import com.taskflowpro.model.AiSuggestion;
import com.taskflowpro.model.Dependency;
import com.taskflowpro.service.AiSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;

    @PostMapping("/tasks/{taskId}/suggest-dependencies")
    public List<AiSuggestion> suggest(@PathVariable String taskId) {
        return aiSuggestionService.suggestFor(taskId);
    }

    @PostMapping("/suggestions/{suggestionId}/accept")
    public Dependency accept(@PathVariable String suggestionId) {
        return aiSuggestionService.accept(suggestionId);
    }

    @PostMapping("/suggestions/{suggestionId}/reject")
    public void reject(@PathVariable String suggestionId) {
        aiSuggestionService.reject(suggestionId);
    }
}
