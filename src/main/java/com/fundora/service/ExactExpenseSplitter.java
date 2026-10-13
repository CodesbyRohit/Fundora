package com.fundora.service;

import com.fundora.exception.LedgerMismatchException;
import com.fundora.model.Expense;
import com.fundora.model.ExpenseSplit;
import com.fundora.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module I & II: Exact split strategy verifying exact split total validation
 */
@Component("exactExpenseSplitter")
public class ExactExpenseSplitter implements ExpenseSplitter {

    @Override
    public List<ExpenseSplit> calculateSplits(Expense expense, List<User> participants, Map<Long, BigDecimal> splitData) {
        if (splitData == null || splitData.isEmpty()) {
            throw new LedgerMismatchException("Exact split requires itemized share amounts for each participant.");
        }

        // Every itemized share must belong to a participant, otherwise it would be silently dropped
        // and the ledger would no longer balance.
        Set<Long> participantIds = participants.stream().map(User::getId).collect(Collectors.toSet());
        for (Map.Entry<Long, BigDecimal> entry : splitData.entrySet()) {
            if (!participantIds.contains(entry.getKey())) {
                throw new LedgerMismatchException("User " + entry.getKey() + " has a share but is not a participant of this expense.");
            }
            if (entry.getValue() == null || entry.getValue().signum() < 0) {
                throw new LedgerMismatchException("Share for user " + entry.getKey() + " must be zero or a positive amount.");
            }
        }

        BigDecimal sumOfShares = splitData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumOfShares.compareTo(expense.getAmount()) != 0) {
            throw new LedgerMismatchException(
                String.format("Split total (%s) does not match total expense amount (%s).", sumOfShares, expense.getAmount())
            );
        }

        List<ExpenseSplit> splits = new ArrayList<>();
        for (User user : participants) {
            BigDecimal share = splitData.getOrDefault(user.getId(), BigDecimal.ZERO);
            boolean isPayer = user.getId().equals(expense.getPaidBy().getId());
            splits.add(new ExpenseSplit(expense, user, share, isPayer));
        }

        return splits;
    }
}
