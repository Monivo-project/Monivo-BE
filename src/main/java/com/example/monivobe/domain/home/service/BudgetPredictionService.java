package com.example.monivobe.domain.home.service;

import com.example.monivobe.domain.home.dto.HomeAiReqDTO;
import com.example.monivobe.domain.home.dto.HomeAiResDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BudgetPredictionService {

    private final ChatClient chatClient;

    public HomeAiResDTO.ExpectedBudgetResult predict(
            HomeAiReqDTO.SpendingAnalysis analysis
    ) {

        String prompt = """
                당신은 개인 소비 분석 전문 AI입니다.

                사용자의 과거 소비 내역을 분석하여
                이번 달 예상 지출 금액과 권장 예산을 계산해주세요.

                반드시 다음 기준을 고려하세요.

                1. 최근 월별 지출 평균
                2. 최근 소비 증가 또는 감소 추세
                3. 이번 달 현재까지 사용한 금액
                4. 이번 달 경과 일수
                5. 월별 소비 변동성
                6. 카테고리별 소비 패턴

                단순히 최근 6개월 평균만 사용하지 말고
                최근 소비 추세가 있다면 이를 반영하세요.

                분석 데이터:
                %s

                반드시 다음 JSON 구조로만 응답하세요.

                {
                  "expectedAmount": 숫자,
                  "recommendedBudget": 숫자,
                  "reason": "예측 근거",
                  "confidence": 0~100 사이 정수
                }

                expectedAmount:
                이번 달 최종적으로 예상되는 총 지출 금액

                recommendedBudget:
                사용자가 이번 달 소비 관리를 위해 설정하는 것을 권장하는 예산

                reason:
                과거 소비 패턴과 이번 달 소비량을 기반으로 한 간단한 설명

                confidence:
                예측 신뢰도
                """.formatted(analysis);

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .entity(HomeAiResDTO.ExpectedBudgetResult.class);
    }
}
