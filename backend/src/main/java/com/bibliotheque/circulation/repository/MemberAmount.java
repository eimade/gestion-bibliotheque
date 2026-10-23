package com.bibliotheque.circulation.repository;

import java.math.BigDecimal;

/** Projection « montant impayé par adhérent » (RG-18, RG-35). */
public interface MemberAmount {

    Long getMemberId();

    BigDecimal getTotal();
}
