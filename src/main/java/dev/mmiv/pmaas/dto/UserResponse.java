package dev.mmiv.pmaas.dto;

/**
 * Safe user representation for API responses.
 * The Users entity contains a password hash. Returning the entity
 * directly from any endpoint (GET /api/admin/users, GET /api/admin/users/{id})
 * would expose the hash over the network, violating data minimisation.
 * This DTO contains only the fields needed by the frontend.
 */
public record UserResponse(
        int id,
        String username,
        String role
) {}
