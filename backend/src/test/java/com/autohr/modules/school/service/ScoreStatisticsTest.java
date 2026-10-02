package com.autohr.modules.school.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScoreStatisticsTest {
    @Test void descriptiveStatisticsUsePopulationMomentsAndInterpolatedQuartiles() {
        Map<String, Object> d = ScoreStatistics.describe(new double[]{100, 60, 60, 80});
        assertEquals(75d, d.get("mean"));
        assertEquals(70d, d.get("median"));
        assertEquals(275d, d.get("variance"));
        assertEquals(60d, d.get("q1"));
        assertEquals(85d, d.get("q3"));
        assertEquals(List.of(60d), d.get("modes"));
        assertEquals(40d, d.get("range"));
        assertEquals(List.of(100d), ScoreStatistics.describe(new double[]{10, 11, 12, 13, 100}).get("outliers"));
    }

    @Test void emptyAndConstantSamplesDoNotInventStatistics() {
        assertNull(ScoreStatistics.describe(new double[0]).get("mean"));
        Map<String, Object> stats = ScoreStatistics.calculate(List.of(sample(1, 60, 60), sample(1, 60, 60)));
        assertNull(map(stats.get("description")).get("skewness"));
        assertNull(rows(stats.get("standardScores")).get(0).get("z"));
        assertEquals(50d, rows(stats.get("standardScores")).get(0).get("pr"));
        assertNull(rows(stats.get("ctt")).get(0).get("alpha"));
        assertNull(map(ScoreStatistics.calculate(List.of()).get("rates")).get("pass"));
    }

    @Test void ratesUseExamPassingScoresAndExactBoundaries() {
        Map<String, Object> stats = ScoreStatistics.calculate(List.of(sample(1, 90), sample(1, 80), sample(1, 60), sample(1, 40), sample(1, 39)));
        Map<String, Object> rates = map(stats.get("rates"));
        assertEquals(40d, rates.get("pass")); // configured pass threshold is 80
        assertEquals(20d, rates.get("excellent"));
        assertEquals(20d, rates.get("low"));
        assertEquals(40d, rates.get("mastery"));
        assertEquals(List.of(1L, 1L, 1L, 2L), rows(rates.get("grades")).stream().map(r -> r.get("count")).toList());
    }

    @Test void standardScoresAreWithinExamAndKnowledgeUsesPerStudentMeans() {
        Map<String, Object> stats = ScoreStatistics.calculate(List.of(sample(1, 40, 80), sample(1, 100), sample(2, 20)));
        List<Map<String, Object>> standard = rows(stats.get("standardScores"));
        assertEquals(-1d, standard.get(0).get("z"));
        assertEquals(25d, standard.get(0).get("pr"));
        assertNull(standard.get(2).get("z"));
        assertEquals(60d, rows(map(stats.get("knowledge")).get("points")).get(0).get("scoreRate"));
        assertEquals(33.3333d, rows(map(stats.get("knowledge")).get("points")).get(0).get("masteryRate"));
    }

    @Test void samePaperProducesReliabilityAndDifferentQuestionsDoNot() {
        Map<String, Object> stats = ScoreStatistics.calculate(List.of(sample(1, 0, 20), sample(1, 20, 40), sample(1, 60, 80), sample(1, 80, 100)));
        Map<String, Object> ctt = rows(stats.get("ctt")).get(0);
        assertEquals(1d, ctt.get("alpha"));
        assertEquals(0d, ctt.get("sem"));
        assertEquals(1d, ctt.get("splitHalf"));
        assertEquals(.4d, rows(ctt.get("items")).get(0).get("p"));
        assertEquals(.8d, rows(ctt.get("items")).get(0).get("d"));
        assertEquals(1d, rows(ctt.get("items")).get(0).get("correlation"));
        Map<String, Object> dynamic = rows(ScoreStatistics.calculate(List.of(sample(1, 20), sample(1, 20, 80))).get("ctt")).get(0);
        assertNull(dynamic.get("alpha"));
        assertTrue(rows(dynamic.get("items")).isEmpty());
    }

    @Test void invalidScoresAndUnscoredFinishedRecordsAreExcluded() {
        ScoreStatistics.Sample bad = new ScoreStatistics.Sample(Map.of("examId", 1), List.of(Map.of("knowledgePoint", "K", "questionContent", "Q")));
        Map<String, Object> stats = ScoreStatistics.calculate(List.of(bad, sample(1), sample(1, 101), sample(1, 60)));
        assertEquals(1, stats.get("sampleCount")); assertEquals(3, stats.get("excludedCount"));
    }

    @Test void tiedGroupBoundariesDoNotDependOnAttemptOrder() {
        Map<String, Object> ctt = rows(ScoreStatistics.calculate(List.of(sample(1, 10, 10), sample(1, 10, 10), sample(1, 80, 80), sample(1, 100, 100))).get("ctt")).get(0);
        assertNull(rows(ctt.get("items")).get(0).get("d"));
    }

    private static ScoreStatistics.Sample sample(int exam, double... scores) {
        List<Map<String, Object>> answers = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) answers.add(Map.of("questionContent", "Question " + i, "knowledgePoint", "K", "averageScore", scores[i]));
        return new ScoreStatistics.Sample(Map.of("examId", exam, "examName", "Exam " + exam, "passingScore", 80, "processId", UUID.randomUUID().toString()), answers);
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> rows(Object value) { return (List<Map<String, Object>>) value; }
}
