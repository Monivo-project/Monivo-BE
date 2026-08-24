package com.example.monivobe.domain.transaction.enums;

public enum ClassificationType {
    UNCLASSIFIED,   // 아직 분류되지 않음
    KEYWORD,        // 기본 키워드로 분류
    MEMBER_KEYWORD, // 사용자 키워드로 분류
    LLM,            // LLM으로 분류
    MANUAL,         // 사용자가 직접 분류
    UNCONFIRMED     // AI 등이 분류했지만 사용자가 아직 확인하지 않음
}
