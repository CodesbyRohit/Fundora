/**
 * Module IV: Fundamentals of JavaScript & DOM Manipulation
 * Module VI: Asynchronous Fetch API interaction with Spring Boot REST Controllers
 */

document.addEventListener('DOMContentLoaded', () => {
    console.log("⚡ Fundora Social FinTech Client Engine Initialized.");

    // Initialize Event Listeners for Nudges and UPI Settlements
    initNudgeButtons();
    initSettlementButtons();
    scanAndHighlightLedgerDOM();
});

/**
 * Module IV: JavaScript DOM Tree Traversal using NodeIterator & Dynamic Highlighting
 */
function scanAndHighlightLedgerDOM() {
    const tableBody = document.querySelector('#expenseTableBody');
    if (!tableBody) return;

    const iterator = document.createNodeIterator(
        tableBody,
        NodeFilter.SHOW_ELEMENT,
        {
            acceptNode: function(node) {
                return node.classList && node.classList.contains('amount-cell')
                    ? NodeFilter.FILTER_ACCEPT
                    : NodeFilter.FILTER_SKIP;
            }
        }
    );

    let currentNode;
    while ((currentNode = iterator.nextNode())) {
        const val = parseFloat(currentNode.getAttribute('data-amount') || 0);
        if (val > 5000) {
            currentNode.style.fontWeight = 'bold';
            currentNode.style.color = '#38bdf8'; // Highlight high-value shared expenses
        }
    }
}

/**
 * Formats a rupee amount the Indian way (e.g. 12,000.00)
 */
function formatINR(amount) {
    return Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Reads the {code, message} error body returned by the REST API, falling back to a generic message
 */
async function readApiError(response, fallback) {
    try {
        const body = await response.json();
        if (body && body.message) return body.message;
    } catch (_) { /* non-JSON error body */ }
    return fallback;
}

/**
 * Module IV & VI: Smart Reminder Nudge Handler (Non-intrusive alert triggering)
 * The sender is the signed-in user (taken from the server-side session).
 */
function initNudgeButtons() {
    const nudgeButtons = document.querySelectorAll('.btn-smart-nudge');
    nudgeButtons.forEach(btn => {
        const originalLabel = btn.innerText;
        btn.addEventListener('click', async () => {
            const receiverId = btn.getAttribute('data-receiver-id');
            const receiverName = btn.getAttribute('data-receiver-name');
            const groupId = btn.getAttribute('data-group-id');
            const amount = btn.getAttribute('data-amount');

            btn.disabled = true;
            btn.innerText = 'Sending Nudge...';

            try {
                const response = await fetch('/api/nudge', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        receiverUserId: parseInt(receiverId, 10),
                        groupId: parseInt(groupId, 10),
                        amount: amount
                    })
                });

                if (response.ok) {
                    showToast(`✨ Smart Nudge sent to ${receiverName}!`, 'success');
                    btn.innerText = 'Nudged ✔';
                    btn.classList.add('btn-accent');
                } else {
                    showToast(await readApiError(response, 'Failed to send nudge. Please retry.'), 'error');
                    btn.disabled = false;
                    btn.innerText = originalLabel;
                }
            } catch (err) {
                console.error("Nudge error:", err);
                showToast('Network error while nudging.', 'error');
                btn.disabled = false;
                btn.innerText = originalLabel;
            }
        });
    });
}

/**
 * Module IV & VI: Direct UPI Settlement Execution & Auto-Balancing
 * The payer is the signed-in user (taken from the server-side session).
 */
function initSettlementButtons() {
    const settleButtons = document.querySelectorAll('.btn-upi-settle');
    settleButtons.forEach(btn => {
        const originalLabel = btn.innerText;
        btn.addEventListener('click', async () => {
            const payeeId = btn.getAttribute('data-payee-id');
            const payeeName = btn.getAttribute('data-payee-name');
            const upiId = btn.getAttribute('data-upi-id');
            const groupId = btn.getAttribute('data-group-id');
            const amount = btn.getAttribute('data-amount');

            if (!confirm(`Confirm UPI settlement of ₹${formatINR(amount)} to ${payeeName} (${upiId})?`)) {
                return;
            }

            btn.disabled = true;
            btn.innerText = 'Processing UPI...';

            try {
                const response = await fetch('/api/settle/upi', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        groupId: parseInt(groupId, 10),
                        payeeUserId: parseInt(payeeId, 10),
                        amount: amount,
                        upiId: upiId,
                        paymentMode: 'UPI'
                    })
                });

                if (response.ok) {
                    const data = await response.json();
                    showToast(`🎉 Settled ₹${formatINR(amount)} via UPI to ${payeeName}! Ref: ${data.transactionReference}`, 'success');
                    setTimeout(() => {
                        window.location.reload();
                    }, 1500);
                } else {
                    showToast(await readApiError(response, 'UPI settlement failed. Please verify your balance.'), 'error');
                    btn.disabled = false;
                    btn.innerText = originalLabel;
                }
            } catch (err) {
                console.error("UPI error:", err);
                showToast('Network error during settlement.', 'error');
                btn.disabled = false;
                btn.innerText = originalLabel;
            }
        });
    });
}

/**
 * Module IV: Toast Notification Component dynamically injected into DOM
 * Uses textContent (never innerHTML) so user-supplied names cannot inject markup.
 */
function showToast(message, type = 'info') {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        container.setAttribute('role', 'status');
        container.setAttribute('aria-live', 'polite');
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    const text = document.createElement('span');
    text.textContent = message;
    toast.appendChild(text);
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.5s ease';
        setTimeout(() => toast.remove(), 500);
    }, 4000);
}
