package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 보유/요구 스킬. 요구사항 R21/R30의 스킬 목록이다.
 *
 * <p>화면 목록은 {@code GET /api/v1/meta/skills} 가 내려준다. 항목이 늘면 여기에 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum SkillCode {

    REACT("React"), NEXT_JS("Next.js"), VUE("Vue"), TYPESCRIPT("TypeScript"),
    JAVASCRIPT("JavaScript"), ANGULAR("Angular"), SVELTE("Svelte"), REDUX("Redux"),
    TAILWIND("Tailwind"), JAVA("Java"), SPRING_BOOT("Spring Boot"), NODE_JS("Node.js"),
    NEST_JS("NestJS"), PYTHON("Python"), DJANGO("Django"), FASTAPI("FastAPI"),
    GO("Go"), PHP("PHP"), LARAVEL("Laravel"), DOTNET("C#/.NET"), KOTLIN("Kotlin"),
    SWIFT("Swift"), FLUTTER("Flutter"), REACT_NATIVE("React Native"), SQL("SQL"),
    PANDAS("Pandas"), TENSORFLOW("TensorFlow"), PYTORCH("PyTorch"),
    SCIKIT_LEARN("scikit-learn"), SPARK("Spark"), LANGCHAIN("LangChain"),
    MYSQL("MySQL"), POSTGRESQL("PostgreSQL"), MONGODB("MongoDB"), REDIS("Redis"),
    ORACLE("Oracle"), ELASTICSEARCH("Elasticsearch"), AWS("AWS"), GCP("GCP"),
    DOCKER("Docker"), KUBERNETES("Kubernetes"), JENKINS("Jenkins"),
    GITHUB_ACTIONS("GitHub Actions"), TERRAFORM("Terraform"), NGINX("Nginx"),
    LINUX("Linux"), UNITY("Unity"), UNREAL("Unreal"), CPP("C++"), SOLIDITY("Solidity"),
    FIGMA("Figma"), SKETCH("Sketch"), ADOBE_XD("Adobe XD"), PHOTOSHOP("Photoshop"),
    ILLUSTRATOR("Illustrator"), AFTER_EFFECTS("After Effects"), ZEPLIN("Zeplin"),
    GIT("Git"), JIRA("Jira"), NOTION("Notion"), REST_API("REST API"),
    GRAPHQL("GraphQL"), SWAGGER("Swagger");

    private final String label;
}
