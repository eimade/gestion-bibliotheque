package com.bibliotheque.catalog.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bibliotheque.catalog.domain.Author;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    /** Suggestions par nom, prénom ou « Prénom Nom » ; {@code pattern} vient de {@code LikePatterns.contains}. */
    @Query("""
            select a from Author a
            where a.lastName like :pattern escape '!'
               or a.firstName like :pattern escape '!'
               or concat(coalesce(a.firstName, ''), ' ', a.lastName) like :pattern escape '!'
            order by a.lastName, a.firstName
            """)
    List<Author> suggest(@Param("pattern") String pattern, Pageable limit);
}
