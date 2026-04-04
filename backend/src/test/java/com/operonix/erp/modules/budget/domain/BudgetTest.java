package com.operonix.erp.modules.budget.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetTest {

    @Test
    void recalculateTotalShouldComputeSubtotalMinusDiscount() {
        Budget budget = new Budget();
        budget.setLaborCost(new BigDecimal("120.50"));
        budget.setPartsCost(new BigDecimal("79.50"));
        budget.setDiscountAmount(new BigDecimal("20.00"));

        budget.recalculateTotal();

        assertEquals(new BigDecimal("180.00"), budget.getTotalAmount());
    }

    @Test
    void recalculateTotalShouldRejectDiscountAboveSubtotal() {
        Budget budget = new Budget();
        budget.setLaborCost(new BigDecimal("10.00"));
        budget.setPartsCost(new BigDecimal("5.00"));
        budget.setDiscountAmount(new BigDecimal("20.00"));

        assertThrows(IllegalArgumentException.class, budget::recalculateTotal);
    }

    @Test
    void recalculateTotalShouldTreatNullValuesAsZero() {
        Budget budget = new Budget();
        budget.setLaborCost(null);
        budget.setPartsCost(null);
        budget.setDiscountAmount(null);

        budget.recalculateTotal();

        assertEquals(new BigDecimal("0.00"), budget.getTotalAmount());
    }
}
