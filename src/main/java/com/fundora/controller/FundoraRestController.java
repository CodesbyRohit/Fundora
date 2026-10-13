package com.fundora.controller;

import com.fundora.dto.*;
import com.fundora.model.*;
import com.fundora.repository.*;
import com.fundora.service.LedgerService;
import com.fundora.service.NudgeService;
import com.fundora.service.SavingsGoalService;
import com.fundora.service.UPISettlementService;
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
@CrossOrigin(origins = "*")
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
    public ResponseEntity<Group> createGroup(@RequestBody GroupRequestDTO dto) {
        Long creatorId = dto.getCreatedByUserId() != null ? dto.getCreatedByUserId() : 1L;
        User creator = userRepository.findById(creatorId).orElse(null);
        if (creator == null) {
            creator = userRepository.findAll().stream().findFirst().orElse(null);
        }

        Group group = new Group(
            dto.getName(),
            dto.getDescription(),
            dto.getGroupType() != null ? dto.getGroupType() : GroupType.FLATMATE_RECURRING,
            creator
        );

        if (dto.getMemberIds() != null && !dto.getMemberIds().isEmpty()) {
            List<User> selectedMembers = userRepository.findAllById(dto.getMemberIds());
            for (User u : selectedMembers) {
                group.addMember(u);
            }
        } else {
            List<User> allUsers = userRepository.findAll();
            for (User u : allUsers) {
                group.addMember(u);
            }
        }

        Group saved = groupRepository.save(group);
        return ResponseEntity.ok(saved);
    }

    // --- SHARED LEDGER & EXPENSE ENDPOINTS ---

    @GetMapping("/groups/{groupId}/ledger")
    public ResponseEntity<LedgerSummaryDTO> getGroupLedger(@PathVariable Long groupId) {
        return ResponseEntity.ok(ledgerService.getGroupLedger(groupId));
    }

    @PostMapping("/expenses/add")
    public ResponseEntity<Expense> addExpense(@RequestBody ExpenseRequestDTO dto) {
        Expense expense = ledgerService.addExpense(dto);
        return ResponseEntity.ok(expense);
    }

    // --- SMART NUDGES & REMINDERS ---

    @PostMapping("/nudge")
    public ResponseEntity<NudgeLog> sendNudge(@RequestBody NudgeRequestDTO dto) {
        NudgeLog log = nudgeService.sendNudge(dto);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/users/{userId}/nudges")
    public ResponseEntity<List<NudgeLog>> getUserNudges(@PathVariable Long userId) {
        return ResponseEntity.ok(nudgeService.getUserNudges(userId));
    }

    // --- UPI SETTLEMENTS & AUTO-BALANCING ---

    @PostMapping("/settle/upi")
    public ResponseEntity<Settlement> settleDebtUPI(@RequestBody SettlementRequestDTO dto) {
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
        String title = (String) payload.get("title");
        BigDecimal targetAmount = new BigDecimal(payload.get("targetAmount").toString());
        String deadlineStr = (String) payload.get("deadline");
        LocalDate deadline = deadlineStr != null ? LocalDate.parse(deadlineStr) : LocalDate.now().plusMonths(2);

        SavingsGoal goal = savingsGoalService.createGoal(groupId, title, targetAmount, deadline);
        return ResponseEntity.ok(goal);
    }

    @PostMapping("/savings/{goalId}/contribute")
    public ResponseEntity<SavingsGoal> contributeToGoal(
            @PathVariable Long goalId,
            @RequestBody Map<String, Object> payload) {
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        SavingsGoal goal = savingsGoalService.contribute(goalId, amount);
        return ResponseEntity.ok(goal);
    }
}
