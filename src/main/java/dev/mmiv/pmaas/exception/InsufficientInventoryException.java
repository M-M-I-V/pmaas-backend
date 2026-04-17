package dev.mmiv.pmaas.exception;

public class InsufficientInventoryException extends VisitWorkflowException {

    private final Long inventoryItemId;
    private final String itemName;
    private final int requested;
    private final int available;

    public InsufficientInventoryException(
        Long itemId,
        String itemName,
        int requested,
        int available
    ) {
        super(
            "Medicine '" +
                itemName +
                "' requires " +
                requested +
                " units but only " +
                available +
                " available."
        );
        this.inventoryItemId = itemId;
        this.itemName = itemName;
        this.requested = requested;
        this.available = available;
    }

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public String getItemName() {
        return itemName;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
