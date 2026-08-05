package com.pairing.template_server.example.infrastructure.mapper;

import com.pairing.template_server.example.domain.model.Example;
import com.pairing.template_server.example.infrastructure.persistence.ExampleJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExampleMapper {

    // 1. Domain Entity -> JPA Entity 변환
    // ExampleJpaEntity의 생성자나 필드를 MapStruct가 자동으로 매핑합니다.
    ExampleJpaEntity toJpaEntity(Example example);

    // 2. JPA Entity -> Domain Entity 변환
    // 도메인 객체는 캡슐화되어 기본 생성자가 닫혀 있으므로,
    // MapStruct의 default 메서드를 활용해 정적 팩토리 메서드(reconstitute)로 직접 복원합니다.
    default Example toDomain(ExampleJpaEntity jpaEntity) {
        if (jpaEntity == null) {
            return null;
        }
        return Example.reconstitute(
                jpaEntity.getId(),
                jpaEntity.getName(),
                jpaEntity.isActive(),
                jpaEntity.getImageUrl()
        );
    }
}