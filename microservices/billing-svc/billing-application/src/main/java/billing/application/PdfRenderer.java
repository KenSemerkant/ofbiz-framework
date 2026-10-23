package billing.application;

import billing.domain.Invoice;

/** Invoice PDF render port (P5-F1-S1). */
public interface PdfRenderer {

    byte[] render(Invoice invoice);
}
