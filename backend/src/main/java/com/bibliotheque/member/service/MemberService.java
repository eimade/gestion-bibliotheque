package com.bibliotheque.member.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.auth.service.MemberAccountService;
import com.bibliotheque.auth.service.MemberAccountService.MemberAccount;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.web.LikePatterns;
import com.bibliotheque.member.domain.Member;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.repository.MemberNumberGenerator;
import com.bibliotheque.member.repository.MemberRepository;
import com.bibliotheque.member.service.MemberCirculation.Obligations;
import com.bibliotheque.member.service.MemberViews.ArchiveCheck;
import com.bibliotheque.member.service.MemberViews.GuardianView;
import com.bibliotheque.member.service.MemberViews.MemberDetail;
import com.bibliotheque.member.service.MemberViews.MemberSummary;
import com.bibliotheque.member.service.MemberViews.MemberToArchive;
import com.bibliotheque.member.service.MemberViews.RenewalView;
import com.bibliotheque.member.service.MembershipPolicy.Renewal;

/** Fichier des adhérents côté personnel (US-016 → US-022). */
@Service
public class MemberService {

    /** RG-13 : une saisie au format d'un numéro d'adhérent est une recherche exacte (AC-017-1, AC-017-4). */
    static final Pattern MEMBER_NUMBER = Pattern.compile("A\\d{6}");

    private static final DateTimeFormatter FRENCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final EnumSet<MemberStatus> NOT_ARCHIVED =
            EnumSet.of(MemberStatus.ACTIVE, MemberStatus.EXPIRED, MemberStatus.SUSPENDED);

    private final MemberRepository members;
    private final MemberNumberGenerator numbers;
    private final MembershipPolicy policy;
    private final MemberCirculation circulation;
    private final MemberAccountService accounts;
    private final AuditService audit;
    private final BusinessTime time;
    private final AppProperties properties;

    public MemberService(MemberRepository members, MemberNumberGenerator numbers, MembershipPolicy policy,
                         MemberCirculation circulation, MemberAccountService accounts, AuditService audit,
                         BusinessTime time, AppProperties properties) {
        this.members = members;
        this.numbers = numbers;
        this.policy = policy;
        this.circulation = circulation;
        this.accounts = accounts;
        this.audit = audit;
        this.time = time;
        this.properties = properties;
    }

    /** US-017 : un numéro exact retrouve aussi un archivé ; sinon nom, prénom ou e-mail, archivés exclus. */
    @Transactional(readOnly = true)
    public Page<MemberSummary> search(String query, MemberStatus status, Pageable pageable) {
        LocalDate today = time.today();
        String trimmed = query == null ? null : query.trim().toUpperCase(Locale.ROOT);
        if (trimmed != null && MEMBER_NUMBER.matcher(trimmed).matches()) {
            List<MemberSummary> found = members.findByMemberNumber(trimmed)
                    .map(member -> MemberSummary.of(member, today)).stream().toList();
            return new PageImpl<>(found, pageable, found.size());
        }
        EnumSet<MemberStatus> statuses = status == null || status == MemberStatus.ARCHIVED
                ? NOT_ARCHIVED : EnumSet.of(status);
        return members.search(statuses, LikePatterns.contains(query), pageable)
                .map(member -> MemberSummary.of(member, today));
    }

    @Transactional(readOnly = true)
    public MemberDetail get(String memberNumber) {
        return detail(load(memberNumber));
    }

