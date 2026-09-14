package com.github.ulresh.mental_arithmetic;

import java.util.List;
import java.util.Random;

/** Game rules, independent of Android: current problem and reaction to what the user said. */
final class Trainer {
    enum Reply { CORRECT, WRONG, REPEAT, NO_ANSWER }

    /** Speech rates in tenths of the normal rate: each repeat is 0.1 slower, down to 0.5. */
    private static final int NORMAL_RATE_TENTHS = 10;
    private static final int MIN_RATE_TENTHS = 5;

    private final Random random;
    private Problem current;
    private int repeats;

    Trainer(Random random) {
        this.random = random;
    }

    Problem next() {
        current = Problem.random(random, current);
        repeats = 0;
        return current;
    }

    Problem current() {
        return current;
    }

    /** Rate for speaking the current problem: 1 for a new one, slower after each "повтори". */
    float speechRate() {
        return Math.max(MIN_RATE_TENTHS, NORMAL_RATE_TENTHS - repeats) / 10f;
    }

    Reply onAnswer(List<String> hypotheses) {
        AnswerParser.Answer answer = AnswerParser.parse(hypotheses);
        return switch (answer.kind) {
            case REPEAT -> {
                repeats++;
                yield Reply.REPEAT;
            }
            case NUMBER -> answer.number == current.sum() ? Reply.CORRECT : Reply.WRONG;
            case NONE -> Reply.NO_ANSWER;
        };
    }
}
