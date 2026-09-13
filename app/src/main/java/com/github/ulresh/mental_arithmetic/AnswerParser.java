package com.github.ulresh.mental_arithmetic;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Interprets Russian speech recognition hypotheses: a number or the "повтори" command. */
final class AnswerParser {
    enum Kind { NUMBER, REPEAT, NONE }

    static final class Answer {
        static final Answer NONE = new Answer(Kind.NONE, 0);
        static final Answer REPEAT = new Answer(Kind.REPEAT, 0);

        final Kind kind;
        final int number;

        private Answer(Kind kind, int number) {
            this.kind = kind;
            this.number = number;
        }

        static Answer number(int number) {
            return new Answer(Kind.NUMBER, number);
        }

        @Override
        public String toString() {
            return kind == Kind.NUMBER ? String.valueOf(number) : kind.name();
        }
    }

    private static final Map<String, Integer> WORDS = new HashMap<>();

    static {
        String[][] words = {
                {"0", "ноль", "нуль"},
                {"1", "один", "одна", "одно"},
                {"2", "два", "две"},
                {"3", "три"},
                {"4", "четыре"},
                {"5", "пять"},
                {"6", "шесть"},
                {"7", "семь"},
                {"8", "восемь"},
                {"9", "девять"},
                {"10", "десять"},
                {"11", "одиннадцать"},
                {"12", "двенадцать"},
                {"13", "тринадцать"},
                {"14", "четырнадцать"},
                {"15", "пятнадцать"},
                {"16", "шестнадцать"},
                {"17", "семнадцать"},
                {"18", "восемнадцать"},
                {"19", "девятнадцать"},
                {"20", "двадцать"},
                {"30", "тридцать"},
                {"40", "сорок"},
                {"50", "пятьдесят"},
                {"60", "шестьдесят"},
                {"70", "семьдесят"},
                {"80", "восемьдесят"},
                {"90", "девяносто"},
        };
        for (String[] row : words) {
            for (int i = 1; i < row.length; i++) {
                WORDS.put(row[i], Integer.parseInt(row[0]));
            }
        }
    }

    private AnswerParser() {
    }

    /** Hypotheses are ordered from the most to the least likely; the first meaningful one wins. */
    static Answer parse(List<String> hypotheses) {
        if (hypotheses == null) {
            return Answer.NONE;
        }
        for (String hypothesis : hypotheses) {
            Answer answer = parse(hypothesis);
            if (answer.kind != Kind.NONE) {
                return answer;
            }
        }
        return Answer.NONE;
    }

    /** "Повтори" anywhere means repeat; otherwise the last number said is the answer. */
    static Answer parse(String text) {
        if (text == null) {
            return Answer.NONE;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        Integer last = null;
        int tens = -1; // Value of the preceding token if it was "двадцать", "тридцать"...
        for (String token : normalized.split("[^\\p{L}\\p{Nd}]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("повтор")) {
                return Answer.REPEAT;
            }
            if (token.chars().allMatch(Character::isDigit)) {
                last = token.length() <= 4 ? Integer.parseInt(token) : null;
                tens = -1;
                continue;
            }
            Integer value = WORDS.get(token);
            if (value == null) {
                tens = -1;
            } else if (tens > 0 && value >= 1 && value <= 9) {
                last = tens + value;
                tens = -1;
            } else {
                last = value;
                tens = value >= 20 && value % 10 == 0 ? value : -1;
            }
        }
        return last == null ? Answer.NONE : Answer.number(last);
    }
}
