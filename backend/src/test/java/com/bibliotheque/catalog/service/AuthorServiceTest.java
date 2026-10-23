package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.CatalogFixtures;
import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.repository.AuthorRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

class AuthorServiceTest {

    private final AuthorRepository authors = mock(AuthorRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AuthorService service = new AuthorService(authors, audit);

    @Test
    void suggereDixAuteursAuPlusAvecUnMotifEchappe() {
        when(authors.suggest("%em!_ile%", PageRequest.of(0, 10)))
                .thenReturn(List.of(CatalogFixtures.author(2L, "Zola", "Émile")));

        assertThat(service.suggest("em_ile")).singleElement()
                .satisfies(author -> assertThat(author.lastName()).isEqualTo("Zola"));
    }

    @Test
    void refuseUneSuggestionTropCourte() {
        assertThatThrownBy(() -> service.suggest(" a "))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void creeUnAuteurSansPrenomEtJournalise() {
        when(authors.save(any(Author.class))).thenAnswer(invocation -> {
            Author saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        var created = service.create(" Anonyme ", "  ", 1L);

        assertThat(created.lastName()).isEqualTo("Anonyme");
        assertThat(created.firstName()).isNull();
        ArgumentCaptor<Map<String, ?>> details = ArgumentCaptor.forClass(Map.class);
        verify(audit).recordAs(eq(1L), eq(AuditAction.CREATE), eq(AuditObjectType.AUTHOR), eq("7"), details.capture());
        assertThat(details.getValue()).containsOnlyKeys("lastName");
    }
}
