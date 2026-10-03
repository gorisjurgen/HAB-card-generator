package com.goris.habcardgenerator.service;

/**
 * Converts names that are written entirely in upper case (e.g. "JAN KERREMANS", "FAM. VAN TICHELEN - HENS")
 * to title case ("Jan Kerremans", "Fam. Van Tichelen - Hens"). Mixed-case names are left untouched.
 */
public final class NameFormatter {

    private NameFormatter() {
    }

    public static String titleCaseIfUpperCase(String name) {
        if (name == null || name.isEmpty() || !isAllUpperCase(name)) {
            return name;
        }
        StringBuilder result = new StringBuilder(name.length());
        boolean startOfWord = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c)) {
                result.append(startOfWord ? Character.toTitleCase(c) : Character.toLowerCase(c));
                startOfWord = false;
            } else {
                // Any non-letter (space, hyphen, apostrophe, period, digit) starts a new word
                result.append(c);
                startOfWord = true;
            }
        }
        return result.toString();
    }

    /** True when the text contains at least one letter and no lower case letters. */
    private static boolean isAllUpperCase(String text) {
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLowerCase(c)) {
                return false;
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
        }
        return hasLetter;
    }
}
