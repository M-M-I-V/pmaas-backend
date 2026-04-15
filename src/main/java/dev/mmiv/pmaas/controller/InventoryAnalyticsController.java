package dev.mmiv.pmaas.controller;

import dev.mmiv.pmaas.dto.DashboardDTOs.*;
import dev.mmiv.pmaas.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inventory Analytics Controller — stock and expiry alert endpoints.
 *
 * Base path: /api/dashboard/inventory
 *
 * Authorization: clinical roles.
 * Inventory data (item names, stock levels) is operational metadata and is
 * not patient PII. However, it remains restricted to authenticated clinical
 * staff to prevent disclosure of clinic procurement details to unauthorized users.
 *
 * Note on ADMIN access: administrative users managing procurement may need
 * these endpoints. If your clinic requires it, add 'ADMIN' to the
 * @PreAuthorize expressions below.
 */
@RestController
@RequestMapping("/api/dashboard/inventory")
@RequiredArgsConstructor
public class InventoryAnalyticsController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/inventory/alerts/low-stock
     *
     * Items where stocksOnHand is at or below the minimum stock level (default 10).
     * Results are ordered by stocksOnHand ascending — most critical items first.
     *
     * Example response:
     * [
     *   { "itemName": "Paracetamol 500mg",  "stocksOnHand": 0,  "minimumStockLevel": 10 },
     *   { "itemName": "Surgical Gloves (M)", "stocksOnHand": 4,  "minimumStockLevel": 10 },
     *   { "itemName": "Alcohol 70%",         "stocksOnHand": 8,  "minimumStockLevel": 10 }
     * ]
     */
    @GetMapping("/alerts/low-stock")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<LowStockItemDTO> getLowStockAlerts() {
        return dashboardService.getLowStockItems();
    }

    /**
     * GET /api/dashboard/inventory/alerts/expiring
     *
     * Items expiring within the next 60 days, ordered by expiration date ascending.
     * Items already expired (expirationDate < today) are excluded — a separate
     * expired-items report should handle those.
     *
     * Example response:
     * [
     *   { "itemName": "Betadine Solution", "expirationDate": "2025-05-01", "stocksOnHand": 12 },
     *   { "itemName": "IV Cannula 20G",    "expirationDate": "2025-05-14", "stocksOnHand": 30 }
     * ]
     */
    @GetMapping("/alerts/expiring")
    @PreAuthorize("hasAnyRole('MD', 'DMD', 'NURSE')")
    public List<ExpiringItemDTO> getExpiringAlerts() {
        return dashboardService.getExpiringItems();
    }
}