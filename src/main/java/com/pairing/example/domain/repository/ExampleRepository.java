package com.pairing.example.domain.repository;

import com.pairing.example.domain.model.Example;
import java.util.Optional;

// Application, Domain 계층이 사용할 레포지토리 포트(Port)
public interface ExampleRepository {
    Example save(Example example);
    Optional<Example> findById(Long id);
}