package com.bibliotheque.member.service;

import com.bibliotheque.member.domain.MemberContact;
import com.bibliotheque.member.domain.MemberIdentity;

/** Données d'inscription ou de modification d'un adhérent, déjà validées par le DTO. */
public record MemberCommand(MemberIdentity identity, MemberContact contact, String email) {
}
