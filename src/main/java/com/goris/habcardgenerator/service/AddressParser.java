package com.goris.habcardgenerator.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a single address cell (as exported by Assistonline, e.g. "De Romboutweg  39 ",
 * "Langestraat 1B", "Rustoordlei 75-77", "Bredabaan 12 bus 3") into street, house number and bus.
 */
public final class AddressParser {

    public record ParsedAddress(String street, String streetNumber, String bus) {
        public boolean hasNumber() {
            return !streetNumber.isEmpty();
        }
    }

    /** Any run of whitespace, including non-breaking spaces that Excel exports often contain. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    /**
     * street = everything up to the first whitespace followed by digits,
     * number = digits with an optional attached letter and an optional range ("1B", "142b", "75-77"),
     * rest   = empty, or a remainder that does not start with a letter (so "12bis" is not split into "12b" + "is").
     */
    private static final Pattern ADDRESS = Pattern.compile(
            "^(?<street>\\S.*?)\\s+(?<number>\\d+[A-Za-z]?(?:-\\d+[A-Za-z]?)?)(?<rest>|[^A-Za-z].*)$");

    private static final Pattern BUS_PREFIX = Pattern.compile("^(?:bus\\b|/)\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern LEADING_NUMBER = Pattern.compile("\\d+");

    private AddressParser() {
    }

    /** Strips the text and collapses every run of whitespace to a single space. {@code null} becomes "". */
    public static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /**
     * Parses an address. When no house number can be found the whole (normalised) text is returned
     * as the street and the number and bus are empty.
     */
    public static ParsedAddress parse(String rawAddress) {
        String address = normalizeWhitespace(rawAddress);
        Matcher matcher = ADDRESS.matcher(address);
        if (!matcher.matches()) {
            return new ParsedAddress(address, "", "");
        }
        return new ParsedAddress(matcher.group("street"), matcher.group("number"), parseBus(matcher.group("rest")));
    }

    private static String parseBus(String rest) {
        String remainder = rest.strip();
        if (remainder.isEmpty()) {
            return "";
        }
        Matcher prefix = BUS_PREFIX.matcher(remainder);
        if (prefix.find()) {
            String value = remainder.substring(prefix.end()).strip();
            return value.isEmpty() ? "" : "bus " + value;
        }
        return remainder;
    }

    /**
     * Numeric value of a house number used for sorting and the odd/even split: the leading digit group
     * ("75-77" -> 75, "1B" -> 1). Empty or non-numeric values return {@link Integer#MAX_VALUE} so they
     * sort last and count as odd.
     */
    public static int houseNumberValue(String streetNumber) {
        if (streetNumber == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = LEADING_NUMBER.matcher(streetNumber);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
