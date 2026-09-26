package com.taskflowpro.repository;

import com.taskflowpro.model.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Dependency.DependencyId> {

    List<Dependency> findByIdTaskId(String taskId);

    List<Dependency> findByIdPrerequisiteId(String prerequisiteId);

    void deleteByIdPrerequisiteIdAndIdTaskId(String prerequisiteId, String taskId);
}
