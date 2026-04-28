package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.InventoryItemRequest;
import dev.mmiv.pmaas.dto.InventoryItemResponse;
import dev.mmiv.pmaas.entity.InventoryItem;
import dev.mmiv.pmaas.entity.ItemCategory;
import dev.mmiv.pmaas.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository repository;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "itemName", "brandName", "category",
            "stockOnHand", "expirationDate", "dateReceived", "createdAt"
    );

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> search(
            String q,
            ItemCategory category,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        String normalizedQuery = (q == null || q.isBlank()) ? "" : q.trim();

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "itemName";
        }

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(direction, sortBy)
        );

        return repository
                .search(normalizedQuery, category, pageable)
                .map(InventoryItemResponse::from);
    }

    @Transactional
    public InventoryItemResponse create(InventoryItemRequest request) {
        InventoryItem item = InventoryItem.builder()
                .itemName(request.itemName().trim())
                .brandName(request.brandName() != null ? request.brandName().trim() : null)
                .category(request.category())
                .description(request.description())
                .stockOnHand(request.stockOnHand())
                .expirationDate(request.expirationDate())
                .dateReceived(request.dateReceived())
                .remarks(request.remarks())
                .build();
        return InventoryItemResponse.from(repository.save(item));
    }

    @Transactional
    public InventoryItemResponse update(Long id, InventoryItemRequest request) {
        InventoryItem item = findOrThrow(id);
        item.setItemName(request.itemName().trim());
        item.setBrandName(request.brandName() != null ? request.brandName().trim() : null);
        item.setCategory(request.category());
        item.setDescription(request.description());
        item.setStockOnHand(request.stockOnHand());
        item.setExpirationDate(request.expirationDate());
        item.setDateReceived(request.dateReceived());
        item.setRemarks(request.remarks());
        return InventoryItemResponse.from(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getById(Long id) {
        return InventoryItemResponse.from(findOrThrow(id));
    }

    private InventoryItem findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Inventory item not found with id: " + id));
    }
}