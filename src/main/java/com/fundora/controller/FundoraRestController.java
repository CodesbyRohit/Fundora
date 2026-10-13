package com.fundora.controller;

import com.fundora.config.AuthFilter;
import com.fundora.dto.*;
import com.fundora.exception.LedgerException;
import com.fundora.model.*;
import com.fundora.repository.*;
import com.fundora.service.LedgerService;
import com.fundora.service.NudgeService;
import com.fundora.service.SavingsGoalService;
import com.fundora.service.UPISettlementService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Module VI (Spring Framework & RESTful APIs):
 * Complete REST API controller for Fundora Mobile and Web SPA integration.
 */
@RestController
@RequestMapping("/api")
public class FundoraRestController {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private UPISettlementService upiSettlementService;

    @Autowired
    private NudgeService nudgeService;

    @Autowired
    private SavingsGoalService savingsGoalService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    // --- USERS & GROUPS ENDPOINTS ---

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/groups")
    public ResponseEntity<List<Group>> getAllGroups() {
        return ResponseEntity.ok(groupRepository.findAll());
    }

    @PostMapping("/groups")
    public ResponseEntity<Group> createGroup(@RequestBody GroupRequestDTO dto, HttpSession session) {
        Long sessionUserId = currentUserId(session);
        Long creatorId = sessionUserId != null ? sessionUserId : dto.getCreatedByUserId();
        if (creatorId == null) {
            throw LedgerException.invalid("Sign in or provide createdByUserId to create a group.");
        }
        Group saved = ledgerService.createGroup(dto.getName(), dto.getDescription(), dto.getGroupType(), creatorId, dto.getMemberIds());
        return ResponseEntity.ok(saved);
    }

    // --- SHARED LEDGER & EXPENSE ENDPOINTS ---

    @GetMapping("/groups/{groupId}/ledger")
    public ResponseEntity<LedgerSummaryDTO> getGroupLedger(@PathVariable Long groupId) {
        return ResponseEntity.ok(ledgerService.getGroupLedger(groupId));
    }

    @PostMapping("/expenses/add")
    public ResponseEntity<Expense> addExpense(@RequestBody ExpenseRequestDTO dto, HttpSession session) {
        if (dto.getPaidByUserId() == null) {
            dto.setPaidByUserId(currentUserId(session));
        }
        Expense expense = ledgerService.addExpense(dto);
        return ResponseEntity.ok(expense);
    }

    // --- SMART NUDGES & REMINDERS ---

    @PostMapping("/nudge")
    public ResponseEntity<NudgeLog> sendNudge(@RequestBody NudgeRequestDTO dto, HttpSession session) {
        Long userId = currentUserId(session);
        if (dto.getSenderUserId() == null) {
            dto.setSenderUserId(userId);
        } else if (!dto.getSenderUserId().equals(userId)) {
            throw LedgerException.forbidden("You can only send nudges as yourself.");
        }
        NudgeLog log = nudgeService.sendNudge(dto);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/users/{userId}/nudges")
    public ResponseEntity<List<NudgeLog>> getUserNudges(@PathVariable Long userId) {
        return ResponseEntity.ok(nudgeService.getUserNudges(userId));
    }

    // --- UPI SETTLEMENTS & AUTO-BALANCING ---

    @PostMapping("/settle/upi")
    public ResponseEntity<Settlement> settleDebtUPI(@RequestBody SettlementRequestDTO dto, HttpSession session) {
        Long userId = currentUserId(session);
        if (dto.getPayerUserId() == null) {
            dto.setPayerUserId(userId);
        } else if (!dto.getPayerUserId().equals(userId)) {
            throw LedgerException.forbidden("You can only settle your own debts.");
        }
        Settlement settlement = upiSettlementService.recordSettlement(dto);
        return ResponseEntity.ok(settlement);
    }

    // --- COLLABORATIVE GROUP SAVINGS GOALS ---

    @GetMapping("/groups/{groupId}/savings")
    public ResponseEntity<List<SavingsGoal>> getSavingsGoals(@PathVariable Long groupId) {
        return ResponseEntity.ok(savingsGoalService.getGoalsByGroup(groupId));
    }

    @PostMapping("/groups/{groupId}/savings")
    public ResponseEntity<SavingsGoal> createSavingsGoal(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> payload) {
        String title = payload.get("title") != null ? payload.get("title").toString() : null;
        BigDecimal targetAmount = toAmount(payload.get("targetAmount"), "targetAmount");
        Object deadlineRaw = payload.get("deadline");
        LocalDate deadline = (deadlineRaw != null && !deadlineRaw.toString().isBlank())
                ? LocalDate.parse(deadlineRaw.toString())
                : LocalDate.now().plusMonths(2);

        SavingsGoal goal = savingsGoalService.createGoal(groupId, title, targetAmount, deadline);
        return ResponseEntity.ok(goal);
    }

    @PostMapping("/savings/{goalId}/contribute")
    public ResponseEntity<SavingsGoal> contributeToGoal(
            @PathVariable Long goalId,
            @RequestBody Map<String, Object> payload) {
        BigDecimal amount = toAmount(payload.get("amount"), "amount");
        SavingsGoal goal = savingsGoalService.contribute(goalId, amount);
        return ResponseEntity.ok(goal);
    }

    private static Long currentUserId(HttpSession session) {
        return session != null ? (Long) session.getAttribute(AuthFilter.SESSION_USER_ID) : null;
    }

    private static BigDecimal toAmount(Object raw, String field) {
        if (raw == null || raw.toString().isBlank()) {
            throw LedgerException.invalid(field + " is required.");
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw LedgerException.invalid(field + " must be a number.");
        }
    }
}
