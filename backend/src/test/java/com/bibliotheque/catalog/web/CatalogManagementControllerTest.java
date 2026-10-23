package com.bibliotheque.catalog.web;

import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.domain.PhysicalCondition;
import com.bibliotheque.catalog.domain.WithdrawalReason;
import com.bibliotheque.catalog.service.AuthorService;
import com.bibliotheque.catalog.service.BookService;
import com.bibliotheque.catalog.service.CatalogViews.AuthorView;
import com.bibliotheque.catalog.service.CatalogViews.BookAdminDetail;
import com.bibliotheque.catalog.service.CatalogViews.CategoryResponse;
import com.bibliotheque.catalog.service.CatalogViews.CopyView;
import com.bibliotheque.catalog.service.CatalogViews.LanguageView;
import com.bibliotheque.catalog.service.CategoryService;
import com.bibliotheque.catalog.service.CopyService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;

@WebMvcTest(controllers = {BookController.class, CopyController.class, AuthorController.class, CategoryController.class})
@Import({SecurityConfig.class, SecuritySessionManager.class})
class CatalogManagementControllerTest {

    private static final AccountPrincipal LIBRARIAN = new AccountPrincipal(2L, "LIBRARIAN", false);
    private static final String BOOK_BODY = """
            {"title":"L'Étranger","authorIds":[1],"categoryIds":[1],"languageCode":"fre","publicationYear":1942}""";
    private static final BookAdminDetail DETAIL = new BookAdminDetail(3L, null, "L'Étranger", null, List.of(), List.of(),
            null, 1942, new LanguageView("fre", "Français"), null, null, List.of(), 0, 0, 0);
    private static final CopyView COPY = new CopyView(10L, "00000010", "R CAM", PhysicalCondition.GOOD,
            LocalDate.of(2024, 1, 2), CopyStatus.WITHDRAWN, WithdrawalReason.WEEDED, null, 1);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private BookService bookService;
    @MockitoBean
    private CopyService copyService;
    @MockitoBean
    private AuthorService authorService;
    @MockitoBean
    private CategoryService categoryService;

    @Test
    void anonymeNonAuthentifie() throws Exception {
        mvc.perform(get("/api/v1/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void adherentNonAutorise() throws Exception {
        mvc.perform(get("/api/v1/books").with(authenticated(new AccountPrincipal(9L, "MEMBER", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void creationDeNoticeRenvoie201AvecLocation() throws Exception {
        when(bookService.create(any(), eq(2L))).thenReturn(DETAIL);

        mvc.perform(post("/api/v1/books").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/books/3"))
                .andExpect(jsonPath("$.language.label").value("Français"));
    }

    @Test
    void noticeSansAuteurNiCategorie() throws Exception {
        mvc.perform(post("/api/v1/books").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"authorIds\":[],\"categoryIds\":[],\"languageCode\":\"fre\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void anneeAnterieureA1450() throws Exception {
        mvc.perform(post("/api/v1/books").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"authorIds\":[1],\"categoryIds\":[1],\"languageCode\":\"fre\",\"publicationYear\":1449}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("publicationYear"));
    }

    @Test
    void isbnDejaPresent() throws Exception {
        when(bookService.create(any(), eq(2L))).thenThrow(BusinessException.of(ErrorCode.ISBN_ALREADY_EXISTS, "Doublon")
                .withProperty("existingBook", Map.of("id", 9, "title", "L'Étranger")));

        mvc.perform(post("/api/v1/books").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingBook.id").value(9));
    }

    @Test
    void modificationEtSuppression() throws Exception {
        when(bookService.update(eq(3L), any(), eq(2L))).thenReturn(DETAIL);

        mvc.perform(put("/api/v1/books/3").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_BODY))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/books/3").with(csrf()).with(authenticated(LIBRARIAN)))
                .andExpect(status().isNoContent());
        verify(bookService).delete(3L, 2L);
    }

    @Test
    void ajoutDExemplaires() throws Exception {
        when(copyService.add(3L, 2, "R CAM", PhysicalCondition.GOOD, LocalDate.of(2026, 10, 17), 2L))
                .thenReturn(List.of(COPY, COPY));

        mvc.perform(post("/api/v1/books/3/copies").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"shelfMark\":\"R CAM\",\"physicalCondition\":\"GOOD\",\"acquisitionDate\":\"2026-10-17\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void quantiteHorsBornes() throws Exception {
        mvc.perform(post("/api/v1/books/3/copies").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":21,\"shelfMark\":\"R\",\"physicalCondition\":\"GOOD\",\"acquisitionDate\":\"2026-10-17\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retraitDExemplaire() throws Exception {
        when(copyService.withdraw("00000010", WithdrawalReason.WEEDED, 2L)).thenReturn(COPY);

        mvc.perform(post("/api/v1/copies/00000010/withdrawal").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WEEDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void numeroDExemplaireMalForme() throws Exception {
        mvc.perform(put("/api/v1/copies/12ab").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shelfMark\":\"R\",\"physicalCondition\":\"GOOD\",\"version\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retraitSansMotif() throws Exception {
        mvc.perform(post("/api/v1/copies/00000010/withdrawal").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suggestionEtCreationDAuteur() throws Exception {
        when(authorService.suggest("zol")).thenReturn(List.of(new AuthorView(2L, "Zola", "Émile")));
        when(authorService.create("Camus", "Albert", 2L)).thenReturn(new AuthorView(3L, "Camus", "Albert"));

        mvc.perform(get("/api/v1/authors").param("q", "zol").with(authenticated(LIBRARIAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Émile"));
        mvc.perform(post("/api/v1/authors").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lastName\":\"Camus\",\"firstName\":\"Albert\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void categorieUtiliseeAvecNombreDeNotices() throws Exception {
        when(categoryService.create("BD", 2L)).thenReturn(new CategoryResponse(5L, "BD", 0, 0));
        doThrow(BusinessException.of(ErrorCode.CATEGORY_IN_USE, "Utilisée")
                .withProperty("bookCount", 120L)).when(categoryService).delete(1L, 2L);

        mvc.perform(post("/api/v1/categories").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"BD\"}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/categories/1").with(csrf()).with(authenticated(LIBRARIAN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.bookCount").value(120));
    }

    @Test
    void listeEtRenommageDeCategories() throws Exception {
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));
        when(categoryService.rename(5L, "Bande dessinée", 0, 2L)).thenReturn(new CategoryResponse(5L, "Bande dessinée", 0, 1));

        mvc.perform(get("/api/v1/categories").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/categories/5").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Bande dessinée\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void rechercheDeGestion() throws Exception {
        when(bookService.search(any(), any(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/books").param("includeWithdrawn", "true").with(authenticated(LIBRARIAN)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/books/3").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
    }
}
