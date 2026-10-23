package billing.adapters.rest.integration;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import billing.application.PdfRenderer;
import billing.domain.Invoice;

/**
 * P5-F1-S1 PDF render adapter: a minimal, dependency-free PDF 1.4 writer —
 * one page, header, one text row per invoice line, total. Good enough for
 * email attachments and the gate e2e; swap for a layout engine when brand
 * styling shows up (the port is the seam).
 */
@Component
public class MinimalPdfRenderer implements PdfRenderer {

    @Override
    public byte[] render(Invoice invoice) {
        List<String> rows = new ArrayList<>();
        rows.add("Invoice " + invoice.invoiceId());
        if (invoice.orderId() != null) {
            rows.add("Order " + invoice.orderId());
        }
        rows.add("Customer " + invoice.customerId());
        rows.add("");
        for (var line : invoice.lines()) {
            rows.add(("%-40s %s x %s = %s %s").formatted(
                truncate(line.description(), 40), line.quantity().stripTrailingZeros()
                    .toPlainString(), line.amount().toPlainString(),
                line.lineTotal().toPlainString(), invoice.currencyUomId()));
        }
        rows.add("");
        rows.add("Total: " + invoice.total().toPlainString() + " " + invoice.currencyUomId());
        rows.add("Status: " + invoice.status());
        String content = String.join("\n", rows);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Long> offsets = new ArrayList<>();
        out.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        offsets.add((long) out.size());
        out.writeBytes("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n"
            .getBytes(StandardCharsets.US_ASCII));
        offsets.add((long) out.size());
        out.writeBytes("2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n"
            .getBytes(StandardCharsets.US_ASCII));
        offsets.add((long) out.size());
        out.writeBytes(("3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842]"
            + " /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>endobj\n")
            .getBytes(StandardCharsets.US_ASCII));
        offsets.add((long) out.size());
        byte[] stream = ("BT /F1 10 Tf 14 TL 50 790 Td\n" + content + "\nET")
            .getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(("4 0 obj<< /Length " + stream.length + " >>stream\n")
            .getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(stream);
        out.writeBytes("\nendstream endobj\n".getBytes(StandardCharsets.US_ASCII));
        offsets.add((long) out.size());
        out.writeBytes(("5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Courier"
            + " >>endobj\n").getBytes(StandardCharsets.US_ASCII));
        long xref = out.size();
        out.writeBytes("xref\n0 6\n0000000000 65535 f \n"
            .getBytes(StandardCharsets.US_ASCII));
        for (long offset : offsets) {
            out.writeBytes(("%010d 00000 n \n".formatted(offset))
                .getBytes(StandardCharsets.US_ASCII));
        }
        out.writeBytes(("trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref
            + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private String truncate(String text, int width) {
        return text.length() <= width ? text : text.substring(0, width - 1) + ".";
    }
}
