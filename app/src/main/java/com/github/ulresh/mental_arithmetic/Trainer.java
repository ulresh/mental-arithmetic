package com.github.ulresh.mental_arithmetic;

import java.util.List;
import java.util.Random;

/** Game rules, independent of Android: current problem and reaction to what the user said. */
final class Trainer {
    enum Reply { CORRECT, WRONG, REPEAT, NO_ANSWER }

    private final Random random;
    private Problem current;

    Trainer(Random random) {
        this.random = random;
    }

    Problem next() {
        current = Problem.random(random, current);
        return current;
    }

    Problem current() {
        return current;
    }

    Reply onAnswer(List<String> hypotheses) {
        AnswerParser.Answer answer = AnswerParser.parse(hypotheses);
        return switch (answer.kind) {
            case REPEAT -> Reply.REPEAT;
            case NUMBER -> answer.number == current.sum() ? Reply.CORRECT : Reply.WRONG;
            case NONE -> Reply.NO_ANSWER;
        };
    }
}
