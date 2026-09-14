package com.github.ulresh.mental_arithmetic;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Tests of the Android-independent logic; run with run-logic-tests.sh (JUnit is not available offline). */
public final class LogicTests {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        parsesDigitsAndWords();
        parsesRepeatCommand();
        ignoresSpeechWithoutNumbers();
        usesFirstMeaningfulHypothesis();
        generatesProblemsInRange();
        trainerJudgesAnswers();
        trainerSlowsDownOnRepeats();
        System.out.println("passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void parsesDigitsAndWords() {
        expectNumber("8", 8);
        expectNumber("Восемь", 8);
        expectNumber("восемь.", 8);
        expectNumber("18", 18);
        expectNumber("восемнадцать", 18);
        expectNumber("Семнадцать", 17);
        expectNumber("одиннадцать", 11);
        expectNumber("два", 2);
        expectNumber("двадцать", 20);
        expectNumber("двадцать один", 21);
        expectNumber("девяносто девять", 99);
        expectNumber("ноль", 0);
        expectNumber("в семь", 7);
        expectNumber("Ответ: 12.", 12);
        expectNumber("пять плюс три будет восемь", 8);
        expectNumber("3 + 5 = 8", 8);
        expectNumber("двадцать 5", 5);
    }

    private static void parsesRepeatCommand() {
        expectKind("повтори", AnswerParser.Kind.REPEAT);
        expectKind("Повтори.", AnswerParser.Kind.REPEAT);
        expectKind("повтори, пожалуйста", AnswerParser.Kind.REPEAT);
        expectKind("Повторите", AnswerParser.Kind.REPEAT);
        expectKind("повторить 5", AnswerParser.Kind.REPEAT);
    }

    private static void ignoresSpeechWithoutNumbers() {
        expectKind("", AnswerParser.Kind.NONE);
        expectKind((String) null, AnswerParser.Kind.NONE);
        expectKind("ёлки-палки", AnswerParser.Kind.NONE);
        expectKind("не знаю", AnswerParser.Kind.NONE);
        expectKind("123456789012345678901234567890", AnswerParser.Kind.NONE);
        check("null list", AnswerParser.parse((List<String>) null).kind == AnswerParser.Kind.NONE);
    }

    private static void usesFirstMeaningfulHypothesis() {
        AnswerParser.Answer answer = AnswerParser.parse(Arrays.asList("ну", "семь", "8"));
        check("first number hypothesis", answer.kind == AnswerParser.Kind.NUMBER && answer.number == 7);
        answer = AnswerParser.parse(Arrays.asList("что", "повтори"));
        check("repeat in later hypothesis", answer.kind == AnswerParser.Kind.REPEAT);
    }

    private static void generatesProblemsInRange() {
        Random random = new Random(1);
        boolean[] seen = new boolean[Problem.MAX_TERM + 1];
        Problem previous = null;
        boolean ok = true;
        for (int i = 0; i < 100_000; i++) {
            Problem problem = Problem.random(random, previous);
            ok &= problem.first >= 1 && problem.first <= 9 && problem.second >= 1 && problem.second <= 9;
            ok &= problem.sum() == problem.first + problem.second;
            ok &= !problem.equals(previous);
            seen[problem.first] = true;
            seen[problem.second] = true;
            previous = problem;
        }
        check("terms in 1..9, no immediate repeats", ok);
        for (int term = 1; term <= 9; term++) {
            check("term " + term + " occurs", seen[term]);
        }
        check("speech", new Problem(3, 5).speech().equals("3 плюс 5"));
    }

    private static void trainerJudgesAnswers() {
        Trainer trainer = new Trainer(new Random(7));
        for (int i = 0; i < 50; i++) {
            Problem problem = trainer.next();
            int sum = problem.sum();
            check("correct digits " + problem, reply(trainer, String.valueOf(sum)) == Trainer.Reply.CORRECT);
            check("wrong " + problem, reply(trainer, String.valueOf(sum + 1)) == Trainer.Reply.WRONG);
            check("repeat " + problem, reply(trainer, "повтори") == Trainer.Reply.REPEAT);
            check("silence " + problem, reply(trainer, "") == Trainer.Reply.NO_ANSWER);
            check("problem kept " + problem, trainer.current() == problem);
        }
        Trainer words = new Trainer(new Random(3));
        Problem problem = words.next();
        String[] names = {"", "", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
                "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать",
                "шестнадцать", "семнадцать", "восемнадцать"};
        check("correct word " + problem, reply(words, names[problem.sum()]) == Trainer.Reply.CORRECT);
    }

    private static void trainerSlowsDownOnRepeats() {
        Trainer trainer = new Trainer(new Random(5));
        Problem problem = trainer.next();
        expectRate("new problem", trainer, 1.0f);
        float[] expected = {0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.5f, 0.5f};
        for (int i = 0; i < expected.length; i++) {
            reply(trainer, "повтори");
            expectRate("repeat " + (i + 1), trainer, expected[i]);
        }
        reply(trainer, String.valueOf(problem.sum() + 1));
        reply(trainer, "");
        expectRate("wrong answer and silence keep the rate", trainer, 0.5f);
        check("correct answer", reply(trainer, String.valueOf(problem.sum())) == Trainer.Reply.CORRECT);
        trainer.next();
        expectRate("next problem after repeats", trainer, 1.0f);
        reply(trainer, "повтори");
        expectRate("repeats counted anew", trainer, 0.9f);
    }

    private static void expectRate(String name, Trainer trainer, float rate) {
        check(name + ": rate " + rate + " (got " + trainer.speechRate() + ")",
                Math.abs(trainer.speechRate() - rate) < 1e-6);
    }

    private static Trainer.Reply reply(Trainer trainer, String text) {
        return trainer.onAnswer(List.of(text));
    }

    private static void expectNumber(String text, int number) {
        AnswerParser.Answer answer = AnswerParser.parse(text);
        check("\"" + text + "\" -> " + number + " (got " + answer + ")",
                answer.kind == AnswerParser.Kind.NUMBER && answer.number == number);
    }

    private static void expectKind(String text, AnswerParser.Kind kind) {
        AnswerParser.Answer answer = AnswerParser.parse(text);
        check("\"" + text + "\" -> " + kind + " (got " + answer + ")", answer.kind == kind);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }
}
