package com.goris.habcardgenerator.model;

public record CardData(
    String memberId,
    String name,
    String street,
    String streetNumber,
    String bus
) {
}
