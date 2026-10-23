package finance.adapters.rest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import finance.application.AutoPostingService;
import finance.application.FinanceSerializer;
import finance.application.GlService;
import finance.application.OutboxWriter;

/** Wires the application-layer use cases into the adapter layer. */
@Configuration
@EnableScheduling
public class FinanceBeans {

    @Bean
    GlService glService(finance.application.GlAccountStore accounts,
            finance.application.GlTransactionStore transactions,
            finance.application.PeriodStore periods, OutboxWriter outbox,
            FinanceSerializer serializer) {
        return new GlService(accounts, transactions, periods, outbox, serializer);
    }

    @Bean
    AutoPostingService autoPostingService(GlService gl,
            finance.application.PostingRuleStore rules,
            finance.application.ParkedPostingStore parked) {
        return new AutoPostingService(gl, rules, parked);
    }
}
