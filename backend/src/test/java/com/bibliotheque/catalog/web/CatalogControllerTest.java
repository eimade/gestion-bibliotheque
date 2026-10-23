package com.bibliotheque.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bibliotheque.catalog.service.CatalogSearchService;
import com.bibliotheque.catalog.service.CatalogSearchService.PublicSearch;
import com.bibliotheque.catalog.service.CatalogViews.BookSearchItem;
import com.bibliotheque.catalog.service.CatalogViews.CategoryView;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;

@WebMvcTest(controllers = CatalogController.class)
@Import({SecurityConfig.class, SecuritySessionManager.class})
class CatalogControllerTest {

    private static final BookSearchItem ITEM = new BookSearchItem(1L, "Le Comte de Monte-Cristo", null, List.of(),
            1844, List.of(), 1, 2);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CatalogSearchService catalog;

    @Test
    void rechercheSansConnexionTrieeParPertinence() throws Exception {
        when(catalog.search(eq(new PublicSearch("monte cristo", null, null, false)), eq(PageRequest.of(0, 20)), eq(true)))
                .thenReturn(new PageImpl<>(List.of(ITEM), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/catalog/books").param("q", "monte cristo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Le Comte de Monte-Cristo"))
                .andExpect(jsonPath("$.content[0].availableCount").value(1))
                .andExpect(jsonPath("$.content[0].totalCount").value(2));
    }

    @Test
    void triExpliciteSansPertinence() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publicationYear"));
        when(catalog.search(any(), eq(pageable), eq(false))).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mvc.perform(get("/api/v1/catalog/books").param("q", "zola").param("sort", "publicationYear,desc"))
                .andExpect(status().isOk());

        verify(catalog).search(new PublicSearch("zola", null, null, false), pageable, false);
    }

    @Test
    void motCleTropCourt() throws Exception {
        mvc.perform(get("/api/v1/catalog/books").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void triNonPropose() throws Exception {
        mvc.perform(get("/api/v1/catalog/books").param("sort", "title,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ficheIntrouvable() throws Exception {
        when(catalog.publicDetail(9L)).thenThrow(BusinessException.of(ErrorCode.BOOK_NOT_FOUND, "Ce livre est introuvable."));

        mvc.perform(get("/api/v1/catalog/books/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void categoriesPubliques() throws Exception {
        when(catalog.categoryOptions()).thenReturn(List.of(new CategoryView(1L, "Roman")));

        mvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Roman"));
    }

    @Test
    void languesPubliques() throws Exception {
        mvc.perform(get("/api/v1/catalog/languages")).andExpect(status().isOk());
    }
}
