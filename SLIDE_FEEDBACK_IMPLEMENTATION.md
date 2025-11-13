# 슬라이드별 피드백 시스템 구현 완료 ✅

## 구현 내용

### 1. 엔티티 수정
- **Feedback.java**: 공백 관련 필드 추가
  - `silenceCount`: 2.5초 이상 공백 횟수
  - `totalSilenceDuration`: 총 공백 시간
  - `silenceScore`: 공백 점수
  - `silenceAnalysisSuccess`: 공백 분석 성공 여부

- **SlideFeedback.java**: 이미 생성되어 있음 (모든 필요 필드 포함)

### 2. Repository 생성
- **SlideFeedbackRepository.java** ✅
  - `findByFeedbackIdOrderBySlideNumber()`: 피드백 ID로 슬라이드 피드백 조회
  - `findByFeedbackIdAndSlideNumber()`: 특정 슬라이드 피드백 조회

### 3. Service 로직 구현

#### AudioAnalysisService.java ✅
- **슬라이드별 분석 이미 수행 중!**
  - ✅ 필러 분석: `fillerService.countFillersBySlides()`
  - ✅ 공백 분석: `silenceDetectionService.detectSilencesBySlides()`
  - ✅ 정확도 분석: `scriptAccuracyService.analyzeAccuracyBySlides()`
  - ✅ **SPM 분석 추가**: `analyzeSlideSpm()` 메서드 구현
  - ✅ **반복 어휘 분석 추가**: `RepetitiveTextAnalysisService.analyzeRepetition()`에서 슬라이드별 결과 추출

- **SlideAnalysisResult 확장**
  - `spmResults`: 슬라이드별 SPM 결과 리스트
  - `repetitionResults`: 슬라이드별 반복 어휘 결과 리스트
  - `slideSttTexts`: 슬라이드별 STT 텍스트 리스트 ✅
  - `intervals`: 슬라이드 구간 정보 (타임스탬프 포함) ✅

- **SlideSpmResult 내부 클래스 추가**
  ```java
  - slideNumber: 슬라이드 번호
  - spm: 사용자 SPM
  - spmScore: SPM 점수 (0-100)
  ```

#### AnalysisResultService.java ✅
- `saveSlideAnalysis()` 메서드 업데이트
  - ✅ 슬라이드별 **타임스탬프 저장** (`timestampSeconds`)
  - ✅ 슬라이드별 **STT 텍스트 저장** (`slideText`)
  - ✅ 슬라이드별 **SPM 정보 저장**
    - SPM이 250 미만 또는 330 초과 시 SPEED 이슈로 저장
  - ✅ 슬라이드별 **필러 정보 저장**
  - ✅ 슬라이드별 **공백 정보 저장**
  - ✅ 슬라이드별 **반복 어휘 정보 저장**
    - 3회 이상 반복 시 REPETITION 이슈로 저장
    - 상위 3개 패턴만 상세 정보에 포함
  - ✅ 슬라이드별 **정확도 정보 저장**
  - 이슈가 있는 슬라이드만 저장 (최적화)

#### PracticeSessionService.java ✅
- `getSlideFeedbacks()`: 슬라이드별 피드백 조회
- `convertToDto()`: SlideFeedback 엔티티를 DTO로 변환
- `getFeedback()` 메서드 업데이트
  - 슬라이드별 피드백 포함
  - 전체 STT 텍스트 포함
  - 정확도 점수 포함

### 4. DTO 생성

#### SlideFeedbackDto.java ✅
```java
- slideNumber: 슬라이드 번호
- timestampSeconds: 슬라이드 시작 시각
- slideText: 해당 슬라이드 STT 텍스트
- issueType: 이슈 유형 (SPEED/FILLER/REPETITION/SILENCE/ACCURACY)
- 속도 관련: spmUser, spmAverage
- 필러 관련: fillerCount, fillerDetail
- 공백 관련: silenceCount, totalSilenceDuration, silenceScore
- 반복 관련: repeatCount, repeatDetail
- 정확도 관련: errorCount
- comment: 코멘트
```

#### PracticeFeedbackDto.java ✅
- `accuracyScore` 필드 추가
- `slideFeedbacks` 리스트 추가 (슬라이드별 피드백)
- `fullSttText` 필드 추가 (전체 STT 텍스트)

## API 응답 구조

### GET /practice/{sessionId}/feedback

```json
{
  "sessionId": 1,
  "feedbackId": 1,
  "spmScore": 85,
  "fillerScore": 90,
  "repeatScore": 95,
  "accuracyScore": 88,
  "totalScore": 89,
  "grade": "A",
  "silenceCount": 2,
  "totalSilenceDuration": 6.5,
  "silenceScore": 80,
  "slideFeedbacks": [
    {
      "slideNumber": 1,
      "timestampSeconds": 0.0,
      "slideText": "안녕하세요...",
      "issueType": "FILLER",
      "spmUser": 280,
      "spmAverage": 290,
      "fillerCount": 5,
      "fillerDetail": {
        "음": 2,
        "그": 3
      },
      "silenceCount": 1,
      "totalSilenceDuration": 3.2,
      "silenceScore": 90,
      "comment": null
    },
    {
      "slideNumber": 2,
      "timestampSeconds": 30.5,
      "slideText": "그러니까 그러니까...",
      "issueType": "REPETITION",
      "repeatCount": 5,
      "repeatDetail": "그러니까, 약간, 되게",
      "comment": null
    },
    {
      "slideNumber": 3,
      "timestampSeconds": 45.2,
      "slideText": "다음으로...",
      "issueType": "SPEED",
      "spmUser": 380,
      "spmAverage": 290,
      "comment": null
    }
  ],
  "fullSttText": "안녕하세요 오늘은... 그러니까... 다음으로...",
  "qnaComparison": null
}
```

