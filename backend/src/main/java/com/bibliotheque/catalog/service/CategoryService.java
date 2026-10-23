package com.bibliotheque.catalog.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.Category;
import com.bibliotheque.catalog.repository.CategoryRepository;
import com.bibliotheque.catalog.service.CatalogViews.CategoryResponse;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Catégories (US-014, RG-12). */
@Service
public class CategoryService {

    static final int MIN_LABEL = 2;
    static final int MAX_LABEL = 50;

    private final CategoryRepository categories;
    private final AuditService audit;

    public CategoryService(CategoryRepository categories, AuditService audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return categories.findAllWithUsage(pageable).map(usage -> new CategoryResponse(usage.category().getId(),
                usage.category().getLabel(), usage.bookCount(), usage.category().getVersion()));
    }

    @Transactional
    public CategoryResponse create(String rawLabel, long actorAccountId) {
        String label = validLabel(rawLabel);
        if (categories.existsByLabel(label)) {
            throw alreadyExists();
        }
        Category category = categories.saveAndFlush(new Category(label));
        audit.recordAs(actorAccountId, AuditAction.CREATE, AuditObjectType.CATEGORY,
                String.valueOf(category.getId()), Map.of("label", label));
        return new CategoryResponse(category.getId(), label, 0, category.getVersion());
    }

    @Transactional
    public CategoryResponse rename(long categoryId, String rawLabel, Integer version, long actorAccountId) {
        Category category = load(categoryId);
        Validation.requireVersion(version, category.getVersion());
        String label = validLabel(rawLabel);
        if (categories.existsByLabelAndIdNot(label, categoryId)) {
            throw alreadyExists();
        }
        String previous = category.getLabel();
        category.rename(label);
        categories.flush();
        audit.recordAs(actorAccountId, AuditAction.UPDATE, AuditObjectType.CATEGORY, String.valueOf(categoryId),
                Map.of("from", previous, "to", label));
        return new CategoryResponse(categoryId, label, categories.countBooks(categoryId), category.getVersion());
    }

    /** RG-12 / AC-014-3 : refus si au moins une notice l'utilise, avec le nombre de notices. */
    @Transactional
    public void delete(long categoryId, long actorAccountId) {
        Category category = load(categoryId);
        long bookCount = categories.countBooks(categoryId);
        if (bookCount > 0) {
            throw BusinessException.of(ErrorCode.CATEGORY_IN_USE,
                            "Suppression impossible : cette catégorie est utilisée par " + bookCount + " notice(s).")
                    .withProperty("bookCount", bookCount);
        }
        categories.delete(category);
        audit.recordAs(actorAccountId, AuditAction.DELETE, AuditObjectType.CATEGORY, String.valueOf(categoryId),
                Map.of("label", category.getLabel()));
    }

    private Category load(long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.CATEGORY_NOT_FOUND, "Catégorie introuvable."));
    }

    private static String validLabel(String rawLabel) {
        String label = rawLabel == null ? "" : rawLabel.trim();
        if (label.length() < MIN_LABEL || label.length() > MAX_LABEL) {
            throw Validation.invalidField("label", "Le libellé doit contenir entre 2 et 50 caractères.");
        }
        return label;
    }

    private static BusinessException alreadyExists() {
        return BusinessException.of(ErrorCode.CATEGORY_ALREADY_EXISTS, "Cette catégorie existe déjà.");
    }
}
