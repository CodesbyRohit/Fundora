package com.fundora.service;

import com.fundora.dto.SettlementRequestDTO;
import com.fundora.exception.InsufficientBalanceException;
import com.fundora.exception.LedgerException;
import com.fundora.exception.UserNotFoundException;
import com.fundora.model.Group;
import com.fundora.model.Settlement;
import com.fundora.model.User;
import com.fundora.repository.SettlementRepository;
import com.fundora.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Module II & VI: UPI Settlement Service implementing PaymentGatewayInterface
 * Automates UPI transaction recordation and auto-balances the shared ledger.
 */
@Service
public class UPISettlementService implements PaymentGatewayInterface {

    private static final Pattern UPI_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$");

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerService ledgerService;

    @Override
    public boolean validateVPA(String upiId) {
        if (upiId == null || upiId.trim().isEmpty()) {
            return false;
        }
        return UPI_PATTERN.matcher(upiId.trim()).matches();
    }

    @Override
    @Transactional
    public Settlement processUPISettlement(Long groupId, User payer, User payee, BigDecimal amount, String upiId) {
        BigDecimal settleAmount = LedgerService.normalizeAmount(amount, "Settlement amount");

        if (payer.getId().equals(payee.getId())) {
            throw LedgerException.invalid("Payer and payee must be different users.");
        }

        Group group = ledgerService.requireGroup(groupId);
        ledgerService.requireMember(group, payer.getId());
        ledgerService.requireMember(group, payee.getId());

        // The VPA must be well-formed and must belong to the payee (prevents paying a mistyped/foreign UPI ID)
        String vpa = (upiId == null || upiId.isBlank()) ? payee.getUpiId() : upiId.trim();
        if (!validateVPA(vpa)) {
            throw LedgerException.invalid("Invalid UPI ID '" + vpa + "'. Expected format: name@bank");
        }
        if (!vpa.equalsIgnoreCase(payee.getUpiId())) {
            throw LedgerException.invalid("UPI ID " + vpa + " does not belong to " + payee.getName() + ".");
        }

        // Update in-app wallet balances. Previously a failed debit was ignored while the payee was
        // still credited, which created money out of thin air.
        if (!payer.debitWallet(settleAmount)) {
            throw new InsufficientBalanceException(String.format(
                    "%s's wallet balance (₹%s) is not enough to settle ₹%s.",
                    payer.getName(), payer.getWalletBalance().toPlainString(), settleAmount.toPlainString()));
        }
        payee.creditWallet(settleAmount);
        userRepository.save(payer);
        userRepository.save(payee);

        // Generate synthetic UPI Reference ID (e.g., UPI/FUNDORA/1A2B3C4D)
        String txnRef = "UPI/FUNDORA/" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Settlement settlement = new Settlement(group, payer, payee, settleAmount, "UPI", txnRef);
        settlement.setSettlementStatus("COMPLETED");
        settlement.setSettledAt(LocalDateTime.now());

        return settlementRepository.save(settlement);
    }

    @Transactional
    public Settlement recordSettlement(SettlementRequestDTO dto) {
        if (dto.getPayerUserId() == null || dto.getPayeeUserId() == null) {
            throw LedgerException.invalid("payerUserId and payeeUserId are required.");
        }
        User payer = userRepository.findById(dto.getPayerUserId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPayerUserId()));
        User payee = userRepository.findById(dto.getPayeeUserId())
                .orElseThrow(() -> new UserNotFoundException(dto.getPayeeUserId()));

        return processUPISettlement(dto.getGroupId(), payer, payee, dto.getAmount(), dto.getUpiId());
    }
}