## 분석 항목별 저장 기준

| 항목 | 저장 조건 | issueType |
|------|----------|-----------|
| **SPM** | SPM < 250 또는 SPM > 330 | SPEED |
| **필러** | 필러 개수 > 0 | FILLER |
| **공백** | 2.5초 이상 공백 있음 | SILENCE |
| **반복** | 반복 횟수 >= 3 | REPETITION |
| **정확도** | 정확도 < 80% | ACCURACY |

## ✅ 완료된 기능

1. ✅ SlideFeedback 저장 로직 구현
2. ✅ 슬라이드별 피드백 조회 기능
3. ✅ **슬라이드별 SPM 분석 및 저장**
4. ✅ **슬라이드별 반복 어휘 분석 및 저장**
5. ✅ 슬라이드별 필러, 공백, 정확도 분석 및 저장
6. ✅ 이슈가 있는 슬라이드만 선택적 저장 (최적화)
7. ✅ 전체 STT 텍스트 반환
8. ✅ 정확도 점수 포함
9. ✅ **슬라이드별 STT 텍스트 저장**
10. ✅ **슬라이드별 타임스탬프 저장**

## 🔨 추가 구현 가능 사항

### 1. 이전 피드백 비교 기능
요구사항: "이전 피드백 점수를 제공해줘야해"

새 DTO 생성:
```java
@Getter
@Builder
public class FeedbackComparisonDto {
    private PracticeFeedbackDto currentFeedback;
    private PracticeFeedbackDto previousFeedback;
    private ScoreChangeDto scoreChanges; // 점수 변화율
}

@Getter
@Builder
public class ScoreChangeDto {
    private Integer spmChange;        // +5, -3 등
    private Integer fillerChange;
    private Integer repeatChange;
    private Integer silenceChange;
    private Integer accuracyChange;
    private Integer totalChange;
}
```

구현:
```java
// PracticeSessionService에 메서드 추가
public FeedbackComparisonDto getFeedbackWithComparison(Long sessionId) {
    // 현재 피드백
    PracticeFeedbackDto current = getFeedback(sessionId);
    
    // 같은 프로젝트의 이전 세션 조회
    PracticeSession currentSession = practiceSessionRepository.findById(sessionId)...;
    List<PracticeSession> previousSessions = practiceSessionRepository
        .findByProjectOrderByPracticedAtDesc(currentSession.getProject());
    
    // 현재 세션 제외, 가장 최근 세션 가져오기
    PracticeFeedbackDto previous = null;
    if (previousSessions.size() > 1) {
        Long prevSessionId = previousSessions.get(1).getSessionId();
        previous = getFeedback(prevSessionId);
    }
    
    // 점수 변화 계산
    ScoreChangeDto changes = calculateScoreChanges(current, previous);
    
    return FeedbackComparisonDto.builder()
        .currentFeedback(current)
        .previousFeedback(previous)
        .scoreChanges(changes)
        .build();
}
```

### 3. 슬라이드별 코멘트 자동 생성
AI를 활용하여 각 슬라이드의 이슈에 대한 구체적인 코멘트 생성

```java
// 예시
"슬라이드 1: 필러워드 '음'이 2회, '그'가 3회 감지되었습니다. 발표 시 불필요한 단어 사용을 줄여보세요."
"슬라이드 2: 발표 속도가 380 SPM으로 너무 빠릅니다. 청중이 따라오기 어려울 수 있습니다."
```

## 데이터베이스 마이그레이션

Feedback 테이블에 컬럼 추가 필요:
```sql
ALTER TABLE feedback 
ADD COLUMN silence_count INT,
ADD COLUMN total_silence_duration DOUBLE,
ADD COLUMN silence_score INT,
ADD COLUMN silence_analysis_success BOOLEAN;
```

SlideFeedback 테이블은 이미 생성되어 있으므로 추가 작업 불필요.

## 주요 개선 사항

### 기존 문서의 오해 해소
- ❌ "슬라이드별 SPM 분석 - 현재는 전체 평균만" → ✅ **이제 슬라이드별로 분석 및 저장**
- ❌ "슬라이드별 반복 어휘 분석 - 현재는 전체 텍스트만" → ✅ **이미 슬라이드별 분석 기능 존재, 이제 저장까지 완료**
- ✅ 프론트에서 `startSec`, `endSec`을 받아서 슬라이드 구간을 이미 잘 나누고 있었음!

### 최적화
- 이슈가 있는 슬라이드만 저장하여 DB 용량 절약
- 반복 패턴은 상위 3개만 저장하여 응답 크기 최소화
- Map을 사용한 빠른 슬라이드별 데이터 매칭

## 테스트 체크리스트

- [x] SlideFeedback 저장 로직
- [x] 슬라이드별 피드백 조회
- [x] 슬라이드별 SPM 분석 및 저장
- [x] 슬라이드별 반복 분석 및 저장
- [x] 슬라이드별 STT 텍스트 저장
- [x] 슬라이드별 타임스탬프 저장
- [ ] 이전 피드백 비교 기능 테스트
- [ ] 전체 플로우 통합 테스트
