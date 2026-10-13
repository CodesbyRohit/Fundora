package com.fundora.service;

import com.fundora.exception.LedgerMismatchException;
import com.fundora.model.Expense;
import com.fundora.model.ExpenseSplit;
import com.fundora.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module I & II: Percentage split strategy (e.g. 50% / 30% / 20%).
 * Percentages must add up to exactly 100; rounding paise go to the last participant
 * so the shares always sum to the expense amount.
 */
@Component("percentageExpenseSplitter")
public class PercentageExpenseSplitter implements ExpenseSplitter {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public List<ExpenseSplit> calculateSplits(Expense expense, List<User> participants, Map<Long, BigDecimal> splitData) {
        if (splitData == null || splitData.isEmpty()) {
            throw new LedgerMismatchException("Percentage split requires a percentage for each participant.");
        }

        Set<Long> participantIds = participants.stream().map(User::getId).collect(Collectors.toSet());
        for (Map.Entry<Long, BigDecimal> entry : splitData.entrySet()) {
            if (!participantIds.contains(entry.getKey())) {
                throw new LedgerMismatchException("User " + entry.getKey() + " has a percentage but is not a participant of this expense.");
            }
            if (entry.getValue() == null || entry.getValue().signum() < 0) {
                throw new LedgerMismatchException("Percentage for user " + entry.getKey() + " must be zero or positive.");
            }
        }

        BigDecimal totalPercent = splitData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPercent.compareTo(HUNDRED) != 0) {
            throw new LedgerMismatchException("Percentages add up to " + totalPercent.stripTrailingZeros().toPlainString() + "%, expected 100%.");
        }

        // Round every share down to the paisa, then hand the leftover paise one by one
        // to participants with a non-zero percentage so the total is exact.
        List<ExpenseSplit> splits = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (User user : participants) {
            BigDecimal percent = splitData.getOrDefault(user.getId(), BigDecimal.ZERO);
            BigDecimal share = expense.getAmount().multiply(percent).divide(HUNDRED, 2, RoundingMode.DOWN);
            allocated = allocated.add(share);
            boolean isPayer = user.getId().equals(expense.getPaidBy().getId());
            splits.add(new ExpenseSplit(expense, user, share, isPayer));
        }

        BigDecimal paisa = new BigDecimal("0.01");
        BigDecimal remainder = expense.getAmount().subtract(allocated);
        for (int i = 0; remainder.signum() > 0; i = (i + 1) % splits.size()) {
            ExpenseSplit split = splits.get(i);
            if (splitData.getOrDefault(split.getUser().getId(), BigDecimal.ZERO).signum() > 0) {
                split.setShareAmount(split.getShareAmount().add(paisa));
                remainder = remainder.subtract(paisa);
            }
        }

        return splits;
    }
}
