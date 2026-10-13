package com.fundora.service;

import com.fundora.dto.DebtRelationDTO;
import com.fundora.dto.ExpenseRequestDTO;
import com.fundora.dto.LedgerSummaryDTO;
import com.fundora.exception.LedgerException;
import com.fundora.exception.UserNotFoundException;
import com.fundora.model.*;
import com.fundora.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module I, II & VI: Core Fundora Shared Ledger & Expense Splitting Engine
 * Features:
 * 1. Equal / Exact / Percentage expense calculations
 * 2. Min-Cash-Flow Greedy Debt Simplification Algorithm ("Who Owes Whom")
 * 3. Real-time net debt balance computation
 */
@Service
public class LedgerService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99"); // DECIMAL(12, 2)

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private JdbcLedgerDao jdbcLedgerDao;

    @Autowired
    @Qualifier("equalExpenseSplitter")
    private ExpenseSplitter equalExpenseSplitter;

    @Autowired
    @Qualifier("exactExpenseSplitter")
    private ExpenseSplitter exactExpenseSplitter;

    @Autowired
    @Qualifier("percentageExpenseSplitter")
    private ExpenseSplitter percentageExpenseSplitter;

    /**
     * Records a new expense and computes individual splits
     */
    @Transactional
    public Expense addExpense(ExpenseRequestDTO dto) {
        if (dto.getGroupId() == null) {
            throw LedgerException.invalid("groupId is required.");
        }
        if (dto.getPaidByUserId() == null) {
            throw LedgerException.invalid("paidByUserId is required.");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw LedgerException.invalid("Expense title is required.");
        }
        BigDecimal amount = normalizeAmount(dto.getAmount(), "Expense amount");

        Group group = requireGroup(dto.getGroupId());
        User paidBy = userRepository.findById(dto.getPaidByUserId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPaidByUserId()));
        requireMember(group, paidBy.getId());

        SplitType splitType = dto.getSplitType() != null ? dto.getSplitType() : SplitType.EQUAL;
        String category = (dto.getCategory() == null || dto.getCategory().isBlank())
                ? "GENERAL" : dto.getCategory().trim().toUpperCase(Locale.ROOT);

        Expense expense = new Expense(group, paidBy, dto.getTitle().trim(), amount, category, splitType);

        Map<Long, BigDecimal> splitData = switch (splitType) {
            case EXACT -> dto.getExactSplits();
            case PERCENTAGE -> dto.getPercentageSplits();
            default -> null;
        };

        // Determine participants: explicit list, else the users named in the split data, else all group members
        List<User> participants;
        if (dto.getParticipantUserIds() != null && !dto.getParticipantUserIds().isEmpty()) {
            participants = resolveMembers(group, dto.getParticipantUserIds());
        } else if (splitData != null && !splitData.isEmpty()) {
            participants = resolveMembers(group, splitData.keySet());
        } else {
            participants = new ArrayList<>(group.getMembers());
        }

        if (participants.isEmpty()) {
            throw LedgerException.invalid("Expense must have at least one participant.");
        }
        participants.sort(Comparator.comparing(User::getId));

        // Module II: Strategy Pattern Selection for Splitting Logic
        ExpenseSplitter splitter = switch (splitType) {
            case EXACT -> exactExpenseSplitter;
            case PERCENTAGE -> percentageExpenseSplitter;
            default -> equalExpenseSplitter;
        };

        List<ExpenseSplit> splits = splitter.calculateSplits(expense, participants, splitData);
        expense.setSplits(splits);

        return expenseRepository.save(expense);
    }

    /**
     * Creates a group; the creator is always a member, and members default to every user when none are chosen.
     */
    @Transactional
    public Group createGroup(String name, String description, GroupType groupType, Long creatorId, Collection<Long> memberIds) {
        if (name == null || name.isBlank()) {
            throw LedgerException.invalid("Group name is required.");
        }
        if (name.trim().length() > 120) {
            throw LedgerException.invalid("Group name must be at most 120 characters.");
        }
        if (description != null && description.trim().length() > 255) {
            throw LedgerException.invalid("Group description must be at most 255 characters.");
        }
        if (creatorId == null) {
            throw LedgerException.invalid("Group creator is required.");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(creatorId));

        String desc = (description == null || description.isBlank()) ? null : description.trim();
        Group group = new Group(name.trim(), desc, groupType != null ? groupType : GroupType.FLATMATE_RECURRING, creator);

        List<User> members = (memberIds == null || memberIds.isEmpty())
                ? userRepository.findAll()
                : userRepository.findAllById(memberIds);
        if (memberIds != null && members.size() != new HashSet<>(memberIds).size()) {
            throw LedgerException.notFound("One or more selected members do not exist.");
        }
        members.forEach(group::addMember);

        return groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<Group> getGroupsForUser(Long userId) {
        List<Group> groups = new ArrayList<>(groupRepository.findGroupsByUserId(userId));
        groups.sort(Comparator.comparing(Group::getId));
        return groups;
    }

    @Transactional(readOnly = true)
    public Group requireGroup(Long groupId) {
        if (groupId == null) {
            throw LedgerException.invalid("groupId is required.");
        }
        return groupRepository.findById(groupId)
                .orElseThrow(() -> LedgerException.notFound("Group with ID " + groupId + " not found."));
    }

    public void requireMember(Group group, Long userId) {
        boolean member = group.getMembers().stream().anyMatch(u -> u.getId().equals(userId));
        if (!member) {
            throw LedgerException.invalid("User " + userId + " is not a member of group '" + group.getName() + "'.");
        }
    }

    /**
     * Calculates complete ledger summary for a group, including net balances and simplified debt graph
     */
    @Transactional(readOnly = true)
    public LedgerSummaryDTO getGroupLedger(Long groupId) {
        Group group = requireGroup(groupId);

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
        List<Settlement> settlements = settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId);

        // Calculate total group spending
        BigDecimal totalSpending = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Module III: Using JDBC DAO for optimized aggregate net balances
        Map<Long, BigDecimal> netBalances = jdbcLedgerDao.calculateRawUserBalances(groupId);

        // Ensure all group members have an entry in net balances
        for (User member : group.getMembers()) {
            netBalances.putIfAbsent(member.getId(), BigDecimal.ZERO);
        }

        // Module I & VI: Min-Cash-Flow Greedy Debt Simplification
        List<DebtRelationDTO> simplifiedDebts = simplifyDebts(netBalances, group);

        return new LedgerSummaryDTO(group, totalSpending, netBalances, simplifiedDebts, expenses, settlements);
    }

    /**
     * Greedy Min-Cash-Flow Debt Simplification Algorithm
     * Minimizes the total number of transactions required to settle all debts in a group.
     */
    private List<DebtRelationDTO> simplifyDebts(Map<Long, BigDecimal> netBalances, Group group) {
        List<DebtRelationDTO> debtList = new ArrayList<>();
        Map<Long, User> userMap = group.getMembers().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, HashMap::new));
        // Users who left the group may still carry a balance; load them too so their debts are not dropped
        Set<Long> missing = new HashSet<>(netBalances.keySet());
        missing.removeAll(userMap.keySet());
        if (!missing.isEmpty()) {
            userRepository.findAllById(missing).forEach(u -> userMap.put(u.getId(), u));
        }

        // Priority Queues for Debtors (negative balance) and Creditors (positive balance)
        // Debtor balance stored as positive amount owed for easy comparison
        class BalanceNode {
            final Long userId;
            final BigDecimal amount;

            BalanceNode(Long userId, BigDecimal amount) {
                this.userId = userId;
                this.amount = amount;
            }
        }

        // Ties broken by user id so the suggested settlements are deterministic
        Comparator<BalanceNode> largestFirst = Comparator.comparing((BalanceNode n) -> n.amount).reversed()
                .thenComparing(n -> n.userId);
        PriorityQueue<BalanceNode> debtors = new PriorityQueue<>(largestFirst);
        PriorityQueue<BalanceNode> creditors = new PriorityQueue<>(largestFirst);

        BigDecimal paisa = new BigDecimal("0.01");
        for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal balance = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            if (balance.compareTo(paisa) >= 0) {
                creditors.offer(new BalanceNode(entry.getKey(), balance));
            } else if (balance.compareTo(paisa.negate()) <= 0) {
                debtors.offer(new BalanceNode(entry.getKey(), balance.abs()));
            }
        }

        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            BalanceNode debtor = debtors.poll();
            BalanceNode creditor = creditors.poll();

            BigDecimal settlementAmount = debtor.amount.min(creditor.amount);

            User debtorUser = userMap.get(debtor.userId);
            User creditorUser = userMap.get(creditor.userId);

            if (debtorUser != null && creditorUser != null) {
                debtList.add(new DebtRelationDTO(
                        debtorUser.getId(),
                        debtorUser.getName(),
                        creditorUser.getId(),
                        creditorUser.getName(),
                        creditorUser.getUpiId(),
                        settlementAmount
                ));
            }

            BigDecimal remainingDebtor = debtor.amount.subtract(settlementAmount);
            BigDecimal remainingCreditor = creditor.amount.subtract(settlementAmount);

            if (remainingDebtor.compareTo(paisa) >= 0) {
                debtors.offer(new BalanceNode(debtor.userId, remainingDebtor));
            }
            if (remainingCreditor.compareTo(paisa) >= 0) {
                creditors.offer(new BalanceNode(creditor.userId, remainingCreditor));
            }
        }

        return debtList;
    }

    /**
     * Validates a money amount: positive, at most 2 decimal places, fits DECIMAL(12, 2).
     */
    public static BigDecimal normalizeAmount(BigDecimal amount, String label) {
        if (amount == null) {
            throw LedgerException.invalid(label + " is required.");
        }
        if (amount.signum() <= 0) {
            throw LedgerException.invalid(label + " must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw LedgerException.invalid(label + " can have at most 2 decimal places (paise).");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw LedgerException.invalid(label + " is too large.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private List<User> resolveMembers(Group group, Collection<Long> userIds) {
        Map<Long, User> members = group.getMembers().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<User> result = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(userIds)) {
            User u = members.get(id);
            if (u == null) {
                throw LedgerException.invalid("User " + id + " is not a member of group '" + group.getName() + "'.");
            }
            result.add(u);
        }
        return result;
    }
}
