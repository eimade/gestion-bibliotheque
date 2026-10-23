package com.bibliotheque.catalog.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.PhysicalCondition;
import com.bibliotheque.catalog.domain.WithdrawalReason;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.CopyNumberGenerator;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.catalog.service.CatalogViews.CopyView;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Exemplaires (US-012, US-013, RG-09, RG-10). */
@Service
public class CopyService {

    private final CopyRepository copies;
    private final BookRepository books;
    private final CopyNumberGenerator numbers;
    private final CopyLoanInfo loanInfo;
    private final CopyAllocation allocation;
    private final AuditService audit;
    private final BusinessTime time;

    public CopyService(CopyRepository copies, BookRepository books, CopyNumberGenerator numbers,
                       CopyLoanInfo loanInfo, CopyAllocation allocation, AuditService audit, BusinessTime time) {
        this.copies = copies;
        this.books = books;
        this.numbers = numbers;
        this.loanInfo = loanInfo;
        this.allocation = allocation;
        this.audit = audit;
        this.time = time;
    }

    /**
     * US-012 : exemplaires disponibles, numéros attribués par la séquence ; chacun est ensuite proposé à la
     * file d'attente de la notice (AC-012-2, RG-32).
     */
    @Transactional
    public List<CopyView> add(long bookId, int quantity, String shelfMark, PhysicalCondition condition,
                              LocalDate acquisitionDate, long actorAccountId) {
        Book book = books.findById(bookId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.BOOK_NOT_FOUND, "Cette notice est introuvable."));
        if (acquisitionDate.isAfter(time.today())) {
            throw Validation.invalidField("acquisitionDate", "La date d'acquisition ne peut pas être dans le futur.");
        }
        List<Copy> created = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Copy copy = copies.save(new Copy(numbers.next(), book, shelfMark.trim(), condition, acquisitionDate));
            audit.recordAs(actorAccountId, AuditAction.CREATE, AuditObjectType.COPY, copy.getCopyNumber(),
                    Map.of("bookId", bookId, "shelfMark", copy.getShelfMark()));
            created.add(copy);
        }
        copies.flush();
        allocation.copiesAdded(bookId, created.stream().map(Copy::getId).toList(), actorAccountId);
        return created.stream().map(copy -> CopyView.of(copy, Map.of())).toList();
    }

    @Transactional
    public CopyView update(String copyNumber, String shelfMark, PhysicalCondition condition, Integer version,
                           long actorAccountId) {
        Copy copy = load(copyNumber);
        Validation.requireVersion(version, copy.getVersion());
        copy.update(shelfMark.trim(), condition);
        copies.flush();
        audit.recordAs(actorAccountId, AuditAction.UPDATE, AuditObjectType.COPY, copyNumber,
                Map.of("shelfMark", copy.getShelfMark(), "physicalCondition", condition.name()));
        return view(copy);
    }

    /** RG-10 : retrait définitif. */
    @Transactional
    public CopyView withdraw(String copyNumber, WithdrawalReason reason, long actorAccountId) {
        Copy copy = load(copyNumber);
        String previous = copy.getStatus().name();
        copy.withdraw(reason, time.now());
        copies.flush();
        audit.recordAs(actorAccountId, AuditAction.STATE_CHANGE, AuditObjectType.COPY, copyNumber,
                Map.of("from", previous, "to", copy.getStatus().name(), "reason", reason.name()));
        return view(copy);
    }

    private CopyView view(Copy copy) {
        return CopyView.of(copy, loanInfo.dueDatesOfActiveLoans(List.of(copy.getId())));
    }

    private Copy load(String copyNumber) {
        return copies.findByCopyNumber(copyNumber)
                .orElseThrow(() -> BusinessException.of(ErrorCode.COPY_NOT_FOUND, "Exemplaire inconnu."));
    }
}
