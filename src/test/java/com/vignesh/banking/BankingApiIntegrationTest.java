package com.vignesh.banking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests: valid and invalid requests, transaction rules and error handling,
 * run against the real controllers, services and an in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:banktest;DB_CLOSE_DELAY=-1",
        "bank.max-transaction-amount=50000"
})
class BankingApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    // ---------- helpers ----------

    private String openAccount(String holder, String type, String openingDeposit) throws Exception {
        String email = holder.toLowerCase().replace(' ', '.') + "@example.com";
        String body = """
                {"holderName":"%s","email":"%s","accountType":"%s","initialDeposit":%s}
                """.formatted(holder, email, type, openingDeposit);
        MvcResult result = mvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accountNumber");
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions amount(String url, String amount) throws Exception {
        return postJson(url, "{\"amount\":" + amount + "}");
    }

    private void assertBalance(String accountNumber, double expected) throws Exception {
        mvc.perform(get("/api/accounts/" + accountNumber + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(expected));
    }

    // ---------- accounts ----------

    @Test
    void opensAccountWithOpeningDepositAndLedgerEntry() throws Exception {
        String number = openAccount("Asha Rao", "SAVINGS", "1500.50");

        mvc.perform(get("/api/accounts/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Asha Rao"))
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(1500.50));

        mvc.perform(get("/api/accounts/" + number + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].description").value("Opening deposit"))
                .andExpect(jsonPath("$[0].balanceAfter").value(1500.50));
    }

    @Test
    void rejectsAccountWithInvalidFields() throws Exception {
        postJson("/api/accounts", "{\"holderName\":\"\",\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.holderName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.accountType").exists());
    }

    @Test
    void rejectsNegativeOpeningDeposit() throws Exception {
        postJson("/api/accounts",
                "{\"holderName\":\"Ravi Kumar\",\"email\":\"ravi@example.com\",\"accountType\":\"CURRENT\",\"initialDeposit\":-10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.initialDeposit").exists());
    }

    @Test
    void rejectsMalformedJsonAndUnknownAccountType() throws Exception {
        postJson("/api/accounts", "{ this is not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        postJson("/api/accounts",
                "{\"holderName\":\"Meena S\",\"email\":\"meena@example.com\",\"accountType\":\"PLATINUM\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mvc.perform(get("/api/accounts/0000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        mvc.perform(get("/api/accounts/0000000000/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // ---------- deposits ----------

    @Test
    void depositIncreasesBalanceAndRecordsRunningBalance() throws Exception {
        String number = openAccount("Divya Nair", "SAVINGS", "1000");

        amount("/api/accounts/" + number + "/deposit", "250.25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(250.25))
                .andExpect(jsonPath("$.balanceAfter").value(1250.25));

        assertBalance(number, 1250.25);
    }

    @Test
    void rejectsZeroNegativeAndTooPreciseAmounts() throws Exception {
        String number = openAccount("Karthik M", "SAVINGS", "1000");

        for (String bad : new String[]{"0", "-50", "10.123"}) {
            amount("/api/accounts/" + number + "/deposit", bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors.amount").exists());
        }
        postJson("/api/accounts/" + number + "/deposit", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());

        assertBalance(number, 1000.0);
    }

    @Test
    void rejectsAmountAboveTransactionLimit() throws Exception {
        String number = openAccount("Lakshmi P", "CURRENT", "1000");

        amount("/api/accounts/" + number + "/deposit", "50000.01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION"));

        amount("/api/accounts/" + number + "/deposit", "50000")
                .andExpect(status().isOk());
    }

    // ---------- withdrawals ----------

    @Test
    void withdrawalReducesBalance() throws Exception {
        String number = openAccount("Suresh B", "SAVINGS", "900");

        amount("/api/accounts/" + number + "/withdraw", "400")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(500.0));

        assertBalance(number, 500.0);
    }

    @Test
    void withdrawalBeyondBalanceIsRejectedAndBalanceUnchanged() throws Exception {
        String number = openAccount("Anitha V", "SAVINGS", "300");

        amount("/api/accounts/" + number + "/withdraw", "300.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertBalance(number, 300.0);
        mvc.perform(get("/api/accounts/" + number + "/transactions"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void withdrawingTheExactBalanceIsAllowed() throws Exception {
        String number = openAccount("Gopal R", "SAVINGS", "300");

        amount("/api/accounts/" + number + "/withdraw", "300")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfter").value(0.0));
    }

    // ---------- transfers ----------

    @Test
    void transferMovesMoneyAndWritesBothLedgers() throws Exception {
        String from = openAccount("Priya Sharma", "SAVINGS", "1000");
        String to = openAccount("Arun Kumar", "CURRENT", "200");

        postJson("/api/transfers",
                "{\"fromAccount\":\"%s\",\"toAccount\":\"%s\",\"amount\":350,\"description\":\"Rent\"}".formatted(from, to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBalanceAfter").value(650.0))
                .andExpect(jsonPath("$.toBalanceAfter").value(550.0));

        assertBalance(from, 650.0);
        assertBalance(to, 550.0);

        mvc.perform(get("/api/accounts/" + from + "/transactions"))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[0].counterpartyAccount").value(to))
                .andExpect(jsonPath("$[0].description").value("Rent"));
        mvc.perform(get("/api/accounts/" + to + "/transactions"))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[0].counterpartyAccount").value(from));
    }

    @Test
    void transferBeyondBalanceChangesNothing() throws Exception {
        String from = openAccount("Vimal K", "SAVINGS", "100");
        String to = openAccount("Sneha T", "SAVINGS", "100");

        postJson("/api/transfers",
                "{\"fromAccount\":\"%s\",\"toAccount\":\"%s\",\"amount\":100.01}".formatted(from, to))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertBalance(from, 100.0);
        assertBalance(to, 100.0);
    }

    @Test
    void transferToSameAccountIsRejected() throws Exception {
        String number = openAccount("Harini D", "SAVINGS", "500");

        postJson("/api/transfers",
                "{\"fromAccount\":\"%s\",\"toAccount\":\"%s\",\"amount\":10}".formatted(number, number))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION"));

        assertBalance(number, 500.0);
    }

    @Test
    void transferToUnknownAccountFailsAndSourceIsUntouched() throws Exception {
        String from = openAccount("Naveen J", "SAVINGS", "500");

        postJson("/api/transfers",
                "{\"fromAccount\":\"%s\",\"toAccount\":\"0000000000\",\"amount\":10}".formatted(from))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        assertBalance(from, 500.0);
    }

    @Test
    void transferRequiresBothAccountsAndAnAmount() throws Exception {
        postJson("/api/transfers", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fromAccount").exists())
                .andExpect(jsonPath("$.fieldErrors.toAccount").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    // ---------- history ----------

    @Test
    void historyIsNewestFirstAndCanBeFilteredByType() throws Exception {
        String number = openAccount("Bharath S", "SAVINGS", "1000");
        amount("/api/accounts/" + number + "/withdraw", "100").andExpect(status().isOk());
        amount("/api/accounts/" + number + "/deposit", "50").andExpect(status().isOk());

        mvc.perform(get("/api/accounts/" + number + "/transactions"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].balanceAfter").value(950.0))
                .andExpect(jsonPath("$[1].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[2].description").value("Opening deposit"));

        mvc.perform(get("/api/accounts/" + number + "/transactions").param("type", "WITHDRAWAL"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(100.0));

        mvc.perform(get("/api/accounts/" + number + "/transactions").param("limit", "2"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void historyRejectsUnknownTransactionType() throws Exception {
        String number = openAccount("Chitra L", "SAVINGS", "100");

        mvc.perform(get("/api/accounts/" + number + "/transactions").param("type", "REFUND"))
                .andExpect(status().isBadRequest());
    }

    // ---------- closing accounts ----------

    @Test
    void accountWithBalanceCannotBeClosed() throws Exception {
        String number = openAccount("Dinesh P", "SAVINGS", "100");

        mvc.perform(delete("/api/accounts/" + number))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATE_CONFLICT"));
    }

    @Test
    void emptyAccountCanBeClosedAndThenRejectsTransactions() throws Exception {
        String number = openAccount("Eswari G", "SAVINGS", "100");
        amount("/api/accounts/" + number + "/withdraw", "100").andExpect(status().isOk());

        mvc.perform(delete("/api/accounts/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        amount("/api/accounts/" + number + "/deposit", "10")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATE_CONFLICT"));

        mvc.perform(delete("/api/accounts/" + number))
                .andExpect(status().isConflict());

        // history is kept after closing
        mvc.perform(get("/api/accounts/" + number + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
