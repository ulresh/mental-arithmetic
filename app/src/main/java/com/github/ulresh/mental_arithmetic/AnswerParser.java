package com.github.ulresh.mental_arithmetic;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interprets Russian speech recognition hypotheses: a number or the "повтори" command.
 * Short number words are often misrecognized, so besides the exact words it accepts typical
 * mishearings and long words that differ from a number word by a single letter.
 */
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

    /** Shorter words are not matched approximately: "есть" must not become "шесть". */
    private static final int APPROXIMATE_MIN_LENGTH = 5;

    private static final Map<String, Integer> WORDS = words(new String[][]{
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
    });

    /** What the recognizer returns instead of short number words, and their other case forms. */
    private static final Map<String, Integer> MISHEARD = words(new String[][]{
            {"2", "двух"},
            {"3", "трех", "при"},
            {"5", "пяти", "пят", "опять"},
            {"6", "шест", "шести", "жесть", "честь", "шерсть"},
            {"7", "семи", "сем", "сэм", "всем", "съем", "семья"},
            {"8", "восьми", "восем", "осень"},
    });

    private AnswerParser() {
    }

    private static Map<String, Integer> words(String[][] rows) {
        Map<String, Integer> words = new HashMap<>();
        for (String[] row : rows) {
            for (int i = 1; i < row.length; i++) {
                words.put(row[i], Integer.parseInt(row[0]));
            }
        }
        return words;
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
            Integer value = wordValue(token);
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

    private static Integer wordValue(String token) {
        Integer value = WORDS.get(token);
        if (value == null) {
            value = MISHEARD.get(token);
        }
        if (value == null) {
            value = approximateWordValue(token);
        }
        return value;
    }

    /** Value of the number word one edit away from the token, or null if there is none or several. */
    private static Integer approximateWordValue(String token) {
        if (token.length() < APPROXIMATE_MIN_LENGTH) {
            return null;
        }
        Integer found = null;
        for (Map.Entry<String, Integer> entry : WORDS.entrySet()) {
            String word = entry.getKey();
            if (word.length() >= APPROXIMATE_MIN_LENGTH
                    && Math.abs(word.length() - token.length()) <= 1
                    && editDistance(token, word) <= 1) {
                if (found != null && !found.equals(entry.getValue())) {
                    return null; // Ambiguous, e.g. "деять" is one edit from both "девять" and "десять".
                }
                found = entry.getValue();
            }
        }
        return found;
    }

    /** Levenshtein distance: the number of inserted, deleted and replaced letters. */
    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int replace = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(replace, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
