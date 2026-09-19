package com.kosscchthon.Icelink.participant;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** P-02: 닉네임 중복 시 "민수2", "민수3" … 형태로 비어 있는 첫 후보를 제안한다. */
final class NicknameSuggester {

    private static final int MAX_SUFFIX = 99;

    private NicknameSuggester() {
    }

    static String suggest(String base, Collection<String> takenLower) {
        Set<String> taken = new HashSet<>(takenLower);
        for (int n = 2; n <= MAX_SUFFIX; n++) {
            String suffix = Integer.toString(n);
            int keep = Math.min(base.length(), Participant.NICKNAME_MAX_LENGTH - suffix.length());
            String candidate = base.substring(0, keep) + suffix;
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }
}
