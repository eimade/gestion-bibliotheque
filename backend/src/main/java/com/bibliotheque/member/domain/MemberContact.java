package com.bibliotheque.member.domain;

/** Coordonnées postales et téléphone ({@code phone} facultatif). */
public record MemberContact(String phone, String street, String postalCode, String city) {
}
