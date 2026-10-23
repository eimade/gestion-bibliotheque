package com.bibliotheque.catalog.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bibliotheque.catalog.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Comparaison par la collation de la base : insensible à la casse et aux accents (RG-12). */
    boolean existsByLabel(String label);

    boolean existsByLabelAndIdNot(String label, Long id);

    List<Category> findAllByOrderByLabelAsc();

    @Query("select count(b) from Book b join b.categories c where c.id = :categoryId")
    long countBooks(@Param("categoryId") Long categoryId);

    @Query(value = """
            select new com.bibliotheque.catalog.repository.CategoryUsage(c,
                   (select count(b) from Book b join b.categories bc where bc = c))
            from Category c
            """, countQuery = "select count(c) from Category c")
    Page<CategoryUsage> findAllWithUsage(Pageable pageable);
}
