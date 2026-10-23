package billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** P5-F1-S3: the credit-limit guard is the reason BillingAccount exists. */
class BillingAccountTest {

    @Test
    void chargeWithinLimitIncreasesAr() {
        BillingAccount account = new BillingAccount("BA-1", "CUST-1",
            new BigDecimal("100.00"), 30);
        account.addAr(new BigDecimal("60.00"));
        assertEquals(0, account.outstandingArBalance().compareTo(new BigDecimal("60.00")));
        assertEquals("BA-1", account.billingAccountId());
        assertEquals("CUST-1", account.customerId());
        assertEquals(0, account.creditLimit().compareTo(new BigDecimal("100.00")));
        assertEquals(30, account.netDaysTerm());
    }

    @Test
    void chargeOverLimitThrowsAndLeavesBalanceUntouched() {
        BillingAccount account = new BillingAccount("BA-1", "CUST-1",
            new BigDecimal("100.00"), 30);
        account.addAr(new BigDecimal("60.00"));
        assertThrows(CreditLimitExceededException.class,
            () -> account.addAr(new BigDecimal("40.01")));
        assertEquals(0, account.outstandingArBalance().compareTo(new BigDecimal("60.00")));
    }

    @Test
    void settleReducesArAndAllowsNewCharges() {
        BillingAccount account = new BillingAccount("BA-1", "CUST-1",
            new BigDecimal("100.00"), 30);
        account.addAr(new BigDecimal("100.00"));
        account.reduceAr(new BigDecimal("100.00"));
        account.addAr(new BigDecimal("1.00"));
        assertEquals(0, account.outstandingArBalance().compareTo(new BigDecimal("1.00")));
    }

    @Test
    void rehydratedAccountGuardsAgainstItsPersistedBalance() {
        BillingAccount account = BillingAccount.rehydrate("BA-2", "CUST-2",
            new BigDecimal("10.00"), new BigDecimal("10.00"), null);
        assertThrows(CreditLimitExceededException.class,
            () -> account.checkCredit(BigDecimal.ONE));
    }

    @Test
    void checkCreditAloneDoesNotMutate() {
        BillingAccount account = new BillingAccount("BA-1", "CUST-1", BigDecimal.TEN, null);
        account.checkCredit(BigDecimal.ONE);
        assertEquals(0, account.outstandingArBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void constructionGuards() {
        assertThrows(IllegalArgumentException.class,
            () -> new BillingAccount("", "CUST-1", BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class,
            () -> new BillingAccount("BA-1", "CUST-1", new BigDecimal("-1"), null));
        assertThrows(IllegalArgumentException.class,
            () -> new BillingAccount("BA-1", " ", BigDecimal.ZERO, null));
    }
}
