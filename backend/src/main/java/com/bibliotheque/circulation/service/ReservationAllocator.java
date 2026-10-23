package com.bibliotheque.circulation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.service.CopyAllocation;
import com.bibliotheque.catalog.service.CopyCirculationService;
import com.bibliotheque.catalog.service.CopyCirculationService.CopySnapshot;
import com.bibliotheque.circulation.domain.Reservation;
import com.bibliotheque.circulation.repository.ReservationRepository;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.Destination;
import com.bibliotheque.circulation.service.CirculationViews.HoldView;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.settings.SettingCode;
import com.bibliotheque.common.settings.SettingsService;
import com.bibliotheque.member.service.MemberLookup;
import com.bibliotheque.member.service.MemberViews.MemberRef;

/**
 * RG-32 : un exemplaire libéré (retour, ajout, exemplaire retrouvé, réservation prête expirée ou annulée) va
 * à la première réservation en attente de sa notice, sinon en rayon. L'appelant détient le verrou de
 * l'exemplaire ; la tête de file est lue sous verrou (ARCH-circulation §5).
 */
@Service
public class ReservationAllocator implements CopyAllocation {

    private final ReservationRepository reservations;
    private final CopyCirculationService copies;
    private final MemberLookup members;
    private final SettingsService settings;
    private final CirculationAudit audit;
    private final BusinessTime time;

    public ReservationAllocator(ReservationRepository reservations, CopyCirculationService copies,
                                MemberLookup members, SettingsService settings, CirculationAudit audit,
                                BusinessTime time) {
        this.reservations = reservations;
        this.copies = copies;
        this.members = members;
        this.settings = settings;
        this.audit = audit;
        this.time = time;
    }

    /**
     * @param actorAccountId agent à l'origine de la libération, ou {@code null} pour un traitement planifié
     */
    @Transactional
    public CopyRelease allocate(CopySnapshot copy, Long actorAccountId) {
        List<Reservation> head = reservations.lockQueueHead(copy.bookId(), PageRequest.of(0, 1));
        if (head.isEmpty()) {
            copies.changeStatus(copy.id(), CopyStatus.AVAILABLE);
            return new CopyRelease(copy.copyNumber(), copy.shelfMark(), Destination.SHELF, null);
        }
        Reservation next = head.get(0);
        LocalDate deadline = time.today().plusDays(settings.getInt(SettingCode.RESERVATION_PICKUP_DAYS));
        next.makeReady(copy.id(), time.now(), deadline);
        reservations.flush();
        copies.changeStatus(copy.id(), CopyStatus.RESERVED);
        MemberRef member = members.require(next.getMemberId());
        audit.log(actorAccountId, AuditAction.STATE_CHANGE, AuditObjectType.RESERVATION, next.getId(),
                Map.of("from", "PENDING", "to", "READY", "copyNumber", copy.copyNumber(),
                        "memberNumber", member.memberNumber(), "pickupDeadline", deadline.toString()));
        return new CopyRelease(copy.copyNumber(), copy.shelfMark(), Destination.HOLD,
                new HoldView(member.memberNumber(), member.lastName(), member.firstName(), deadline));
    }

    /** AC-012-2 : chaque exemplaire ajouté disponible est proposé à la file d'attente. */
    @Override
    @Transactional
    public void copiesAdded(long bookId, List<Long> copyIds, long actorAccountId) {
        for (Long copyId : copyIds) {
            CopySnapshot copy = copies.lockById(copyId);
            if (copy.status() == CopyStatus.AVAILABLE) {
                allocate(copy, actorAccountId);
            }
        }
    }
}
