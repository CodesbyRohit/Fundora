package com.fundora.service;

import com.fundora.dto.NudgeRequestDTO;
import com.fundora.exception.LedgerException;
import com.fundora.exception.UserNotFoundException;
import com.fundora.model.Group;
import com.fundora.model.NudgeLog;
import com.fundora.model.User;
import com.fundora.repository.NudgeLogRepository;
import com.fundora.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Module I, II & VI: Fundora Smart Nudge & Reminder System
 * Generates friendly, socially comfortable peer reminder messages
 * to eliminate interpersonal awkwardness in money collection.
 */
@Service
public class NudgeService {

    @Autowired
    private NudgeLogRepository nudgeLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerService ledgerService;

    /**
     * Sends a smart, non-intrusive nudge notification
     */
    @Transactional
    public NudgeLog sendNudge(NudgeRequestDTO dto) {
        if (dto.getSenderUserId() == null || dto.getReceiverUserId() == null) {
            throw LedgerException.invalid("senderUserId and receiverUserId are required.");
        }
        if (dto.getSenderUserId().equals(dto.getReceiverUserId())) {
            throw LedgerException.invalid("You cannot nudge yourself.");
        }
        BigDecimal amount = LedgerService.normalizeAmount(dto.getAmount(), "Nudge amount");
        Group group = ledgerService.requireGroup(dto.getGroupId());

        User sender = userRepository.findById(dto.getSenderUserId())
                .orElseThrow(() -> new UserNotFoundException(dto.getSenderUserId()));
        User receiver = userRepository.findById(dto.getReceiverUserId())
                .orElseThrow(() -> new UserNotFoundException(dto.getReceiverUserId()));
        ledgerService.requireMember(group, sender.getId());
        ledgerService.requireMember(group, receiver.getId());

        String message = dto.getCustomMessage();
        if (message != null && message.trim().length() > 255) {
            throw LedgerException.invalid("Nudge message must be at most 255 characters.");
        }
        if (message == null || message.trim().isEmpty()) {
            // Auto-generated polite smart nudge
            message = String.format("Hey %s! Just a friendly reminder from %s for ₹%s on Fundora.",
                    receiver.getName().split(" ")[0],
                    sender.getName().split(" ")[0],
                    amount.toPlainString());
        } else {
            message = message.trim();
        }

        NudgeLog log = new NudgeLog(
                sender.getId(),
                receiver.getId(),
                group.getId(),
                amount,
                message
        );
        log.setNudgeType("SMART_NUDGE");
        log.setCreatedAt(LocalDateTime.now());

        return nudgeLogRepository.save(log);
    }

    /**
     * Retrieves nudges received by a user
     */
    public List<NudgeLog> getUserNudges(Long userId) {
        return nudgeLogRepository.findByReceiverUserIdOrderByCreatedAtDesc(userId);
    }
}
