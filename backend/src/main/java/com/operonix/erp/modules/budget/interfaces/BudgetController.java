package com.operonix.erp.modules.budget.interfaces;

import com.operonix.erp.modules.budget.domain.Budget;
import com.operonix.erp.modules.budget.domain.BudgetRepository;
import com.operonix.erp.modules.budget.domain.BudgetStatus;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private static final Set<BudgetStatus> PIPELINE_STATUSES = Set.of(BudgetStatus.RASCUNHO, BudgetStatus.ENVIADO);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float PDF_TOP_MARGIN = 56f;
    private static final float PDF_SIDE_MARGIN = 52f;
    private static final float PDF_BOTTOM_MARGIN = 48f;
    private static final float PDF_HEADER_HEIGHT = 92f;
    private static final float PDF_LOGO_MAX_WIDTH = 178f;
    private static final float PDF_LOGO_MAX_HEIGHT = 58f;

    private final BudgetRepository budgetRepository;
    private final AuditService auditService;

    public BudgetController(BudgetRepository budgetRepository, AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> list(
        @RequestParam(name = "status", required = false) BudgetStatus status,
        @RequestParam(name = "search", required = false) String search
    ) {
        String tenantId = TenantContext.getTenantId();
        String normalizedSearch = normalizeSearch(search);

        Stream<Budget> stream = budgetRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream();

        if (status != null) {
            stream = stream.filter(item -> item.getStatus() == status);
        }

        if (normalizedSearch != null) {
            stream = stream.filter(item ->
                contains(item.getBudgetNumber(), normalizedSearch)
                    || contains(item.getCustomerName(), normalizedSearch)
                    || contains(item.getEquipment(), normalizedSearch)
            );
        }

        List<BudgetResponse> response = stream
            .map(this::toResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<BudgetMetricsResponse> metrics() {
        String tenantId = TenantContext.getTenantId();

        long total = budgetRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).size();
        long draft = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.RASCUNHO);
        long sent = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.ENVIADO);
        long approved = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.APROVADO);
        long rejected = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.REPROVADO);
        long expired = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.EXPIRADO);

        BigDecimal approvedTotal = budgetRepository.sumTotalByStatuses(tenantId, List.of(BudgetStatus.APROVADO));
        BigDecimal pipelineTotal = budgetRepository.sumTotalByStatuses(tenantId, PIPELINE_STATUSES);

        return ResponseEntity.ok(new BudgetMetricsResponse(
            total,
            draft,
            sent,
            approved,
            rejected,
            expired,
            approvedTotal,
            pipelineTotal
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(requireBudget(id)));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        Budget budget = new Budget();
        applyRequest(
            budget,
            request.customerName(),
            request.customerPhone(),
            request.equipment(),
            request.problemDescription(),
            request.itemsDescription(),
            request.laborCost(),
            request.partsCost(),
            request.discountAmount(),
            request.validUntil(),
            request.notes()
        );

        Budget saved = budgetRepository.save(budget);
        auditService.record(
            "BUDGET",
            "CREATE",
            "BUDGET",
            String.valueOf(saved.getId()),
            "Orcamento criado: " + saved.getBudgetNumber() + " para " + saved.getCustomerName()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BudgetResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateBudgetRequest request
    ) {
        Budget budget = requireBudget(id);
        applyRequest(
            budget,
            request.customerName(),
            request.customerPhone(),
            request.equipment(),
            request.problemDescription(),
            request.itemsDescription(),
            request.laborCost(),
            request.partsCost(),
            request.discountAmount(),
            request.validUntil(),
            request.notes()
        );

        Budget saved = budgetRepository.save(budget);
        auditService.record(
            "BUDGET",
            "UPDATE",
            "BUDGET",
            String.valueOf(saved.getId()),
            "Orcamento atualizado: " + saved.getBudgetNumber()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BudgetResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateBudgetStatusRequest request
    ) {
        Budget budget = requireBudget(id);
        BudgetStatus previousStatus = budget.getStatus();
        budget.setStatus(request.status());

        Budget saved = budgetRepository.save(budget);
        auditService.record(
            "BUDGET",
            "UPDATE_STATUS",
            "BUDGET",
            String.valueOf(saved.getId()),
            "Status alterado de " + previousStatus.name() + " para " + saved.getStatus().name()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Budget budget = requireBudget(id);

        budgetRepository.delete(budget);
        auditService.record(
            "BUDGET",
            "DELETE",
            "BUDGET",
            String.valueOf(id),
            "Orcamento removido: " + budget.getBudgetNumber()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/document/pdf")
    public ResponseEntity<ByteArrayResource> documentPdf(
        @PathVariable Long id,
        @RequestParam(name = "download", defaultValue = "true") boolean download
    ) {
        Budget budget = requireBudget(id);
        byte[] content = generateBudgetPdf(budget);
        String filename = safeFileName(budget.getBudgetNumber(), "orcamento") + ".pdf";

        return fileResponse(content, MediaType.APPLICATION_PDF, filename, download);
    }

    @GetMapping("/{id}/whatsapp-link")
    public ResponseEntity<Map<String, String>> whatsappLink(@PathVariable Long id) {
        Budget budget = requireBudget(id);
        String phone = normalizePhone(budget.getCustomerPhone());
        if (phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone do cliente nao informado no orcamento.");
        }

        String message = String.format(
            Locale.ROOT,
            "Ola %s! Orcamento %s atualizado:%nStatus: %s%nTotal: %s%nValidade: %s%nPDF do orcamento pronto para envio em anexo.",
            budget.getCustomerName(),
            budget.getBudgetNumber(),
            formatEnum(budget.getStatus()),
            formatCurrency(budget.getTotalAmount()),
            formatDate(budget.getValidUntil())
        );

        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String url = "https://wa.me/" + phone + "?text=" + encoded;
        String pdfFileName = safeFileName(budget.getBudgetNumber(), "orcamento") + ".pdf";

        return ResponseEntity.ok(Map.of("url", url, "message", message, "pdfFileName", pdfFileName));
    }

    private ResponseEntity<ByteArrayResource> fileResponse(
        byte[] content,
        MediaType mediaType,
        String filename,
        boolean download
    ) {
        ContentDisposition disposition = ContentDisposition
            .builder(download ? "attachment" : "inline")
            .filename(filename, StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(mediaType)
            .contentLength(content.length)
            .body(new ByteArrayResource(content));
    }

    private byte[] generateBudgetPdf(Budget budget) {
        List<PdfLine> lines = new ArrayList<>();

        lines.add(new PdfLine("DaniCell Assistencia Tecnica", true, 16f));
        lines.add(new PdfLine("Orcamento " + fallback(budget.getBudgetNumber(), "Sem numero"), true, 14f));
        lines.add(new PdfLine("Documento comercial para aprovacao de servico.", false, 10.5f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Resumo", true, 12f));
        lines.add(new PdfLine("Status: " + formatEnum(budget.getStatus()), false, 11f));
        lines.add(new PdfLine("Criado em: " + formatDateTime(budget.getCreatedAt()), false, 11f));
        lines.add(new PdfLine("Atualizado em: " + formatDateTime(budget.getUpdatedAt()), false, 11f));
        lines.add(new PdfLine("Validade: " + formatDate(budget.getValidUntil()), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Dados do cliente", true, 12f));
        lines.add(new PdfLine("Cliente: " + normalizedText(budget.getCustomerName(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("Telefone: " + normalizedText(budget.getCustomerPhone(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("Equipamento: " + normalizedText(budget.getEquipment(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Valores", true, 12f));
        lines.add(new PdfLine("Mao de obra: " + formatCurrency(budget.getLaborCost()), false, 11f));
        lines.add(new PdfLine("Pecas: " + formatCurrency(budget.getPartsCost()), false, 11f));
        lines.add(new PdfLine("Desconto: " + formatCurrency(budget.getDiscountAmount()), false, 11f));
        lines.add(new PdfLine("Total: " + formatCurrency(budget.getTotalAmount()), true, 11.5f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Problema relatado", true, 12f));
        lines.add(new PdfLine(normalizedText(budget.getProblemDescription(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Itens e servicos", true, 12f));
        lines.add(new PdfLine(normalizedText(budget.getItemsDescription(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Observacoes", true, 12f));
        lines.add(new PdfLine(normalizedText(budget.getNotes(), "Sem observacoes adicionais."), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Assinatura do cliente: ________________________________", false, 10.5f));
        lines.add(new PdfLine("Documento emitido em " + formatDateTime(LocalDateTime.now()) + ".", false, 10f));

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (PdfRenderer renderer = new PdfRenderer(document, loadBrandLogoBytes())) {
                renderer.writeLines(lines);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar PDF do orcamento.");
        }
    }


    private byte[] loadBrandLogoBytes() {
        ClassPathResource preferred = new ClassPathResource("branding/danicell-logo-doc.png");
        ClassPathResource fallback = new ClassPathResource("branding/danicell-logo.jpg");

        ClassPathResource selected = preferred.exists() ? preferred : fallback;
        if (!selected.exists()) {
            return null;
        }

        try {
            return StreamUtils.copyToByteArray(selected.getInputStream());
        } catch (IOException ex) {
            return null;
        }
    }
    private String normalizePhone(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            return null;
        }

        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }

        if (digits.length() == 10 || digits.length() == 11) {
            return "55" + digits;
        }

        return digits;
    }

    private String safeFileName(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }

        return value.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    private String normalizedText(String value, String fallbackValue) {
        String base = StringUtils.hasText(value) ? value.trim() : fallbackValue;
        return base.replace("\r", "").replace("\t", " ");
    }

    private String sanitizePdfText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", "?");
    }

    private List<String> wrapPdfText(String value, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> wrapped = new ArrayList<>();
        String[] paragraphs = value.replace("\r", "").split("\n", -1);

        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (normalized.isEmpty()) {
                wrapped.add("");
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                float width = font.getStringWidth(candidate) / 1000f * fontSize;

                if (width <= maxWidth || current.length() == 0) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }

                wrapped.add(current.toString());
                current.setLength(0);
                current.append(word);
            }

            if (current.length() > 0) {
                wrapped.add(current.toString());
            }
        }

        if (wrapped.isEmpty()) {
            wrapped.add("");
        }

        return wrapped;
    }

    private final class PdfRenderer implements AutoCloseable {

        private final PDDocument document;
        private final PDImageXObject logoImage;
        private PDPage page;
        private PDPageContentStream stream;
        private float cursorY;
        private int pageNumber;

        private PdfRenderer(PDDocument document, byte[] logoBytes) throws IOException {
            this.document = document;
            this.logoImage = toLogoImage(document, logoBytes);
            openNewPage();
        }

        private void writeLines(List<PdfLine> lines) throws IOException {
            for (PdfLine line : lines) {
                writeLine(line);
            }
        }

        private void writeLine(PdfLine line) throws IOException {
            PDFont font = line.bold() ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
            float fontSize = line.fontSize();
            float lineHeight = Math.max(12f, fontSize * 1.35f);

            List<String> chunks = wrapPdfText(line.text(), font, fontSize, page.getMediaBox().getWidth() - (PDF_SIDE_MARGIN * 2));
            for (String chunk : chunks) {
                ensureSpace(lineHeight);
                stream.beginText();
                stream.setNonStrokingColor(24, 30, 42);
                stream.setFont(font, fontSize);
                stream.newLineAtOffset(PDF_SIDE_MARGIN, cursorY);
                stream.showText(sanitizePdfText(chunk));
                stream.endText();
                cursorY -= lineHeight;
            }
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (cursorY - requiredHeight < PDF_BOTTOM_MARGIN) {
                openNewPage();
            }
        }

        private void openNewPage() throws IOException {
            close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            pageNumber++;
            drawPageHeader();
            drawPageFooter();
            cursorY = page.getMediaBox().getHeight() - PDF_TOP_MARGIN - PDF_HEADER_HEIGHT;
        }

        private void drawPageHeader() throws IOException {
            float pageWidth = page.getMediaBox().getWidth();
            float headerBottom = page.getMediaBox().getHeight() - PDF_TOP_MARGIN - PDF_HEADER_HEIGHT;
            float headerWidth = pageWidth - (PDF_SIDE_MARGIN * 2f);

            stream.setNonStrokingColor(14, 21, 34);
            stream.addRect(PDF_SIDE_MARGIN, headerBottom, headerWidth, PDF_HEADER_HEIGHT);
            stream.fill();

            float textStartX = PDF_SIDE_MARGIN + 14f;
            if (logoImage != null) {
                float[] logoSize = fitWithin(logoImage.getWidth(), logoImage.getHeight(), PDF_LOGO_MAX_WIDTH, PDF_LOGO_MAX_HEIGHT);
                float logoX = PDF_SIDE_MARGIN + 14f;
                float logoY = headerBottom + (PDF_HEADER_HEIGHT - logoSize[1]) / 2f;
                stream.drawImage(logoImage, logoX, logoY, logoSize[0], logoSize[1]);
                textStartX = logoX + logoSize[0] + 14f;
            }

            stream.beginText();
            stream.setNonStrokingColor(245, 248, 252);
            stream.setFont(PDType1Font.HELVETICA_BOLD, 14f);
            stream.newLineAtOffset(textStartX, headerBottom + PDF_HEADER_HEIGHT - 30f);
            stream.showText("DaniCell Assistencia Tecnica");
            stream.endText();

            stream.beginText();
            stream.setNonStrokingColor(214, 222, 233);
            stream.setFont(PDType1Font.HELVETICA, 9.8f);
            stream.newLineAtOffset(textStartX, headerBottom + PDF_HEADER_HEIGHT - 45f);
            stream.showText("Orcamento tecnico com layout profissional para aprovacao de servico.");
            stream.endText();
        }

        private void drawPageFooter() throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(96, 104, 118);
            stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 8.8f);
            stream.newLineAtOffset(PDF_SIDE_MARGIN, PDF_BOTTOM_MARGIN - 20f);
            stream.showText("Emitido em " + sanitizePdfText(formatDateTime(LocalDateTime.now())) + " | Pagina " + pageNumber);
            stream.endText();
        }

        private PDImageXObject toLogoImage(PDDocument targetDocument, byte[] logoBytes) {
            if (logoBytes == null || logoBytes.length == 0) {
                return null;
            }
            try {
                return PDImageXObject.createFromByteArray(targetDocument, logoBytes, "danicell-logo");
            } catch (IOException ex) {
                return null;
            }
        }

        private float[] fitWithin(float sourceWidth, float sourceHeight, float maxWidth, float maxHeight) {
            if (sourceWidth <= 0f || sourceHeight <= 0f) {
                return new float[] { maxWidth, maxHeight };
            }

            float ratio = Math.min(maxWidth / sourceWidth, maxHeight / sourceHeight);
            return new float[] { sourceWidth * ratio, sourceHeight * ratio };
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
    private record PdfLine(String text, boolean bold, float fontSize) {
    }

    private Budget requireBudget(Long id) {
        String tenantId = TenantContext.getTenantId();
        return budgetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orcamento nao encontrado"));
    }

    private void applyRequest(
        Budget budget,
        String customerName,
        String customerPhone,
        String equipment,
        String problemDescription,
        String itemsDescription,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal discountAmount,
        LocalDate validUntil,
        String notes
    ) {
        budget.setCustomerName(requiredText(customerName, "customerName"));
        budget.setCustomerPhone(normalizeOptional(customerPhone));
        budget.setEquipment(requiredText(equipment, "equipment"));
        budget.setProblemDescription(requiredText(problemDescription, "problemDescription"));
        budget.setItemsDescription(normalizeOptional(itemsDescription));
        budget.setLaborCost(nonNullMoney(laborCost));
        budget.setPartsCost(nonNullMoney(partsCost));
        budget.setDiscountAmount(nonNullMoney(discountAmount));
        budget.setValidUntil(validUntil);
        budget.setNotes(normalizeOptional(notes));
        budget.recalculateTotal();
    }

    private BudgetResponse toResponse(Budget item) {
        return new BudgetResponse(
            item.getId(),
            item.getBudgetNumber(),
            item.getCustomerName(),
            item.getCustomerPhone(),
            item.getEquipment(),
            item.getProblemDescription(),
            item.getItemsDescription(),
            item.getLaborCost(),
            item.getPartsCost(),
            item.getDiscountAmount(),
            item.getTotalAmount(),
            item.getStatus(),
            item.getValidUntil() == null ? null : item.getValidUntil().toString(),
            item.getNotes(),
            item.getCreatedAt().toString(),
            item.getUpdatedAt().toString()
        );
    }

    private String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "Nao informado";
        }

        NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return currency.format(value);
    }

    private String formatDate(LocalDate value) {
        if (value == null) {
            return "Nao informado";
        }
        return DATE_FORMAT.format(value);
    }

    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "Nao informado";
        }
        return DATE_TIME_FORMAT.format(value);
    }

    private String formatEnum(Enum<?> value) {
        if (value == null) {
            return "Nao informado";
        }

        String normalized = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String normalizeSearch(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private String requiredText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Campo obrigatorio: " + field);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal nonNullMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    public record CreateBudgetRequest(
        @NotBlank String customerName,
        String customerPhone,
        @NotBlank String equipment,
        @NotBlank String problemDescription,
        String itemsDescription,
        @DecimalMin("0.00") BigDecimal laborCost,
        @DecimalMin("0.00") BigDecimal partsCost,
        @DecimalMin("0.00") BigDecimal discountAmount,
        LocalDate validUntil,
        String notes
    ) {
    }

    public record UpdateBudgetRequest(
        @NotBlank String customerName,
        String customerPhone,
        @NotBlank String equipment,
        @NotBlank String problemDescription,
        String itemsDescription,
        @DecimalMin("0.00") BigDecimal laborCost,
        @DecimalMin("0.00") BigDecimal partsCost,
        @DecimalMin("0.00") BigDecimal discountAmount,
        LocalDate validUntil,
        String notes
    ) {
    }

    public record UpdateBudgetStatusRequest(
        @NotNull BudgetStatus status
    ) {
    }

    public record BudgetResponse(
        Long id,
        String budgetNumber,
        String customerName,
        String customerPhone,
        String equipment,
        String problemDescription,
        String itemsDescription,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BudgetStatus status,
        String validUntil,
        String notes,
        String createdAt,
        String updatedAt
    ) {
    }

    public record BudgetMetricsResponse(
        long total,
        long draft,
        long sent,
        long approved,
        long rejected,
        long expired,
        BigDecimal approvedTotal,
        BigDecimal pipelineTotal
    ) {
    }
}