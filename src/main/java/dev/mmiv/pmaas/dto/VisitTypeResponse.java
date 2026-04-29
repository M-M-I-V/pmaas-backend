package dev.mmiv.pmaas.dto;

import dev.mmiv.pmaas.entity.VisitStatus;
import dev.mmiv.pmaas.entity.VisitType;

/**
 * Minimal response for the GET /api/visits/{id}/type endpoint.
 *
 * PURPOSE:
 *   The frontend's visit detail page (/visits/[id]) needs to know the
 *   visitType before it can call the correct GET endpoint
 *   (GET /api/visits/medical/{id} vs GET /api/visits/dental/{id}).
 *
 *   When the user navigates directly to /visits/42 without a ?type= query
 *   parameter (e.g. from a bookmark, a notification link, or a back-button),
 *   the frontend has no type information available. This lightweight endpoint
 *   resolves that by returning just the fields needed for routing — no
 *   clinical data is included, so the response is safe to call first.
 *
 * SECURITY:
 *   No PHI is returned. visitType and status are classification labels,
 *   not patient health data. The endpoint is accessible to all clinical
 *   roles (NURSE, MD, DMD). ADMIN is excluded by the controller's
 *   @PreAuthorize annotation.
 */
public record VisitTypeResponse(
    Long visitId,
    VisitType visitType,
    VisitStatus status
) {}
