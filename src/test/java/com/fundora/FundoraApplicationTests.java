package com.fundora;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundora.model.Group;
import com.fundora.model.GroupType;
import com.fundora.repository.UserRepository;
import com.fundora.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests against the real embedded Tomcat, so the @WebServlet/@WebFilter
 * components and JSP views are exercised exactly as in production.
 * Each test that mutates data works on its own freshly created group.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FundoraApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- helpers ----------

    private HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(HttpClient client, String path, Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(HttpClient client, String path, String... kv) throws Exception {
        StringBuilder form = new StringBuilder();
        for (int i = 0; i < kv.length; i += 2) {
            if (form.length() > 0) form.append('&');
            form.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
        }
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient loggedInAs(long userId) throws Exception {
        HttpClient client = newClient();
        HttpResponse<String> res = postForm(client, "/login", "quickUserId", String.valueOf(userId));
        assertThat(res.statusCode()).isEqualTo(302);
        return client;
    }

    private Group newGroup(String name) {
        return ledgerService.createGroup(name, "test", GroupType.TRIP_EVENT, 1L, List.of(1L, 2L, 3L, 4L));
    }

    private JsonNode ledger(long groupId) throws Exception {
        HttpResponse<String> res = get(newClient(), "/api/groups/" + groupId + "/ledger");
        assertThat(res.statusCode()).isEqualTo(200);
        return mapper.readTree(res.body());
    }

    private BigDecimal balanceSum(JsonNode ledger) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Iterator<Map.Entry<String, JsonNode>> it = ledger.get("netBalances").fields(); it.hasNext(); ) {
            sum = sum.add(it.next().getValue().decimalValue());
        }
        return sum;
    }

    private BigDecimal wallet(long userId) {
        return userRepository.findById(userId).orElseThrow().getWalletBalance();
    }

    // ---------- pages ----------

    @Test
    void landingPageRendersAtRoot() throws Exception {
        HttpResponse<String> res = get(newClient(), "/");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("Welcome to Fundora");
    }

    @Test
    void dashboardRequiresLoginAndRendersForSignedInUser() throws Exception {
        assertThat(get(newClient(), "/dashboard").statusCode()).isEqualTo(302);

        HttpClient client = loggedInAs(1);
        HttpResponse<String> res = get(client, "/dashboard?groupId=1");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("Gokul Flat 402").contains("+₹8,400.00");
    }

    @Test
    void dashboardToleratesInvalidAndUnknownGroupIds() throws Exception {
        HttpClient client = loggedInAs(1);
        HttpResponse<String> bad = get(client, "/dashboard?groupId=abc");
        assertThat(bad.statusCode()).isEqualTo(200);
        HttpResponse<String> unknown = get(client, "/dashboard?groupId=999999");
        assertThat(unknown.statusCode()).isEqualTo(200);
        assertThat(unknown.body()).contains("not a member");
        assertThat(get(client, "/ledger?groupId=999999").statusCode()).isEqualTo(200);
    }

    @Test
    void invalidExpenseFormShowsErrorInsteadOf500() throws Exception {
        HttpClient client = loggedInAs(1);
        HttpResponse<String> res = postForm(client, "/expenses",
                "action", "addExpense", "groupId", "1", "title", "Bad", "amount", "abc", "category", "FOOD");
        assertThat(res.statusCode()).isEqualTo(302);
        assertThat(get(client, "/dashboard?groupId=1").body()).contains("Expense amount must be a number");
    }

    @Test
    void createGroupFormAddsCreatorAndSelectedMembers() throws Exception {
        HttpClient client = loggedInAs(3);
        HttpResponse<String> res = postForm(client, "/dashboard",
                "action", "createGroup", "name", "Lonavala Trek", "groupType", "TRIP_EVENT", "memberIds", "4");
        assertThat(res.statusCode()).isEqualTo(302);
        String location = res.headers().firstValue("Location").orElseThrow();
        long groupId = Long.parseLong(location.substring(location.indexOf("groupId=") + 8));

        JsonNode group = ledger(groupId).get("group");
        assertThat(group.get("name").asText()).isEqualTo("Lonavala Trek");
        assertThat(group.get("members")).hasSize(2);
    }

    // ---------- ledger maths ----------

    @Test
    void seededLedgerBalancesAreCorrect() throws Exception {
        JsonNode ledger = ledger(1);
        JsonNode balances = ledger.get("netBalances");
        assertThat(balances.get("1").decimalValue()).isEqualByComparingTo("8400");
        assertThat(balances.get("2").decimalValue()).isEqualByComparingTo("-1200");
        assertThat(balances.get("3").decimalValue()).isEqualByComparingTo("-3600");
        assertThat(balances.get("4").decimalValue()).isEqualByComparingTo("-3600");
        assertThat(ledger.get("simplifiedDebts")).hasSize(3);
    }

    @Test
    void exactSplitRejectsSharesForNonParticipants() throws Exception {
        Group g = newGroup("Exact split");
        HttpClient client = loggedInAs(1);
        HttpResponse<String> res = postJson(client, "/api/expenses/add", Map.of(
                "groupId", g.getId(), "title", "Dinner", "amount", 100, "category", "FOOD",
                "splitType", "EXACT", "participantUserIds", List.of(1, 2),
                "exactSplits", Map.of("1", 50, "2", 30, "3", 20)));
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.body()).contains("not a participant").doesNotContain("trace");
    }

    @Test
    void percentageSplitIsHonouredAndLedgerStaysBalanced() throws Exception {
        Group g = newGroup("Percentage split");
        HttpClient client = loggedInAs(1);
        HttpResponse<String> res = postJson(client, "/api/expenses/add", Map.of(
                "groupId", g.getId(), "title", "Cab", "amount", "100.01", "category", "TRAVEL",
                "splitType", "PERCENTAGE", "percentageSplits", Map.of("1", 50, "2", 30, "3", 20)));
        assertThat(res.statusCode()).isEqualTo(200);

        BigDecimal shares = BigDecimal.ZERO;
        for (JsonNode split : mapper.readTree(res.body()).get("splits")) {
            shares = shares.add(split.get("shareAmount").decimalValue());
        }
        assertThat(shares).isEqualByComparingTo("100.01");
        assertThat(balanceSum(ledger(g.getId()))).isEqualByComparingTo("0");
    }

    @Test
    void invalidApiInputReturnsClientErrors() throws Exception {
        HttpClient client = loggedInAs(1);
        assertThat(get(client, "/api/groups/999999/ledger").statusCode()).isEqualTo(404);
        assertThat(postJson(client, "/api/expenses/add",
                Map.of("groupId", 1, "title", "x", "amount", -5)).statusCode()).isEqualTo(400);
        assertThat(postJson(client, "/api/savings/1/contribute", Map.of()).statusCode()).isEqualTo(400);
        assertThat(postJson(client, "/api/nudge",
                Map.of("receiverUserId", 2, "groupId", 1)).statusCode()).isEqualTo(400);
    }

    // ---------- settlements ----------

    @Test
    void settlementRequiresAuthenticationAndOwnership() throws Exception {
        Map<String, Object> body = Map.of("groupId", 1, "payerUserId", 2, "payeeUserId", 1, "amount", 10);
        assertThat(postJson(newClient(), "/api/settle/upi", body).statusCode()).isEqualTo(401);
        assertThat(postJson(loggedInAs(1), "/api/settle/upi", body).statusCode()).isEqualTo(403);
    }

    @Test
    void settlementCannotExceedWalletBalance() throws Exception {
        Group g = newGroup("Wallet check");
        BigDecimal payerBefore = wallet(4);
        BigDecimal payeeBefore = wallet(1);

        HttpResponse<String> res = postJson(loggedInAs(4), "/api/settle/upi", Map.of(
                "groupId", g.getId(), "payeeUserId", 1, "amount", payerBefore.add(BigDecimal.ONE)));
        assertThat(res.statusCode()).isEqualTo(422);
        assertThat(wallet(4)).isEqualByComparingTo(payerBefore);
        assertThat(wallet(1)).isEqualByComparingTo(payeeBefore);
    }

    @Test
    void validSettlementMovesMoneyAndRebalancesLedger() throws Exception {
        Group g = newGroup("Settle up");
        HttpClient tejasvee = loggedInAs(2);
        ledgerService.addExpense(expense(g.getId(), 1L, "400"));   // Tejasvee now owes Dnyaneshwar 100

        BigDecimal payerBefore = wallet(2);
        HttpResponse<String> res = postJson(tejasvee, "/api/settle/upi", Map.of(
                "groupId", g.getId(), "payeeUserId", 1, "amount", "100.00", "upiId", "dnyaneshwar@okhdfcbank"));
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(wallet(2)).isEqualByComparingTo(payerBefore.subtract(new BigDecimal("100")));

        JsonNode ledger = ledger(g.getId());
        assertThat(ledger.get("netBalances").get("2").decimalValue()).isEqualByComparingTo("0");
        assertThat(balanceSum(ledger)).isEqualByComparingTo("0");
    }

    private com.fundora.dto.ExpenseRequestDTO expense(Long groupId, Long payer, String amount) {
        com.fundora.dto.ExpenseRequestDTO dto = new com.fundora.dto.ExpenseRequestDTO();
        dto.setGroupId(groupId);
        dto.setPaidByUserId(payer);
        dto.setTitle("Test expense");
        dto.setAmount(new BigDecimal(amount));
        dto.setCategory("FOOD");
        return dto;
    }
}
