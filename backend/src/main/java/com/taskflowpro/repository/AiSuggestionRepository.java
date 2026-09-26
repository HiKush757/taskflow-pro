package com.taskflowpro.repository;

import com.taskflowpro.model.AiSuggestion;
import com.taskflowpro.model.SuggestionState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, String> {

    List<AiSuggestion> findByTaskIdAndState(String taskId, SuggestionState state);
}