    /** US-016 : adhérent actif + compte adhérent avec mot de passe temporaire. */
    @Transactional
    public MemberDetail register(MemberCommand command, String password, boolean confirmDuplicate,
                                 long actorAccountId) {
        LocalDate today = time.today();
        checkIdentity(command, today);
        if (accounts.isEmailUsed(command.email())) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_USED, "Cette adresse e-mail est déjà utilisée.");
        }
        accounts.checkPassword(password);
        if (!confirmDuplicate) {
            Optional<Member> homonym = members.findFirstByLastNameAndFirstNameAndBirthDateAndStatusNot(
                    command.identity().lastName(), command.identity().firstName(), command.identity().birthDate(),
                    MemberStatus.ARCHIVED);
            if (homonym.isPresent()) {
                Member existing = homonym.get();
                throw BusinessException.of(ErrorCode.DUPLICATE_MEMBER_SUSPECTED,
                                "Un adhérent existe déjà avec ces nom, prénom et date de naissance.")
                        .withProperty("existingMember", Map.of(
                                "memberNumber", existing.getMemberNumber(),
                                "lastName", existing.getLastName(),
                                "firstName", existing.getFirstName(),
                                "status", existing.effectiveStatus(today).name()));
            }
        }
        Member member = members.save(new Member(numbers.next(), command.identity(), command.contact(), today,
                policy.initialExpiry(today)));
        accounts.create(member.getId(), command.email(), password, actorAccountId);
        audit.recordAs(actorAccountId, AuditAction.CREATE, AuditObjectType.MEMBER, member.getMemberNumber(),
                Map.of("expiryDate", member.getExpiryDate().toString()));
        members.flush();
        return detail(member);
    }

    /** US-019 : identité, coordonnées et e-mail ; journalisé avec les valeurs avant/après. */
    @Transactional
    public MemberDetail update(String memberNumber, MemberCommand command, Integer version, long actorAccountId) {
        Member member = load(memberNumber);
        if (member.getStatus() == MemberStatus.ARCHIVED) {
            throw BusinessException.of(ErrorCode.MEMBER_ARCHIVED, "Un adhérent archivé ne peut plus être modifié.");
        }
        MemberErrors.requireVersion(version, member.getVersion());
        checkIdentity(command, time.today());
        Map<String, Object> before = snapshot(member);
        accounts.changeEmail(member.getId(), command.email(), actorAccountId);
        member.updateIdentity(command.identity());
        member.updateContact(command.contact());
        members.flush();
        Map<String, Object> changes = changes(before, snapshot(member));
        if (!changes.isEmpty()) {
            audit.recordAs(actorAccountId, AuditAction.UPDATE, AuditObjectType.MEMBER, memberNumber, changes);
        }
        return detail(member);
    }

    /** US-020, RG-15. */
    @Transactional
    public MemberDetail renew(String memberNumber, long actorAccountId) {
        Member member = load(memberNumber);
        Renewal renewal = policy.renewal(member, time.today());
        if (!renewal.allowed()) {
            throw switch (renewal.blockedBy()) {
                case "MEMBER_ARCHIVED" -> BusinessException.of(ErrorCode.MEMBER_ARCHIVED,
                        "Un adhérent archivé ne peut pas être renouvelé.");
                case "MEMBER_SUSPENDED" -> BusinessException.of(ErrorCode.MEMBER_SUSPENDED,
                        "Un adhérent suspendu doit d'abord être réactivé.");
                default -> BusinessException.of(ErrorCode.RENEWAL_TOO_EARLY,
                                "Renouvellement possible à partir du " + FRENCH_DATE.format(renewal.renewableFrom()) + ".")
                        .withProperty("renewableFrom", renewal.renewableFrom().toString());
            };
        }
        LocalDate previous = member.getExpiryDate();
        member.renew(renewal.newExpiryDate());
        members.flush();
        audit.recordAs(actorAccountId, AuditAction.UPDATE, AuditObjectType.MEMBER, memberNumber,
                Map.of("event", "RENEWAL", "before", previous.toString(), "after", member.getExpiryDate().toString()));
        return detail(member);
    }

    /** US-021, RG-17 : les réservations en cours sont conservées (AC-021-1). */
    @Transactional
    public MemberDetail suspend(String memberNumber, String reason, long actorAccountId) {
        Member member = load(memberNumber);
        MemberStatus previous = member.effectiveStatus(time.today());
        member.suspend(reason.trim());
        members.flush();
        audit.recordAs(actorAccountId, AuditAction.STATE_CHANGE, AuditObjectType.MEMBER, memberNumber,
                Map.of("from", previous.name(), "to", MemberStatus.SUSPENDED.name(), "reason", reason.trim()));
        return detail(member);
    }

    /** US-021 : actif, ou expiré si l'adhésion est échue (AC-021-3). */
    @Transactional
    public MemberDetail reactivate(String memberNumber, long actorAccountId) {
        Member member = load(memberNumber);
        if (member.getStatus() == MemberStatus.ARCHIVED) {
            throw BusinessException.of(ErrorCode.MEMBER_ARCHIVED, "Un adhérent archivé ne peut pas être réactivé.");
        }
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            member.reactivate(time.today());
            members.flush();
            audit.recordAs(actorAccountId, AuditAction.STATE_CHANGE, AuditObjectType.MEMBER, memberNumber,
                    Map.of("from", MemberStatus.SUSPENDED.name(), "to", member.getStatus().name()));
        }
        return detail(member);
    }

    @Transactional(readOnly = true)
    public ArchiveCheck archiveCheck(String memberNumber) {
        Member member = load(memberNumber);
        return ArchiveCheck.of(circulation.obligations(member.getId()));
    }

    /** US-022, RG-18 : sous verrou de la ligne, pour qu'aucun emprunt ne s'intercale. */
    @Transactional
    public MemberDetail archive(String memberNumber, long actorAccountId) {
        Member member = members.lockByMemberNumber(memberNumber)
                .orElseThrow(() -> MemberErrors.notFound(memberNumber));
        if (member.getStatus() == MemberStatus.ARCHIVED) {
            return detail(member);
        }
        ArchiveCheck check = ArchiveCheck.of(circulation.obligations(member.getId()));
        if (!check.archivable()) {
            throw BusinessException.of(ErrorCode.ARCHIVE_BLOCKED, "Archivage impossible.")
                    .withReasons(check.reasons())
                    .withProperty("activeLoans", check.activeLoans())
                    .withProperty("activeReservations", check.activeReservations())
                    .withProperty("unpaidTotal", check.unpaidTotal());
        }
        MemberStatus previous = member.effectiveStatus(time.today());
        member.archive(time.now());
        accounts.archive(member.getId());
        members.flush();
        audit.recordAs(actorAccountId, AuditAction.STATE_CHANGE, AuditObjectType.MEMBER, memberNumber,
                Map.of("from", previous.name(), "to", MemberStatus.ARCHIVED.name()));
        return detail(member);
    }

    /** AC-022-2 : expirés depuis plus de D1 (RG-19), avec leur archivabilité. */
    @Transactional(readOnly = true)
    public Page<MemberToArchive> toArchive(Pageable pageable) {
        LocalDate limit = time.today().minus(properties.privacy().memberArchiveProposalAfter());
        Page<Member> page = members.findByStatusAndExpiryDateBefore(MemberStatus.EXPIRED, limit, pageable);
        Map<Long, Obligations> obligations = circulation.obligations(page.map(Member::getId).getContent());
        return page.map(member -> MemberToArchive.of(member,
                obligations.getOrDefault(member.getId(), Obligations.NONE)));
    }

    private MemberDetail detail(Member member) {
        LocalDate today = time.today();
        MemberStatus status = member.effectiveStatus(today);
        Optional<MemberAccount> account = accounts.findByMember(member.getId());
        Obligations obligations = circulation.obligations(member.getId());
        return new MemberDetail(member.getMemberNumber(), member.getLastName(), member.getFirstName(),
                member.getBirthDate(), account.map(MemberAccount::email).orElse(null), member.getPhone(),
                member.getStreet(), member.getPostalCode(), member.getCity(), GuardianView.of(member.getGuardian()),
                member.getRegistrationDate(), member.getExpiryDate(), status, member.getSuspensionReason(),
                account.map(MemberAccount::accountId).orElse(null),
                account.map(MemberAccount::status).orElse(null), member.getVersion(),
                circulation.eligibility(member.getId(), status), RenewalView.of(policy.renewal(member, today)),
                policy.expiresSoon(member, today), obligations.activeReservations());
    }

    /** AC-016-3, AC-016-6 : date de naissance passée, représentant légal sous 15 ans (RG-20). */
    private void checkIdentity(MemberCommand command, LocalDate today) {
        LocalDate birthDate = command.identity().birthDate();
        if (!birthDate.isBefore(today)) {
            throw MemberErrors.invalidField("birthDate", "La date de naissance doit être antérieure à aujourd'hui.");
        }
        if (command.identity().guardian() == null && policy.requiresGuardian(birthDate, today)) {
            throw BusinessException.of(ErrorCode.GUARDIAN_REQUIRED,
                    "Représentant légal obligatoire pour un adhérent de moins de 15 ans.");
        }
    }

    private Member load(String memberNumber) {
        return members.findByMemberNumber(memberNumber).orElseThrow(() -> MemberErrors.notFound(memberNumber));
    }

    private static Map<String, Object> snapshot(Member member) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("lastName", member.getLastName());
        values.put("firstName", member.getFirstName());
        values.put("birthDate", String.valueOf(member.getBirthDate()));
        values.put("phone", member.getPhone());
        values.put("street", member.getStreet());
        values.put("postalCode", member.getPostalCode());
        values.put("city", member.getCity());
        values.put("guardian", member.getGuardian() == null ? null
                : member.getGuardian().lastName() + " " + member.getGuardian().firstName() + " · "
                        + member.getGuardian().phone());
        return values;
    }

    private static Map<String, Object> changes(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, previous) -> {
            Object current = after.get(field);
            if (!Objects.equals(previous, current)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("before", previous);
                change.put("after", current);
                changes.put(field, change);
            }
        });
        return changes;
    }
}
