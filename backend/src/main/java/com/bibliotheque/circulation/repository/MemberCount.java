package com.bibliotheque.circulation.repository;

/** Projection « nombre par adhérent » (obligations RG-18 de plusieurs adhérents en une requête). */
public interface MemberCount {

    Long getMemberId();

    long getTotal();
}
