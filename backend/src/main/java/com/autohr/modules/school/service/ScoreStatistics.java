package com.autohr.modules.school.service;

import java.util.*;
import java.util.stream.Collectors;

/** Pure calculations on finished attempts; every answer is graded on the existing 0–100 scale. */
final class ScoreStatistics {
    record Sample(Map<String, Object> student, List<Map<String, Object>> answers) {
        boolean valid() {
            return !answers.isEmpty() && answers.stream().allMatch(a -> a.get("averageScore") instanceof Number n
                    && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 100);
        }
        double score() { return answers.stream().mapToDouble(a -> number(a.get("averageScore"))).average().orElse(0); }
        String exam() { return String.valueOf(student.get("examId")); }
    }

    static Map<String, Object> calculate(List<Sample> finished) {
        List<Sample> samples = finished.stream().filter(Sample::valid).toList();
        double[] scores = samples.stream().mapToDouble(Sample::score).sorted().toArray();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sampleCount", scores.length);
        result.put("excludedCount", finished.size() - scores.length);
        result.put("description", describe(scores));
        result.put("rates", rates(samples));
        result.put("standardScores", standardScores(samples));
        result.put("knowledge", knowledge(samples));
        result.put("ctt", ctt(samples));
        return result;
    }

    static Map<String, Object> describe(double[] values) {
        double[] x = values.clone();
        Arrays.sort(x);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : List.of("mean", "median", "variance", "standardDeviation", "minimum", "maximum",
                "range", "q1", "q3", "iqr", "lowerWhisker", "upperWhisker", "skewness", "excessKurtosis")) out.put(key, null);
        out.put("modes", List.of());
        out.put("outliers", List.of());
        if (x.length == 0) return out;
        double mean = mean(x), variance = variance(x), sd = Math.sqrt(variance);
        double q1 = quantile(x, .25), q3 = quantile(x, .75), iqr = q3 - q1;
        double low = q1 - 1.5 * iqr, high = q3 + 1.5 * iqr;
        out.put("mean", rounded(mean)); out.put("median", rounded(quantile(x, .5)));
        out.put("variance", rounded(variance)); out.put("standardDeviation", rounded(sd));
        out.put("minimum", rounded(x[0])); out.put("maximum", rounded(x[x.length - 1]));
        out.put("range", rounded(x[x.length - 1] - x[0]));
        out.put("q1", rounded(q1)); out.put("q3", rounded(q3)); out.put("iqr", rounded(iqr));
        out.put("lowerWhisker", rounded(Arrays.stream(x).filter(v -> v >= low).min().orElse(x[0])));
        out.put("upperWhisker", rounded(Arrays.stream(x).filter(v -> v <= high).max().orElse(x[x.length - 1])));
        out.put("outliers", Arrays.stream(x).filter(v -> v < low || v > high).mapToObj(ScoreStatistics::rounded).toList());
        Map<Double, Integer> frequency = new TreeMap<>();
        for (double v : x) frequency.merge(v, 1, Integer::sum);
        int max = Collections.max(frequency.values());
        // Continuous averages are not rounded before counting; all unique values have no mode.
        if (max > 1) out.put("modes", frequency.entrySet().stream().filter(e -> e.getValue() == max).map(e -> rounded(e.getKey())).toList());
        if (sd > 0 && x.length >= 3) out.put("skewness", rounded(Arrays.stream(x).map(v -> Math.pow((v - mean) / sd, 3)).average().orElse(0)));
        if (sd > 0 && x.length >= 4) out.put("excessKurtosis", rounded(Arrays.stream(x).map(v -> Math.pow((v - mean) / sd, 4)).average().orElse(0) - 3));
        return out;
    }

    private static Map<String, Object> rates(List<Sample> s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pass", percentage(s.stream().filter(v -> v.score() >= number(v.student.getOrDefault("passingScore", 60))).count(), s.size()));
        out.put("excellent", percentage(s.stream().filter(v -> v.score() >= 90).count(), s.size()));
        out.put("low", percentage(s.stream().filter(v -> v.score() < 40).count(), s.size()));
        out.put("mastery", percentage(s.stream().filter(v -> v.score() >= 80).count(), s.size()));
        List<Map<String, Object>> grades = new ArrayList<>();
        String[] labels = {"A（90–100）", "B（80–<90）", "C（60–<80）", "D（<60）"};
        double[] lower = {90, 80, 60, 0}, upper = {101, 90, 80, 60};
        for (int i = 0; i < labels.length; i++) {
            double l = lower[i], u = upper[i];
            long count = s.stream().filter(v -> v.score() >= l && v.score() < u).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grade", labels[i]); row.put("count", count); row.put("rate", percentage(count, s.size())); grades.add(row);
        }
        out.put("grades", grades);
        return out;
    }

    private static List<Map<String, Object>> standardScores(List<Sample> samples) {
        Map<String, List<Sample>> exams = samples.stream().collect(Collectors.groupingBy(Sample::exam, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (List<Sample> cohort : exams.values()) {
            double[] scores = cohort.stream().mapToDouble(Sample::score).toArray();
            double mean = mean(scores), sd = Math.sqrt(variance(scores));
            for (Sample s : cohort) {
                Map<String, Object> row = identity(s);
                double score = s.score();
                row.put("score", rounded(score));
                row.put("z", sd == 0 ? null : rounded((score - mean) / sd));
                row.put("t", sd == 0 ? null : rounded(50 + 10 * (score - mean) / sd));
                row.put("pr", percentage(Arrays.stream(scores).filter(v -> v < score).count()
                        + .5 * Arrays.stream(scores).filter(v -> v == score).count(), scores.length));
                row.put("group", score >= 80 ? "高分组" : score >= 60 ? "中分组" : "低分组");
                row.put("cohortSize", cohort.size()); rows.add(row);
            }
        }
        return rows;
    }

    private static Map<String, Object> knowledge(List<Sample> samples) {
        Map<String, List<Double>> aggregate = new TreeMap<>();
        List<Map<String, Object>> cells = new ArrayList<>();
        for (Sample s : samples) {
            Map<String, List<Map<String, Object>>> points = s.answers.stream().collect(Collectors.groupingBy(a -> String.valueOf(a.get("knowledgePoint")), TreeMap::new, Collectors.toList()));
            for (var entry : points.entrySet()) {
                double score = entry.getValue().stream().mapToDouble(a -> number(a.get("averageScore"))).average().orElse(0);
                aggregate.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(score);
                Map<String, Object> cell = identity(s);
                cell.put("knowledgePoint", entry.getKey()); cell.put("scoreRate", rounded(score)); cell.put("rounds", entry.getValue().size()); cells.add(cell);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        aggregate.forEach((name, scores) -> rows.add(Map.of("knowledgePoint", name, "scoreRate", rounded(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                "sampleCount", scores.size(), "masteryRate", percentage(scores.stream().filter(v -> v >= 80).count(), scores.size()))));
        rows.sort(Comparator.comparingDouble(row -> number(row.get("scoreRate"))));
        return Map.of("points", rows, "cells", cells);
    }

    private static List<Map<String, Object>> ctt(List<Sample> samples) {
        List<Map<String, Object>> exams = new ArrayList<>();
        for (List<Sample> cohort : samples.stream().collect(Collectors.groupingBy(Sample::exam, LinkedHashMap::new, Collectors.toList())).values()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("examName", cohort.get(0).student.get("examName"));
            result.put("sampleCount", cohort.size()); result.put("alpha", null); result.put("sem", null); result.put("splitHalf", null);
            List<Map<String, Double>> matrix = new ArrayList<>();
            Map<String, String> names = new LinkedHashMap<>();
            boolean repeated = false;
            for (Sample s : cohort) {
                Map<String, Double> row = new LinkedHashMap<>();
                for (var answer : s.answers) {
                    String question = String.valueOf(answer.get("questionContent"));
                    String key = String.valueOf(answer.get("knowledgePoint")) + "\u0000" + question;
                    if (row.put(key, number(answer.get("averageScore"))) != null) repeated = true;
                    names.put(key, question);
                }
                matrix.add(row);
            }
            Set<String> common = new LinkedHashSet<>(matrix.get(0).keySet());
            matrix.forEach(row -> common.retainAll(row.keySet()));
            boolean samePaper = !repeated && !common.isEmpty() && matrix.stream().allMatch(row -> row.keySet().equals(common));
            List<Map<String, Object>> items = new ArrayList<>();
            // Only common items in a fully identical paper support comparable total scores.
            if (samePaper && cohort.size() >= 2) {
                List<String> keys = new ArrayList<>(common);
                double[] totals = matrix.stream().mapToDouble(row -> row.values().stream().mapToDouble(Double::doubleValue).sum()).toArray();
                Integer[] order = new Integer[totals.length];
                for (int i = 0; i < order.length; i++) order[i] = i;
                Arrays.sort(order, Comparator.comparingDouble(i -> totals[i]));
                int group = Math.max(1, (int) Math.floor(totals.length * .27));
                boolean tiedBoundary = totals[order[group - 1]] == totals[order[group]]
                        || totals[order[order.length - group - 1]] == totals[order[order.length - group]];
                double itemVariance = 0;
                double[] odd = new double[totals.length], even = new double[totals.length];
                for (int j = 0; j < keys.size(); j++) {
                    String key = keys.get(j);
                    double[] x = matrix.stream().mapToDouble(row -> row.get(key)).toArray();
                    itemVariance += variance(x);
                    double[] rest = new double[x.length];
                    for (int i = 0; i < x.length; i++) { rest[i] = totals[i] - x[i]; if (j % 2 == 0) odd[i] += x[i]; else even[i] += x[i]; }
                    double lo = 0, hi = 0;
                    for (int i = 0; i < group; i++) { lo += x[order[i]]; hi += x[order[order.length - 1 - i]]; }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("question", names.get(key)); item.put("p", rounded(mean(x) / 100));
                    item.put("d", totals.length < 4 || tiedBoundary ? null : rounded((hi - lo) / group / 100));
                    item.put("correlation", correlation(x, rest)); items.add(item);
                }
                if (keys.size() >= 2 && variance(totals) > 0) {
                    double alpha = (double) keys.size() / (keys.size() - 1) * (1 - itemVariance / variance(totals));
                    result.put("alpha", rounded(alpha));
                    if (alpha >= 0 && alpha <= 1) result.put("sem", rounded(Math.sqrt(variance(totals)) / keys.size() * Math.sqrt(1 - alpha)));
                    Double r = correlation(odd, even);
                    if (r != null && r > -1) result.put("splitHalf", rounded(2 * r / (1 + r)));
                }
            }
            result.put("itemCount", common.size()); result.put("items", items);
            result.put("reason", !samePaper ? "题目集合不同或有重复题目，不能将动态题目按轮次视为同一道题。" : cohort.size() < 2 ? "至少需要两份具有相同有效已评分题目集合的答题记录。" : "同一考试、相同有效已评分题目及知识点集合；缺少变异或题目数不足的指标留空。小样本仅供探索。题目相关采用校正 Pearson 相关，非二分题点二列相关。");
            exams.add(result);
        }
        return exams;
    }

    private static Map<String, Object> identity(Sample s) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String key : List.of("processId", "studentNo", "fullName", "examName", "className")) row.put(key, s.student.get(key));
        return row;
    }
    private static double number(Object v) { return v instanceof Number n ? n.doubleValue() : 0; }
    private static double mean(double[] x) { return Arrays.stream(x).average().orElse(0); }
    private static double variance(double[] x) { double m = mean(x); return Arrays.stream(x).map(v -> (v - m) * (v - m)).average().orElse(0); }
    private static double quantile(double[] x, double p) { double i = (x.length - 1) * p; int lo = (int) i; return x[lo] + (x[Math.min(lo + 1, x.length - 1)] - x[lo]) * (i - lo); }
    private static Double percentage(double count, int n) { return n == 0 ? null : rounded(count * 100 / n); }
    private static double rounded(double v) { return Math.round(v * 10000d) / 10000d; }
    private static Double correlation(double[] x, double[] y) {
        double vx = variance(x), vy = variance(y);
        if (x.length < 2 || vx == 0 || vy == 0) return null;
        double mx = mean(x), my = mean(y), sum = 0;
        for (int i = 0; i < x.length; i++) sum += (x[i] - mx) * (y[i] - my);
        return Math.max(-1, Math.min(1, sum / x.length / Math.sqrt(vx * vy)));
    }
}
