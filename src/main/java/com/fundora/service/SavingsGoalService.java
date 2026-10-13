package com.fundora.service;

import com.fundora.exception.LedgerException;
import com.fundora.model.Group;
import com.fundora.model.SavingsGoal;
import com.fundora.repository.GroupRepository;
import com.fundora.repository.SavingsGoalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Module I & VI: Savings Goal Service for Trip and Event Funds
 */
@Service
public class SavingsGoalService {

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private GroupRepository groupRepository;

    public List<SavingsGoal> getGoalsByGroup(Long groupId) {
        return savingsGoalRepository.findByGroupId(groupId);
    }

    public List<SavingsGoal> getAllGoals() {
        return savingsGoalRepository.findAll();
    }

    @Transactional
    public SavingsGoal createGoal(Long groupId, String title, BigDecimal targetAmount, LocalDate deadline) {
        if (title == null || title.isBlank()) {
            throw LedgerException.invalid("Goal title is required.");
        }
        if (title.trim().length() > 150) {
            throw LedgerException.invalid("Goal title must be at most 150 characters.");
        }
        BigDecimal target = LedgerService.normalizeAmount(targetAmount, "Target amount");
        if (deadline != null && deadline.isBefore(LocalDate.now())) {
            throw LedgerException.invalid("Goal deadline cannot be in the past.");
        }
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> LedgerException.notFound("Group not found with ID: " + groupId));

        SavingsGoal goal = new SavingsGoal(group, title.trim(), target, deadline);
        return savingsGoalRepository.save(goal);
    }

    @Transactional
    public SavingsGoal contribute(Long goalId, BigDecimal amount) {
        SavingsGoal goal = savingsGoalRepository.findById(goalId)
                .orElseThrow(() -> LedgerException.notFound("Savings goal not found with ID: " + goalId));

        goal.deposit(LedgerService.normalizeAmount(amount, "Contribution amount"));
        return savingsGoalRepository.save(goal);
    }
}
