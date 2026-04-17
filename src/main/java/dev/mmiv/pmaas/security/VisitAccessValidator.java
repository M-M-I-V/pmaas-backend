package dev.mmiv.pmaas.security;

import dev.mmiv.pmaas.entity.Visits;
import dev.mmiv.pmaas.repository.VisitsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Spring Security component used in @PreAuthorize SpEL expressions to enforce
 * visit ownership/assignment rules beyond simple role checks.
 *
 * Registered as a bean named "visitAccessValidator" so Spring Security's
 * SpEL evaluator can find it via @visitAccessValidator.methodName(args).
 *
 * Usage in controllers:
 *
 *   @PreAuthorize("hasRole('MD') and @visitAccessValidator.isAssignedTo(#visitId, authentication)")
 *   @PreAuthorize("hasRole('DMD') and @visitAccessValidator.isAssignedTo(#visitId, authentication)")
 *
 * IMPORTANT: These checks are ADVISORY — they complement @PreAuthorize role checks,
 * they do not replace them. The service layer also validates ownership to prevent
 * bypass via direct service injection.
 */
@Slf4j
@Component("visitAccessValidator")
@RequiredArgsConstructor
public class VisitAccessValidator {

    private final VisitsRepository visitsRepository;

    /**
     * Returns true if the authenticated user is the clinician currently
     * assigned to the given visit.
     *
     * Used to prevent MD-A from editing a visit assigned to MD-B.
     * Returns false (deny) if the visit does not exist — the 404 will be
     * thrown by the service layer when it loads the visit.
     */
    public boolean isAssignedTo(Long visitId, Authentication auth) {
        if (visitId == null || auth == null) return false;

        return visitsRepository.findById(visitId)
                .map(visit -> {
                    Long assignedTo = visit.getAssignedToUserId();
                    if (assignedTo == null) return false;

                    Long currentUserId = extractUserId(auth);
                    boolean allowed = assignedTo.equals(currentUserId);

                    if (!allowed) {
                        log.warn(
                                "Authorization denied: user {} attempted to access visit {} assigned to {}",
                                auth.getName(), visitId, assignedTo
                        );
                    }
                    return allowed;
                })
                .orElse(false);
    }

    /**
     * Returns true if the authenticated user is an MD assigned to this visit.
     * Checks both role AND assignment — role alone is not sufficient.
     */
    public boolean isAssignedMd(Long visitId, Authentication auth) {
        return hasRole(auth, "ROLE_MD") && isAssignedTo(visitId, auth);
    }

    /**
     * Returns true if the authenticated user is a DMD assigned to this visit.
     */
    public boolean isAssignedDmd(Long visitId, Authentication auth) {
        return hasRole(auth, "ROLE_DMD") && isAssignedTo(visitId, auth);
    }

    /**
     * Returns true if the authenticated user is MD or DMD AND assigned to this visit.
     * Used for prescribe endpoint which both roles can access.
     */
    public boolean isAssignedClinician(Long visitId, Authentication auth) {
        return (hasRole(auth, "ROLE_MD") || hasRole(auth, "ROLE_DMD"))
                && isAssignedTo(visitId, auth);
    }

    // Helpers

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority(role));
    }

    /**
     * Extracts the user ID from the Authentication principal.
     * Adjust to match your UserPrincipal implementation.
     */
    private Long extractUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof dev.mmiv.pmaas.entity.UserPrincipal up) {
            return (long) up.getId();
        }
        throw new IllegalStateException("Cannot extract user ID from authentication principal.");
    }
}