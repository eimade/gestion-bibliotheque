package com.bibliotheque.catalog;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.BookDescription;
import com.bibliotheque.catalog.domain.Category;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.domain.Language;
import com.bibliotheque.catalog.domain.PhysicalCondition;

/** Données de test du catalogue (identifiants posés par réflexion, comme après une sauvegarde). */
public final class CatalogFixtures {

    private CatalogFixtures() {
    }

    public static Language french() {
        Language language = instantiate(Language.class);
        ReflectionTestUtils.setField(language, "code", "fre");
        ReflectionTestUtils.setField(language, "label", "Français");
        return language;
    }

    public static Author author(long id, String lastName, String firstName) {
        Author author = new Author(lastName, firstName);
        ReflectionTestUtils.setField(author, "id", id);
        return author;
    }

    public static Category category(long id, String label) {
        Category category = new Category(label);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "version", 0);
        return category;
    }

    public static Book book(long id, String title) {
        Book book = new Book(new BookDescription(null, title, null, null, 1942, null, null), french(),
                List.of(author(1L, "Camus", "Albert")), Set.of(category(1L, "Roman")));
        ReflectionTestUtils.setField(book, "id", id);
        ReflectionTestUtils.setField(book, "version", 0);
        return book;
    }

    public static Copy copy(long id, String copyNumber, Book book, CopyStatus status) {
        Copy copy = new Copy(copyNumber, book, "R CAM", PhysicalCondition.GOOD, LocalDate.of(2024, 1, 2));
        ReflectionTestUtils.setField(copy, "id", id);
        ReflectionTestUtils.setField(copy, "version", 0);
        ReflectionTestUtils.setField(copy, "status", status);
        return copy;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
