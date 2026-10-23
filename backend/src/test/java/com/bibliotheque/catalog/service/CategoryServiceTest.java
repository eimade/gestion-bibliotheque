package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.CatalogFixtures;
import com.bibliotheque.catalog.domain.Category;
import com.bibliotheque.catalog.repository.CategoryRepository;
import com.bibliotheque.catalog.repository.CategoryUsage;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

class CategoryServiceTest {

    private static final long ACTOR = 1L;

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categories, audit);
        when(categories.saveAndFlush(any(Category.class))).thenAnswer(invocation -> {
            Category saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            ReflectionTestUtils.setField(saved, "version", 0);
            return saved;
        });
    }

    @Test
    void creeUneCategorieAuLibelleNettoye() {
        var created = service.create("  Bande dessinée ", ACTOR);

        assertThat(created.label()).isEqualTo("Bande dessinée");
        assertThat(created.bookCount()).isZero();
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.CREATE), eq(AuditObjectType.CATEGORY), eq("5"), anyMap());
    }

    @Test
    void refuseUnDoublon() {
        when(categories.existsByLabel("roman")).thenReturn(true);

        assertThatThrownBy(() -> service.create("roman", ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CATEGORY_ALREADY_EXISTS));
    }

    @ParameterizedTest(name = "refuse « {0} »")
    @ValueSource(strings = {"X", " X ", "123456789012345678901234567890123456789012345678901"})
    void refuseUnLibelleHorsBornes(String label) {
        assertThatThrownBy(() -> service.create(label, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void accepteLesBornesExactes() {
        assertThat(service.create("BD", ACTOR).label()).isEqualTo("BD");
        assertThat(service.create("x".repeat(50), ACTOR).label()).hasSize(50);
    }

    @Test
    void renommeEnExcluantElleMemeDuControleDUnicite() {
        Category category = CatalogFixtures.category(2L, "Bande dessinée");
        when(categories.findById(2L)).thenReturn(Optional.of(category));
        when(categories.countBooks(2L)).thenReturn(4L);

        var renamed = service.rename(2L, "BD", 0, ACTOR);

        assertThat(renamed.label()).isEqualTo("BD");
        assertThat(renamed.bookCount()).isEqualTo(4);
        verify(categories).existsByLabelAndIdNot("BD", 2L);
    }

    @Test
    void refuseUnRenommageVersUnLibelleExistant() {
        when(categories.findById(2L)).thenReturn(Optional.of(CatalogFixtures.category(2L, "BD")));
        when(categories.existsByLabelAndIdNot("Roman", 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.rename(2L, "Roman", 0, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CATEGORY_ALREADY_EXISTS));
    }

    @Test
    void refuseDeSupprimerUneCategorieUtiliseeAvecLeNombreDeNotices() {
        when(categories.findById(1L)).thenReturn(Optional.of(CatalogFixtures.category(1L, "Roman")));
        when(categories.countBooks(1L)).thenReturn(120L);

        assertThatThrownBy(() -> service.delete(1L, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.CATEGORY_IN_USE);
                    assertThat(ex.properties()).containsEntry("bookCount", 120L);
                    assertThat(ex.getMessage()).contains("120 notice(s)");
                });
        verify(categories, never()).delete(any());
    }

    @Test
    void supprimeUneCategorieInutilisee() {
        Category category = CatalogFixtures.category(4L, "Jeunesse");
        when(categories.findById(4L)).thenReturn(Optional.of(category));

        service.delete(4L, ACTOR);

        verify(categories).delete(category);
    }

    @Test
    void refuseUneCategorieInconnue() {
        assertThatThrownBy(() -> service.delete(9L, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Test
    void listeAvecLUsage() {
        var pageable = PageRequest.of(0, 20);
        when(categories.findAllWithUsage(pageable)).thenReturn(new PageImpl<>(
                List.of(new CategoryUsage(CatalogFixtures.category(1L, "Roman"), 5)), pageable, 1));

        assertThat(service.list(pageable).getContent()).singleElement()
                .satisfies(category -> assertThat(category.bookCount()).isEqualTo(5));
    }
}
