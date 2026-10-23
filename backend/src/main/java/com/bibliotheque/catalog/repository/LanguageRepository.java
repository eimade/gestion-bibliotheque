package com.bibliotheque.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bibliotheque.catalog.domain.Language;

public interface LanguageRepository extends JpaRepository<Language, String> {

    List<Language> findAllByOrderByDisplayOrderAsc();
}
