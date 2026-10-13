package com.fundora.controller;

import com.fundora.config.AuthFilter;
import com.fundora.dto.ExpenseRequestDTO;
import com.fundora.dto.LedgerSummaryDTO;
import com.fundora.exception.LedgerException;
import com.fundora.model.Group;
import com.fundora.model.GroupType;
import com.fundora.model.SplitType;
import com.fundora.model.User;
import com.fundora.repository.UserRepository;
import com.fundora.service.LedgerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Module III (Servlets, JSP & MVC Pattern):
 * ExpenseServlet serves dynamic server-side rendered JSP views,
 * handles HTTP GET/POST form submissions, and interacts with Session state.
 */
@WebServlet(name = "ExpenseServlet", urlPatterns = {"/dashboard", "/expenses", "/ledger"})
public class ExpenseServlet extends HttpServlet {

    private static final String FLASH_ERROR = "flashError";
    private static final String FLASH_SUCCESS = "flashSuccess";

    private LedgerService ledgerService;
    private UserRepository userRepository;

    @Override
    public void init() throws ServletException {
        super.init();
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
        this.ledgerService = ctx.getBean(LedgerService.class);
        this.userRepository = ctx.getBean(UserRepository.class);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession();
        User currentUser = resolveCurrentUser(session);
        if (currentUser == null) {
            // AuthFilter guarantees a user id; it can still point at a user that no longer exists
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        request.setAttribute("currentUser", currentUser);
        moveFlash(session, request, FLASH_ERROR);
        moveFlash(session, request, FLASH_SUCCESS);

        // Only show the groups the signed-in user belongs to
        List<Group> userGroups = ledgerService.getGroupsForUser(currentUser.getId());
        request.setAttribute("groups", userGroups);
        request.setAttribute("allUsers", userRepository.findAll());

        Long requestedId = parseId(request.getParameter("groupId"));
        Group selected = userGroups.stream()
                .filter(g -> g.getId().equals(requestedId))
                .findFirst()
                .orElse(userGroups.isEmpty() ? null : userGroups.get(0));
        if (requestedId != null && (selected == null || !selected.getId().equals(requestedId))) {
            request.setAttribute(FLASH_ERROR, "That group does not exist or you are not a member of it.");
        }

        if (selected != null) {
            LedgerSummaryDTO ledgerSummary = ledgerService.getGroupLedger(selected.getId());
            request.setAttribute("selectedGroupId", selected.getId());
            request.setAttribute("ledger", ledgerSummary);
        }

        String view = "/ledger".equals(request.getServletPath()) ? "group-ledger.jsp" : "dashboard.jsp";
        request.getRequestDispatcher("/WEB-INF/jsp/" + view).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession();
        Long currentUserId = (Long) session.getAttribute(AuthFilter.SESSION_USER_ID);
        String action = request.getParameter("action");
        Long groupId = parseId(request.getParameter("groupId"));
        String redirectTo = "/dashboard" + (groupId != null ? "?groupId=" + groupId : "");

        try {
            if ("addExpense".equals(action)) {
                ExpenseRequestDTO dto = new ExpenseRequestDTO();
                dto.setGroupId(groupId);
                dto.setPaidByUserId(currentUserId);
                dto.setTitle(request.getParameter("title"));
                dto.setAmount(parseAmount(request.getParameter("amount")));
                dto.setCategory(request.getParameter("category"));
                dto.setSplitType(parseEnum(SplitType.class, request.getParameter("splitType"), SplitType.EQUAL));

                ledgerService.addExpense(dto);
                session.setAttribute(FLASH_SUCCESS, "Expense \"" + dto.getTitle().trim() + "\" recorded and split.");
            } else if ("createGroup".equals(action)) {
                List<Long> memberIds = new ArrayList<>();
                String[] rawIds = request.getParameterValues("memberIds");
                if (rawIds != null) {
                    for (String raw : rawIds) {
                        Long id = parseId(raw);
                        if (id == null) {
                            throw LedgerException.invalid("Invalid member selection.");
                        }
                        memberIds.add(id);
                    }
                }
                GroupType type = parseEnum(GroupType.class, request.getParameter("groupType"), GroupType.FLATMATE_RECURRING);
                Group saved = ledgerService.createGroup(
                        request.getParameter("name"), request.getParameter("description"), type, currentUserId, memberIds);
                session.setAttribute(FLASH_SUCCESS, "Group \"" + saved.getName() + "\" created with " + saved.getMembers().size() + " members.");
                redirectTo = "/dashboard?groupId=" + saved.getId();
            }
        } catch (LedgerException e) {
            session.setAttribute(FLASH_ERROR, e.getMessage());
        }

        response.sendRedirect(request.getContextPath() + redirectTo);
    }

    private User resolveCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(AuthFilter.SESSION_USER_ID);
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }

    private static void moveFlash(HttpSession session, HttpServletRequest request, String key) {
        Object value = session.getAttribute(key);
        if (value != null) {
            request.setAttribute(key, value);
            session.removeAttribute(key);
        }
    }

    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw LedgerException.invalid("Expense amount is required.");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw LedgerException.invalid("Expense amount must be a number.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw LedgerException.invalid("Unknown option: " + raw);
        }
    }
}
