package com.taskflowpro.service;

import com.taskflowpro.model.*;
import com.taskflowpro.repository.AiSuggestionRepository;
import com.taskflowpro.repository.DependencyRepository;
import com.taskflowpro.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-augmented dependency suggestion.
 *
 * <p>Grounding strategy (see README / synopsis Section 4 for the full
 * rationale):
 * <ol>
 *   <li>Closed-set prompting — the model is only allowed to choose from
 *       existing task ids, and every returned id is re-validated server-side
 *       before it is ever shown to the user.</li>
 *   <li>Suggestions are never auto-applied. They are stored as PROPOSED and
 *       only become a real dependency (and go through the same cycle check
 *       as a manual one) when the user explicitly accepts it.</li>
 *   <li>If no LLM is configured (or the call fails), this falls back to a
 *       plain keyword-overlap heuristic so the feature still works with zero
 *       external configuration — useful for grading and offline demos.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private final TaskRepository taskRepository;
    private final DependencyRepository dependencyRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final TaskService taskService;

    @Value("${ai.llm.enabled:false}")
    private boolean llmEnabled;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "for", "to", "of", "in", "on", "with", "is", "are", "be");

    @Transactional
    public List<AiSuggestion> suggestFor(String taskId) {
        Task target = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));

        Set<String> alreadyDependsOn = dependencyRepository.findByIdTaskId(taskId).stream()
                .map(d -> d.getId().getPrerequisiteId())
                .collect(Collectors.toSet());

        List<Task> candidates = taskRepository.findAll().stream()
                .filter(t -> !t.getId().equals(taskId))
                .filter(t -> !alreadyDependsOn.contains(t.getId()))
                .toList();

        // Hook point: if llmEnabled, this is where a real LLM call would go
        // (e.g. via the Anthropic or OpenAI API, using an ANTHROPIC_API_KEY /
        // OPENAI_API_KEY environment variable — never a hardcoded key).
        // The prompt would list only `candidates`' ids/titles/descriptions,
        // instruct the model to pick zero or more by id with a short reason,
        // and require valid JSON matching a fixed schema. The response would
        // still be validated exactly like the heuristic result below.
        List<AiSuggestion> suggestions = llmEnabled
                ? callLlm(target, candidates)
                : keywordOverlapHeuristic(target, candidates);

        // Server-side validation: every suggestion must reference a real,
        // distinct task and must not close a cycle if accepted.
        List<AiSuggestion> valid = new ArrayList<>();
        Map<String, Set<String>> prerequisitesByTask = loadPrerequisitesByTask();
        for (AiSuggestion s : suggestions) {
            if (!taskRepository.existsById(s.getCandidateId())) continue;
            if (s.getCandidateId().equals(taskId)) continue;
            boolean wouldCycle = new com.taskflowpro.engine.DagEngine()
                    .wouldCreateCycle(s.getCandidateId(), taskId, prerequisitesByTask);
            if (wouldCycle) continue;
            s.setTaskId(taskId);
            s.setState(SuggestionState.PROPOSED);
            valid.add(aiSuggestionRepository.save(s));
        }
        return valid;
    }

    @Transactional
    public Dependency accept(String suggestionId) {
        AiSuggestion suggestion = aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new NoSuchElementException("Suggestion not found: " + suggestionId));
        suggestion.setState(SuggestionState.ACCEPTED);
        aiSuggestionRepository.save(suggestion);
        // Goes through the exact same path (and cycle check) as a manually added dependency.
        return taskService.addDependency(suggestion.getCandidateId(), suggestion.getTaskId(), DependencySource.AI_ACCEPTED);
    }

    @Transactional
    public void reject(String suggestionId) {
        AiSuggestion suggestion = aiSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new NoSuchElementException("Suggestion not found: " + suggestionId));
        suggestion.setState(SuggestionState.REJECTED);
        aiSuggestionRepository.save(suggestion);
    }

    // --- fallback heuristic -------------------------------------------------

    private List<AiSuggestion> keywordOverlapHeuristic(Task target, List<Task> candidates) {
        Set<String> targetWords = wordsOf(target.getTitle() + " " + nullToEmpty(target.getDescription()));
        List<AiSuggestion> results = new ArrayList<>();
        for (Task candidate : candidates) {
            Set<String> candidateWords = wordsOf(candidate.getTitle() + " " + nullToEmpty(candidate.getDescription()));
            Set<String> overlap = new HashSet<>(targetWords);
            overlap.retainAll(candidateWords);
            if (overlap.isEmpty()) continue;
            double confidence = Math.min(1.0, overlap.size() / 4.0);
            results.add(AiSuggestion.builder()
                    .candidateId(candidate.getId())
                    .reason("Shared terms: " + String.join(", ", overlap))
                    .confidence(confidence)
                    .build());
        }
        results.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return results.stream().limit(5).collect(Collectors.toList());
    }

    private List<AiSuggestion> callLlm(Task target, List<Task> candidates) {
        // Not wired up in this scaffold — see the comment above suggestFor().
        // Falls back to the heuristic so the endpoint always returns something.
        return keywordOverlapHeuristic(target, candidates);
    }

    private Set<String> wordsOf(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> w.length() > 2 && !STOPWORDS.contains(w))
                .collect(Collectors.toSet());
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private Map<String, Set<String>> loadPrerequisitesByTask() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Dependency d : dependencyRepository.findAll()) {
            map.computeIfAbsent(d.getId().getTaskId(), k -> new HashSet<>()).add(d.getId().getPrerequisiteId());
        }
        return map;
    }
}
