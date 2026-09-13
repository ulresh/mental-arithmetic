package com.github.ulresh.mental_arithmetic;

import java.util.Random;

/** An addition problem with two single-digit terms. */
final class Problem {
    static final int MIN_TERM = 1;
    static final int MAX_TERM = 9;

    final int first;
    final int second;

    Problem(int first, int second) {
        this.first = first;
        this.second = second;
    }

    /** Returns a random problem that differs from the previous one (which may be null). */
    static Problem random(Random random, Problem previous) {
        Problem problem;
        do {
            problem = new Problem(randomTerm(random), randomTerm(random));
        } while (problem.equals(previous));
        return problem;
    }

    private static int randomTerm(Random random) {
        return MIN_TERM + random.nextInt(MAX_TERM - MIN_TERM + 1);
    }

    int sum() {
        return first + second;
    }

    /** Text for the speech synthesizer, e.g. "3 плюс 5". */
    String speech() {
        return first + " плюс " + second;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Problem problem && problem.first == first && problem.second == second;
    }

    @Override
    public int hashCode() {
        return 31 * first + second;
    }

    @Override
    public String toString() {
        return first + "+" + second;
    }
}
