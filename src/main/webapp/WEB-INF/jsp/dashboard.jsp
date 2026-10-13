<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<fmt:setLocale value="en_IN"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Fundora - Social FinTech Dashboard</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/styles.css">
    <!-- Module V: React 18 and Babel for in-browser JSX rendering -->
    <script src="https://unpkg.com/react@18.3.1/umd/react.production.min.js" crossorigin></script>
    <script src="https://unpkg.com/react-dom@18.3.1/umd/react-dom.production.min.js" crossorigin></script>
    <script src="https://unpkg.com/@babel/standalone@7.26.4/babel.min.js"></script>
</head>
<body>

    <!-- Header Navigation -->
    <header class="navbar">
        <div style="display: flex; align-items: center; gap: 1rem;">
            <a href="${pageContext.request.contextPath}/dashboard" class="brand">
                <span>✨ Fundora</span>
                <span class="brand-badge">Mumbai Univ Full-Stack Java</span>
            </a>
            <a href="https://drive.google.com/drive/folders/1na8wxfnAwbS5o1-GlbSOibjOmlkwBQHj" target="_blank" rel="noopener noreferrer" class="btn btn-accent" style="text-decoration: none; padding: 0.35rem 0.85rem; font-size: 0.85rem; border-radius: 6px;">
                📁 Assignment
            </a>
        </div>
        <div class="user-profile-badge">
            <span>👤 <strong><c:out value="${currentUser.name}"/></strong> (<c:out value="${currentUser.role}"/>)</span>
            <span style="color: var(--accent); font-weight: bold;">Wallet: ₹<fmt:formatNumber value="${currentUser.walletBalance}" type="number" minFractionDigits="2"/></span>
            <span style="font-size: 0.8rem; color: var(--text-muted);"><c:out value="${currentUser.upiId}"/></span>
            <a href="${pageContext.request.contextPath}/logout" class="btn btn-nudge" style="padding: 0.25rem 0.65rem; font-size: 0.8rem; text-decoration: none;">
                🚪 Logout
            </a>
        </div>
    </header>

    <div class="container">

        <c:if test="${not empty flashError}">
            <div class="alert-box alert-danger">⚠️ <c:out value="${flashError}"/></div>
        </c:if>
        <c:if test="${not empty flashSuccess}">
            <div class="alert-box alert-success">✅ <c:out value="${flashSuccess}"/></div>
        </c:if>

        <!-- Group Selector Bar -->
        <div style="display: flex; gap: 1rem; align-items: center; margin-bottom: 2rem; flex-wrap: wrap;">
            <label style="color: var(--text-muted); font-weight: 600;">Active Group:</label>
            <c:if test="${not empty groups}">
            <form action="${pageContext.request.contextPath}/dashboard" method="GET" style="display: flex; gap: 0.75rem;">
                <select name="groupId" onchange="this.form.submit()" class="form-control" style="width: auto; min-width: 250px;">
                    <c:forEach var="grp" items="${groups}">
                        <option value="${grp.id}" ${grp.id == selectedGroupId ? 'selected' : ''}>
                            <c:out value="${grp.name}"/> (<c:out value="${grp.groupType}"/>)
                        </option>
                    </c:forEach>
                </select>
            </form>
            </c:if>
            <button type="button" class="btn btn-accent" onclick="openCreateGroupModal()" style="display: flex; align-items: center; gap: 0.4rem; padding: 0.65rem 1.15rem; font-weight: 600; cursor: pointer;">
                ➕ Create New Group
            </button>
            <div style="margin-left: auto; display: flex; gap: 0.75rem;">
                <a href="https://drive.google.com/drive/folders/1na8wxfnAwbS5o1-GlbSOibjOmlkwBQHj" target="_blank" rel="noopener noreferrer" class="btn btn-accent" style="text-decoration: none;">
                    📁 Assignment Folder
                </a>
                <c:if test="${not empty ledger}">
                <a href="${pageContext.request.contextPath}/ledger?groupId=${selectedGroupId}" class="btn btn-primary">
                    📊 View Full Shared Ledger
                </a>
                </c:if>
            </div>
        </div>

        <c:if test="${empty ledger}">
            <div class="card" style="text-align: center; padding: 3rem 1.5rem;">
                <div style="font-size: 2rem; margin-bottom: 0.5rem;">👋</div>
                <h2 class="card-title" style="margin-bottom: 0.5rem;">You are not part of any group yet</h2>
                <p style="color: var(--text-muted);">Use <strong>➕ Create New Group</strong> above to start splitting expenses with your flatmates.</p>
            </div>
        </c:if>

        <c:if test="${not empty ledger}">

        <!-- Metric Cards -->
        <div class="hero-stats-grid">
            <div class="stat-card">
                <div class="stat-title">Total Group Spending</div>
                <div class="stat-value">₹<fmt:formatNumber value="${ledger.totalGroupSpending}" type="number" minFractionDigits="2"/></div>
            </div>
            <div class="stat-card">
                <div class="stat-title">Your Net Standing</div>
                <c:set var="myBalance" value="${empty ledger.netBalances[currentUser.id] ? 0 : ledger.netBalances[currentUser.id]}"/>
                <div class="stat-value ${myBalance >= 0 ? 'positive' : 'negative'}">
                    ${myBalance >= 0 ? '+₹' : '-₹'}<fmt:formatNumber value="${myBalance >= 0 ? myBalance : -myBalance}" type="number" minFractionDigits="2"/>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-title">Active Members</div>
                <div class="stat-value"><c:out value="${fn:length(ledger.group.members)}"/> Members</div>
            </div>
        </div>

        <!-- Main 2-Column Grid -->
        <div class="grid-2col">

            <!-- Left Column: Debts & Recent Expenses -->
            <div>
                <!-- Simplified Debts ("Who Owes Whom") & Smart Nudges -->
                <div class="card">
                    <div class="card-header">
                        <h2 class="card-title">🤝 Net Settlements & Smart Reminders</h2>
                        <span style="font-size: 0.8rem; color: var(--text-muted);">Auto-Simplified Ledger</span>
                    </div>

                    <c:if test="${empty ledger.simplifiedDebts}">
                        <p style="color: var(--accent); padding: 1rem 0;">🎉 All debts are completely settled in this group!</p>
                    </c:if>

                    <c:forEach var="debt" items="${ledger.simplifiedDebts}">
                        <div class="debt-item">
                            <div class="debt-info">
                                <span class="debt-names">
                                    <c:out value="${debt.fromUserName}"/> owes <c:out value="${debt.toUserName}"/>
                                </span>
                                <span class="debt-upi">UPI: <c:out value="${debt.toUserUpiId}"/></span>
                            </div>
                            <div class="debt-actions">
                                <span style="font-weight: 700; color: #fff; margin-right: 0.5rem;">
                                    ₹<fmt:formatNumber value="${debt.amount}" type="number" minFractionDigits="2"/>
                                </span>

                                <!-- Smart Nudge Button (Module IV JS Handler): only the creditor can remind the debtor -->
                                <c:if test="${debt.toUserId == currentUser.id}">
                                <button
                                    class="btn btn-nudge btn-smart-nudge"
                                    data-receiver-id="${debt.fromUserId}"
                                    data-receiver-name="${fn:escapeXml(debt.fromUserName)}"
                                    data-group-id="${selectedGroupId}"
                                    data-amount="${debt.amount}">
                                    Nudge 🔔
                                </button>
                                </c:if>

                                <!-- Direct UPI Settlement Button: only the debtor can pay their own debt -->
                                <c:if test="${debt.fromUserId == currentUser.id}">
                                <button
                                    class="btn btn-upi btn-upi-settle"
                                    data-payee-id="${debt.toUserId}"
                                    data-payee-name="${fn:escapeXml(debt.toUserName)}"
                                    data-upi-id="${fn:escapeXml(debt.toUserUpiId)}"
                                    data-group-id="${selectedGroupId}"
                                    data-amount="${debt.amount}">
                                    Pay UPI ⚡
                                </button>
                                </c:if>
                            </div>
                        </div>
                    </c:forEach>
                </div>

                <!-- Add New Shared Expense Form -->
                <div class="card">
                    <div class="card-header">
                        <h2 class="card-title">➕ Split a New Expense</h2>
                    </div>
                    <form action="${pageContext.request.contextPath}/expenses" method="POST">
                        <input type="hidden" name="action" value="addExpense"/>
                        <input type="hidden" name="groupId" value="${selectedGroupId}"/>

                        <div style="display: grid; grid-template-columns: 2fr 1fr; gap: 1rem;">
                            <div class="form-group">
                                <label class="form-label">Expense Title / Description</label>
                                <input type="text" name="title" maxlength="150" class="form-control" placeholder="e.g. D-Mart Grocery / WiFi Bill" required/>
                            </div>
                            <div class="form-group">
                                <label class="form-label">Total Amount (₹)</label>
                                <input type="number" step="0.01" min="0.01" name="amount" class="form-control" placeholder="0.00" required/>
                            </div>
                        </div>

                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                            <div class="form-group">
                                <label class="form-label">Category</label>
                                <select name="category" class="form-control">
                                    <option value="GROCERIES">Groceries & Food</option>
                                    <option value="RENT">Room Rent / Deposit</option>
                                    <option value="UTILITIES">WiFi / Electricity / Gas</option>
                                    <option value="TRAVEL">Cabs / Flights / Fuel</option>
                                    <option value="ENTERTAINMENT">Outing / Party</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label class="form-label">Splitting Strategy</label>
                                <select name="splitType" class="form-control">
                                    <option value="EQUAL">Split Equally Among All</option>
                                </select>
                            </div>
                        </div>

                        <button type="submit" class="btn btn-primary" style="width: 100%; margin-top: 0.5rem;">
                            Record & Split Expense
                        </button>
                    </form>
                </div>

                <!-- Recent Expenses Table -->
                <div class="card">
                    <div class="card-header">
                        <h2 class="card-title">📜 Recent Group Expenses</h2>
                    </div>
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Date</th>
                                <th>Item</th>
                                <th>Paid By</th>
                                <th>Category</th>
                                <th>Amount</th>
                            </tr>
                        </thead>
                        <tbody id="expenseTableBody">
                            <c:if test="${empty ledger.recentExpenses}">
                                <tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">No expenses recorded yet in this group.</td></tr>
                            </c:if>
                            <c:forEach var="exp" items="${ledger.recentExpenses}">
                                <tr>
                                    <td><c:out value="${exp.expenseDate.toLocalDate()}"/></td>
                                    <td><strong><c:out value="${exp.title}"/></strong></td>
                                    <td><c:out value="${exp.paidBy.name}"/></td>
                                    <td><span style="background: var(--bg-card-alt); padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem;"><c:out value="${exp.category}"/></span></td>
                                    <td class="amount-cell" data-amount="${exp.amount}">₹<fmt:formatNumber value="${exp.amount}" type="number" minFractionDigits="2"/></td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>

            </div>

            <!-- Right Column: Module V React Component (Collaborative Trip / Event Savings Goal) -->
            <div>
                <div class="card">
                    <div class="card-header">
                        <h2 class="card-title">🏖️ Shared Trip & Goal Wallet</h2>
                        <span style="font-size: 0.75rem; background: #4f46e5; padding: 0.2rem 0.5rem; border-radius: 4px;">React.js SPA</span>
                    </div>
                    <!-- React Mounting Root -->
                    <div id="reactSavingsTrackerRoot" data-group-id="${selectedGroupId}"></div>
                </div>

                <!-- Recent Settlements History -->
                <div class="card">
                    <div class="card-header">
                        <h2 class="card-title">⚡ UPI Settlement Log</h2>
                    </div>
                    <c:if test="${empty ledger.recentSettlements}">
                        <p style="color: var(--text-muted); font-size: 0.9rem;">No direct UPI settlements logged yet.</p>
                    </c:if>
                    <c:forEach var="st" items="${ledger.recentSettlements}">
                        <div style="padding: 0.75rem 0; border-bottom: 1px solid var(--border-color); font-size: 0.85rem;">
                            <div><strong><c:out value="${st.payer.name}"/></strong> ➡️ <strong><c:out value="${st.payee.name}"/></strong></div>
                            <div style="display: flex; justify-content: space-between; color: var(--text-muted); margin-top: 0.25rem;">
                                <span>Ref: <c:out value="${st.transactionReference}"/></span>
                                <span style="color: var(--accent); font-weight: bold;">₹<fmt:formatNumber value="${st.amount}" type="number" minFractionDigits="2"/></span>
                            </div>
                        </div>
                    </c:forEach>
                </div>
            </div>

        </div>
        </c:if>

    </div>

    <!-- Create Group Modal -->
    <div id="createGroupModal" class="modal-overlay" style="display: none;">
        <div class="modal-card">
            <div class="modal-header">
                <h3 style="color: #fff; margin: 0; font-size: 1.25rem;">✨ Create New Group</h3>
                <button type="button" onclick="closeCreateGroupModal()" class="modal-close">&times;</button>
            </div>
            <form action="${pageContext.request.contextPath}/dashboard" method="POST">
                <input type="hidden" name="action" value="createGroup" />
                <div class="form-group">
                    <label class="form-label">Group Name *</label>
                    <input type="text" name="name" maxlength="120" class="form-control" placeholder="e.g. Manali College Trip 2026 / Room 402 Groceries" required />
                </div>
                <div class="form-group">
                    <label class="form-label">Group Type</label>
                    <select name="groupType" class="form-control">
                        <option value="FLATMATE_RECURRING">Flatmate & Monthly Recurring (Rent, Groceries, WiFi)</option>
                        <option value="TRIP_EVENT">Trip, Outing & Campus Event (Vacation, Parties)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">Description / Purpose</label>
                    <input type="text" name="description" maxlength="255" class="form-control" placeholder="e.g. Shared expenses among flatmates" />
                </div>
                <div class="form-group">
                    <label class="form-label">Select Group Members (Flatmates)</label>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; background: var(--bg-card-alt); padding: 0.85rem; border-radius: 8px;">
                        <c:forEach var="usr" items="${allUsers}">
                            <label style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.85rem; color: #fff; cursor: pointer;">
                                <c:choose>
                                    <c:when test="${usr.id == currentUser.id}">
                                        <%-- The creator is always added to the group server-side --%>
                                        <input type="checkbox" checked disabled /> <c:out value="${usr.name}"/> (you)
                                    </c:when>
                                    <c:otherwise>
                                        <input type="checkbox" name="memberIds" value="${usr.id}" checked /> <c:out value="${usr.name}"/>
                                    </c:otherwise>
                                </c:choose>
                            </label>
                        </c:forEach>
                    </div>
                </div>
                <div style="display: flex; justify-content: flex-end; gap: 0.75rem; margin-top: 1.5rem;">
                    <button type="button" class="btn btn-nudge" onclick="closeCreateGroupModal()">Cancel</button>
                    <button type="submit" class="btn btn-primary" style="padding: 0.65rem 1.5rem;">🚀 Create Group</button>
                </div>
            </form>
        </div>
    </div>

    <!-- Scripts -->
    <script src="${pageContext.request.contextPath}/static/js/app.js"></script>
    <c:if test="${not empty ledger}">
    <script type="text/babel" src="${pageContext.request.contextPath}/static/js/react-components.js"></script>
    </c:if>
    <script>
        function openCreateGroupModal() {
            document.getElementById('createGroupModal').style.display = 'flex';
            document.querySelector('#createGroupModal input[name="name"]').focus();
        }
        function closeCreateGroupModal() {
            document.getElementById('createGroupModal').style.display = 'none';
        }
        window.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeCreateGroupModal();
        });
        document.getElementById('createGroupModal').addEventListener('click', (e) => {
            if (e.target.id === 'createGroupModal') closeCreateGroupModal();
        });
    </script>
</body>
</html>
