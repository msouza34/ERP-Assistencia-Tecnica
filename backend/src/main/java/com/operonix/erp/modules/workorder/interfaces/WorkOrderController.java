package com.operonix.erp.modules.workorder.interfaces;

import com.operonix.erp.modules.workorder.domain.WorkOrder;
import com.operonix.erp.modules.workorder.domain.WorkOrderPriority;
import com.operonix.erp.modules.workorder.domain.WorkOrderStatus;
import com.operonix.erp.modules.workorder.domain.WorkOrderTimelineEvent;
import com.operonix.erp.modules.workorder.infrastructure.WorkOrderRepository;
import com.operonix.erp.modules.workorder.infrastructure.WorkOrderTimelineEventRepository;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
        MediaType.APPLICATION_PDF_VALUE,
        MediaType.IMAGE_JPEG_VALUE,
        MediaType.IMAGE_PNG_VALUE
    );
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float PDF_TOP_MARGIN = 56f;
    private static final float PDF_SIDE_MARGIN = 52f;
    private static final float PDF_BOTTOM_MARGIN = 48f;
    private static final float PDF_HEADER_HEIGHT = 92f;
    private static final float PDF_LOGO_MAX_WIDTH = 178f;
    private static final float PDF_LOGO_MAX_HEIGHT = 58f;

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderTimelineEventRepository timelineEventRepository;
    private final AuditService auditService;

    public WorkOrderController(
        WorkOrderRepository workOrderRepository,
        WorkOrderTimelineEventRepository timelineEventRepository,
        AuditService auditService
    ) {
        this.workOrderRepository = workOrderRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<WorkOrderResponse>> list(
        @RequestParam(name = "status", required = false) WorkOrderStatus status,
        @RequestParam(name = "priority", required = false) WorkOrderPriority priority,
        @RequestParam(name = "search", required = false) String search
    ) {
        String tenantId = TenantContext.getTenantId();
        String normalizedSearch = normalizeSearch(search);

        Stream<WorkOrder> stream = workOrderRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream();

        if (status != null) {
            stream = stream.filter(workOrder -> workOrder.getStatus() == status);
        }

        if (priority != null) {
            stream = stream.filter(workOrder -> workOrder.getPriority() == priority);
        }

        if (normalizedSearch != null) {
            stream = stream.filter(workOrder ->
                contains(workOrder.getOrderNumber(), normalizedSearch)
                    || contains(workOrder.getCustomerName(), normalizedSearch)
                    || contains(workOrder.getEquipment(), normalizedSearch)
                    || contains(workOrder.getTechnicianName(), normalizedSearch)
            );
        }

        List<WorkOrderResponse> response = stream
            .map(this::toResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<WorkOrderMetricsResponse> metrics() {
        String tenantId = TenantContext.getTenantId();
        List<WorkOrder> items = workOrderRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);

        long total = items.size();
        long open = items.stream()
            .filter(item -> item.getStatus() != WorkOrderStatus.FINALIZADO && item.getStatus() != WorkOrderStatus.ENTREGUE)
            .count();
        long urgent = items.stream().filter(item -> item.getPriority() == WorkOrderPriority.URGENTE).count();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (WorkOrderStatus value : WorkOrderStatus.values()) {
            long count = items.stream().filter(item -> item.getStatus() == value).count();
            byStatus.put(value.name(), count);
        }

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (WorkOrderPriority value : WorkOrderPriority.values()) {
            long count = items.stream().filter(item -> item.getPriority() == value).count();
            byPriority.put(value.name(), count);
        }

        BigDecimal totalCost = items.stream()
            .map(WorkOrder::getServiceCost)
            .filter(cost -> cost != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pricedItems = items.stream().filter(item -> item.getServiceCost() != null).count();
        BigDecimal averageTicket = pricedItems == 0
            ? BigDecimal.ZERO
            : totalCost.divide(BigDecimal.valueOf(pricedItems), 2, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new WorkOrderMetricsResponse(total, open, urgent, byStatus, byPriority, averageTicket, totalCost));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrderResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(requireWorkOrder(id)));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<WorkOrderTimelineResponse>> timeline(@PathVariable Long id) {
        WorkOrder workOrder = requireWorkOrder(id);
        String tenantId = TenantContext.getTenantId();

        List<WorkOrderTimelineResponse> response = timelineEventRepository
            .findByTenantIdAndWorkOrderIdOrderByCreatedAtAsc(tenantId, workOrder.getId())
            .stream()
            .map(event -> new WorkOrderTimelineResponse(
                event.getId(),
                event.getEventType(),
                event.getDescription(),
                event.getCreatedBy(),
                event.getCreatedAt().toString()
            ))
            .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkOrderResponse> createJson(@Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrder workOrder = buildWorkOrder(request);
        WorkOrder saved = workOrderRepository.save(workOrder);

        addTimelineEvent(saved, "CREATED", "Ordem de servico criada.");
        if (saved.hasAttachment()) {
            addTimelineEvent(saved, "ATTACHMENT_ADDED", "Anexo adicionado na criacao da OS.");
        }

        auditService.record(
            "WORK_ORDER",
            "CREATE",
            "WORK_ORDER",
            String.valueOf(saved.getId()),
            "OS criada: " + saved.getOrderNumber() + " para cliente " + saved.getCustomerName()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkOrderResponse> createWithFile(
        @RequestParam("customerName") String customerName,
        @RequestParam(name = "customerPhone", required = false) String customerPhone,
        @RequestParam("equipment") String equipment,
        @RequestParam("defectDescription") String defectDescription,
        @RequestParam(name = "notes", required = false) String notes,
        @RequestParam(name = "priority", required = false) WorkOrderPriority priority,
        @RequestParam(name = "technicianName", required = false) String technicianName,
        @RequestParam(name = "estimatedCompletionDate", required = false) String estimatedCompletionDate,
        @RequestParam(name = "serviceCost", required = false) String serviceCost,
        @RequestParam(name = "attachment", required = false) MultipartFile attachment
    ) {
        CreateWorkOrderRequest payload = new CreateWorkOrderRequest(
            requiredText(customerName, "customerName"),
            customerPhone,
            requiredText(equipment, "equipment"),
            requiredText(defectDescription, "defectDescription"),
            notes,
            priority,
            technicianName,
            estimatedCompletionDate,
            parseCost(serviceCost)
        );

        WorkOrder workOrder = buildWorkOrder(payload);
        applyAttachment(workOrder, attachment);

        WorkOrder saved = workOrderRepository.save(workOrder);
        addTimelineEvent(saved, "CREATED", "Ordem de servico criada.");
        if (saved.hasAttachment()) {
            addTimelineEvent(saved, "ATTACHMENT_ADDED", "Anexo adicionado na criacao da OS.");
        }

        auditService.record(
            "WORK_ORDER",
            "CREATE_WITH_ATTACHMENT",
            "WORK_ORDER",
            String.valueOf(saved.getId()),
            "OS criada com anexo: " + saved.getOrderNumber()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WorkOrderResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateStatusRequest request
    ) {
        WorkOrder workOrder = requireWorkOrder(id);
        WorkOrderStatus previousStatus = workOrder.getStatus();
        workOrder.setStatus(request.status());

        WorkOrder saved = workOrderRepository.save(workOrder);
        addTimelineEvent(
            saved,
            "STATUS_CHANGED",
            "Status alterado de " + previousStatus.name() + " para " + saved.getStatus().name() + "."
        );

        auditService.record(
            "WORK_ORDER",
            "UPDATE_STATUS",
            "WORK_ORDER",
            String.valueOf(saved.getId()),
            "Status alterado de " + previousStatus.name() + " para " + saved.getStatus().name()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/details")
    public ResponseEntity<WorkOrderResponse> updateDetails(
        @PathVariable Long id,
        @Valid @RequestBody UpdateWorkOrderDetailsRequest request
    ) {
        WorkOrder workOrder = requireWorkOrder(id);

        workOrder.setPriority(Optional.ofNullable(request.priority()).orElse(workOrder.getPriority()));
        workOrder.setTechnicianName(request.technicianName());
        workOrder.setEstimatedCompletionDate(parseDate(request.estimatedCompletionDate()));
        workOrder.setServiceCost(request.serviceCost());
        workOrder.setCustomerPhone(request.customerPhone());
        workOrder.setNotes(request.notes());

        WorkOrder saved = workOrderRepository.save(workOrder);
        addTimelineEvent(saved, "DETAILS_UPDATED", "Dados operacionais da OS foram atualizados.");

        auditService.record(
            "WORK_ORDER",
            "UPDATE_DETAILS",
            "WORK_ORDER",
            String.valueOf(saved.getId()),
            "Detalhes da OS atualizados"
        );

        return ResponseEntity.ok(toResponse(saved));
    }


    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        WorkOrder workOrder = requireWorkOrder(id);
        String tenantId = TenantContext.getTenantId();

        timelineEventRepository.deleteByTenantIdAndWorkOrderId(tenantId, workOrder.getId());
        workOrderRepository.delete(workOrder);

        auditService.record(
            "WORK_ORDER",
            "DELETE",
            "WORK_ORDER",
            String.valueOf(id),
            "OS removida: " + workOrder.getOrderNumber()
        );

        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{id}/document")
    public ResponseEntity<ByteArrayResource> document(
        @PathVariable Long id,
        @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        WorkOrder workOrder = requireWorkOrder(id);
        byte[] content = generateWorkOrderDocument(workOrder);
        String filename = safeFileName(workOrder.getOrderNumber(), "ordem_servico") + ".html";

        return fileResponse(content, MediaType.TEXT_HTML, filename, download);
    }
    @GetMapping("/{id}/document/pdf")
    public ResponseEntity<ByteArrayResource> documentPdf(
        @PathVariable Long id,
        @RequestParam(name = "download", defaultValue = "true") boolean download
    ) {
        WorkOrder workOrder = requireWorkOrder(id);
        byte[] content = generateWorkOrderPdf(workOrder);
        String filename = safeFileName(workOrder.getOrderNumber(), "ordem_servico") + ".pdf";

        return fileResponse(content, MediaType.APPLICATION_PDF, filename, download);
    }


    @GetMapping("/{id}/attachment")
    public ResponseEntity<ByteArrayResource> attachment(
        @PathVariable Long id,
        @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        WorkOrder workOrder = requireWorkOrder(id);
        if (!workOrder.hasAttachment()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo nao encontrado para esta OS.");
        }

        String filename = safeFileName(workOrder.getAttachmentFileName(), "anexo_os");
        MediaType mediaType = resolveMediaType(workOrder.getAttachmentContentType());

        return fileResponse(workOrder.getAttachmentData(), mediaType, filename, true);
    }

    @GetMapping("/{id}/whatsapp-link")
    public ResponseEntity<Map<String, String>> whatsappLink(@PathVariable Long id) {
        WorkOrder workOrder = requireWorkOrder(id);
        String phone = normalizePhone(workOrder.getCustomerPhone());
        if (phone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telefone do cliente nao informado na OS.");
        }

        String message = String.format(
            Locale.ROOT,
            "Ola %s! Atualizacao da OS %s:%nStatus: %s%nPrioridade: %s%nEquipamento: %s%nPDF da OS pronto para envio em anexo.",
            workOrder.getCustomerName(),
            workOrder.getOrderNumber(),
            workOrder.getStatus().name().replace('_', ' '),
            workOrder.getPriority().name(),
            workOrder.getEquipment()
        );

        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String url = "https://wa.me/" + phone + "?text=" + encoded;

        String pdfFileName = safeFileName(workOrder.getOrderNumber(), "ordem_servico") + ".pdf";

        return ResponseEntity.ok(Map.of("url", url, "message", message, "pdfFileName", pdfFileName));
    }

    private WorkOrder buildWorkOrder(CreateWorkOrderRequest request) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setCustomerName(request.customerName());
        workOrder.setCustomerPhone(request.customerPhone());
        workOrder.setEquipment(request.equipment());
        workOrder.setDefectDescription(request.defectDescription());
        workOrder.setNotes(request.notes());
        workOrder.setPriority(Optional.ofNullable(request.priority()).orElse(WorkOrderPriority.MEDIA));
        workOrder.setTechnicianName(request.technicianName());
        workOrder.setEstimatedCompletionDate(parseDate(request.estimatedCompletionDate()));
        workOrder.setServiceCost(request.serviceCost());
        workOrder.setStatus(WorkOrderStatus.ENTRADA);
        return workOrder;
    }

    private void addTimelineEvent(WorkOrder workOrder, String eventType, String description) {
        WorkOrderTimelineEvent event = new WorkOrderTimelineEvent();
        event.setWorkOrderId(workOrder.getId());
        event.setEventType(eventType);
        event.setDescription(description);
        event.setCreatedBy(currentUsername());
        timelineEventRepository.save(event);
    }

    private void applyAttachment(WorkOrder workOrder, MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return;
        }

        if (attachment.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("Anexo excede o limite de 5 MB.");
        }

        String contentType = normalizeAttachmentContentType(attachment.getContentType());
        if (!ALLOWED_ATTACHMENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de anexo nao permitido. Use PDF, JPG ou PNG.");
        }

        try {
            workOrder.setAttachmentFileName(attachment.getOriginalFilename());
            workOrder.setAttachmentContentType(contentType);
            workOrder.setAttachmentData(attachment.getBytes());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Falha ao processar o anexo da OS.");
        }
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

    private WorkOrder requireWorkOrder(Long id) {
        String tenantId = TenantContext.getTenantId();
        return workOrderRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ordem de servico nao encontrada"));
    }

    private String normalizeAttachmentContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
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

    private byte[] generateWorkOrderDocument(WorkOrder workOrder) {
        String logoDataUri = loadBrandLogoDataUri();
        String logoMarkup = StringUtils.hasText(logoDataUri)
            ? "<div class=\"brand-mark\"><img src=\"" + logoDataUri + "\" alt=\"Logo DaniCell\" /></div>"
            : "";

        String html = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>OS {{orderNumber}} - DaniCell</title>
              <style>
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  padding: 24px;
                  font-family: Arial, Helvetica, sans-serif;
                  background: #eef3f8;
                  color: #0f172a;
                }
                .sheet {
                  max-width: 960px;
                  margin: 0 auto;
                  background: #ffffff;
                  border-radius: 24px;
                  overflow: hidden;
                  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.14);
                }
                .hero {
                  display: flex;
                  justify-content: space-between;
                  gap: 20px;
                  padding: 28px 30px;
                  color: #ffffff;
                  background: linear-gradient(135deg, #05070b 0%, #0f172a 44%, #1d4ed8 100%);
                }
                .hero small {
                  display: inline-block;
                  margin-bottom: 10px;
                  padding: 6px 10px;
                  border-radius: 999px;
                  background: rgba(255, 255, 255, 0.12);
                  letter-spacing: 0.08em;
                  text-transform: uppercase;
                }
                .hero h1 {
                  margin: 0 0 8px;
                  font-size: 30px;
                }
                .hero p {
                  margin: 0;
                  color: rgba(255, 255, 255, 0.84);
                  line-height: 1.5;
                }
                .brand-mark {
                  width: 220px;
                  min-width: 220px;
                  height: 118px;
                  border-radius: 18px;
                  overflow: hidden;
                  background: #020406;
                  border: 1px solid rgba(255, 255, 255, 0.16);
                }
                .brand-mark img {
                  width: 100%;
                  height: 100%;
                  display: block;
                  object-fit: cover;
                  object-position: center 74%;
                }
                .content {
                  padding: 28px 30px 18px;
                }
                .stats {
                  display: grid;
                  grid-template-columns: repeat(4, minmax(0, 1fr));
                  gap: 12px;
                  margin-bottom: 22px;
                }
                .stat {
                  padding: 14px 16px;
                  border-radius: 16px;
                  border: 1px solid #dbe5ef;
                  background: #f8fbff;
                }
                .stat span {
                  display: block;
                  margin-bottom: 6px;
                  font-size: 12px;
                  text-transform: uppercase;
                  letter-spacing: 0.08em;
                  color: #64748b;
                }
                .stat strong {
                  font-size: 17px;
                }
                .section {
                  margin-bottom: 18px;
                  padding: 20px;
                  border: 1px solid #dbe5ef;
                  border-radius: 18px;
                }
                .section h2 {
                  margin: 0 0 14px;
                  font-size: 18px;
                }
                .grid {
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 14px 18px;
                }
                .field.full {
                  grid-column: 1 / -1;
                }
                .field label {
                  display: block;
                  margin-bottom: 5px;
                  font-size: 12px;
                  text-transform: uppercase;
                  letter-spacing: 0.08em;
                  color: #64748b;
                }
                .field div {
                  font-size: 15px;
                  font-weight: 600;
                  line-height: 1.55;
                  word-break: break-word;
                }
                .note {
                  padding: 16px;
                  border-radius: 16px;
                  background: #f8fbff;
                  border: 1px solid #d5e2ff;
                  line-height: 1.65;
                }
                .signatures {
                  display: grid;
                  grid-template-columns: repeat(2, minmax(0, 1fr));
                  gap: 24px;
                  margin-top: 22px;
                }
                .signature {
                  padding-top: 14px;
                  border-top: 1px solid #cdd8e4;
                  color: #64748b;
                  font-size: 13px;
                }
                .footer {
                  padding: 0 30px 26px;
                  color: #64748b;
                  font-size: 12px;
                }
                @media (max-width: 760px) {
                  body { padding: 0; }
                  .sheet { border-radius: 0; }
                  .hero, .stats, .grid, .signatures { grid-template-columns: 1fr; display: grid; }
                  .hero { padding: 24px; }
                  .brand-mark { width: 100%; min-width: 0; }
                }
                @media print {
                  body { background: #ffffff; padding: 0; }
                  .sheet { box-shadow: none; border-radius: 0; }
                }
              </style>
            </head>
            <body>
              <main class="sheet">
                <section class="hero">
                  <div>
                    <small>DaniCell Assistencia Tecnica</small>
                    <h1>Ordem de Servico {{orderNumber}}</h1>
                    <p>Documento profissional para atendimento, diagnostico, acompanhamento e entrega do equipamento.</p>
                  </div>
                  {{logoMarkup}}
                </section>

                <section class="content">
                  <section class="stats">
                    <article class="stat"><span>Status</span><strong>{{status}}</strong></article>
                    <article class="stat"><span>Prioridade</span><strong>{{priority}}</strong></article>
                    <article class="stat"><span>Criada em</span><strong>{{createdAt}}</strong></article>
                    <article class="stat"><span>Atualizada em</span><strong>{{updatedAt}}</strong></article>
                  </section>

                  <section class="section">
                    <h2>Dados do Atendimento</h2>
                    <div class="grid">
                      <div class="field"><label>Cliente</label><div>{{customerName}}</div></div>
                      <div class="field"><label>Telefone</label><div>{{customerPhone}}</div></div>
                      <div class="field"><label>Equipamento</label><div>{{equipment}}</div></div>
                      <div class="field"><label>Tecnico responsavel</label><div>{{technicianName}}</div></div>
                      <div class="field"><label>Previsao de conclusao</label><div>{{estimatedCompletionDate}}</div></div>
                      <div class="field"><label>Valor do servico</label><div>{{serviceCost}}</div></div>
                      <div class="field full"><label>Defeito relatado</label><div>{{defectDescription}}</div></div>
                    </div>
                  </section>

                  <section class="section">
                    <h2>Controle Operacional</h2>
                    <div class="grid">
                      <div class="field"><label>Anexo</label><div>{{attachmentInfo}}</div></div>
                      <div class="field"><label>Emitido em</label><div>{{generatedAt}}</div></div>
                      <div class="field full"><label>Observacoes internas</label><div class="note">{{notes}}</div></div>
                    </div>

                    <div class="signatures">
                      <div class="signature">Assinatura do cliente</div>
                      <div class="signature">Assinatura do tecnico</div>
                    </div>
                  </section>
                </section>

                <footer class="footer">
                  Documento emitido por DaniCell Assistencia Tecnica para acompanhamento da OS {{orderNumber}}.
                </footer>
              </main>
            </body>
            </html>
            """;

        html = html.replace("{{orderNumber}}", escapeHtml(fallback(workOrder.getOrderNumber(), "Sem numero")));
        html = html.replace("{{logoMarkup}}", logoMarkup);
        html = html.replace("{{status}}", escapeHtml(formatEnum(workOrder.getStatus())));
        html = html.replace("{{priority}}", escapeHtml(formatEnum(workOrder.getPriority())));
        html = html.replace("{{createdAt}}", escapeHtml(formatDateTime(workOrder.getCreatedAt())));
        html = html.replace("{{updatedAt}}", escapeHtml(formatDateTime(workOrder.getUpdatedAt())));
        html = html.replace("{{customerName}}", escapeHtml(fallback(workOrder.getCustomerName(), "Nao informado")));
        html = html.replace("{{customerPhone}}", escapeHtml(fallback(workOrder.getCustomerPhone(), "Nao informado")));
        html = html.replace("{{equipment}}", escapeHtml(fallback(workOrder.getEquipment(), "Nao informado")));
        html = html.replace("{{technicianName}}", escapeHtml(fallback(workOrder.getTechnicianName(), "Nao atribuido")));
        html = html.replace("{{estimatedCompletionDate}}", escapeHtml(formatDate(workOrder.getEstimatedCompletionDate())));
        html = html.replace("{{serviceCost}}", escapeHtml(formatCurrency(workOrder.getServiceCost())));
        html = html.replace("{{defectDescription}}", toHtmlText(workOrder.getDefectDescription(), "Nao informado"));
        html = html.replace("{{notes}}", toHtmlText(workOrder.getNotes(), "Sem observacoes adicionais."));
        html = html.replace("{{attachmentInfo}}", escapeHtml(attachmentLabel(workOrder)));
        html = html.replace("{{generatedAt}}", escapeHtml(formatDateTime(LocalDateTime.now())));

        return html.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateWorkOrderPdf(WorkOrder workOrder) {
        String orderNumber = fallback(workOrder.getOrderNumber(), "Sem numero");
        List<PdfLine> lines = new ArrayList<>();

        lines.add(new PdfLine("DaniCell Assistencia Tecnica", true, 16f));
        lines.add(new PdfLine("Ordem de Servico " + orderNumber, true, 14f));
        lines.add(new PdfLine("Documento para atendimento, acompanhamento e entrega do equipamento.", false, 10.5f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Resumo", true, 12f));
        lines.add(new PdfLine("Status: " + formatEnum(workOrder.getStatus()), false, 11f));
        lines.add(new PdfLine("Prioridade: " + formatEnum(workOrder.getPriority()), false, 11f));
        lines.add(new PdfLine("Criada em: " + formatDateTime(workOrder.getCreatedAt()), false, 11f));
        lines.add(new PdfLine("Atualizada em: " + formatDateTime(workOrder.getUpdatedAt()), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Dados do atendimento", true, 12f));
        lines.add(new PdfLine("Cliente: " + normalizedText(workOrder.getCustomerName(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("Telefone: " + normalizedText(workOrder.getCustomerPhone(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("Equipamento: " + normalizedText(workOrder.getEquipment(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("Tecnico responsavel: " + normalizedText(workOrder.getTechnicianName(), "Nao atribuido"), false, 11f));
        lines.add(new PdfLine("Previsao de conclusao: " + formatDate(workOrder.getEstimatedCompletionDate()), false, 11f));
        lines.add(new PdfLine("Valor do servico: " + formatCurrency(workOrder.getServiceCost()), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Defeito relatado", true, 12f));
        lines.add(new PdfLine(normalizedText(workOrder.getDefectDescription(), "Nao informado"), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Observacoes internas", true, 12f));
        lines.add(new PdfLine(normalizedText(workOrder.getNotes(), "Sem observacoes adicionais."), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Anexo", true, 12f));
        lines.add(new PdfLine(attachmentLabel(workOrder), false, 11f));
        lines.add(new PdfLine("", false, 10f));

        lines.add(new PdfLine("Assinatura do cliente: ________________________________", false, 10.5f));
        lines.add(new PdfLine("Assinatura do tecnico: ________________________________", false, 10.5f));
        lines.add(new PdfLine("", false, 10f));
        lines.add(new PdfLine("Documento emitido em " + formatDateTime(LocalDateTime.now()) + ".", false, 10f));

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (PdfRenderer renderer = new PdfRenderer(document, loadBrandLogoBytes())) {
                renderer.writeLines(lines);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar PDF da OS.");
        }
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
            stream.showText("Ordem de servico tecnica com layout profissional para atendimento.");
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

    private String loadBrandLogoDataUri() {
        byte[] bytes = loadBrandLogoBytes();
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String attachmentLabel(WorkOrder workOrder) {
        if (!workOrder.hasAttachment()) {
            return "Sem anexo";
        }
        return "Anexo disponivel: " + fallback(workOrder.getAttachmentFileName(), "arquivo_os");
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

    private String toHtmlText(String value, String fallbackValue) {
        String base = StringUtils.hasText(value) ? value : fallbackValue;
        return escapeHtml(base).replace("\n", "<br />");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private WorkOrderResponse toResponse(WorkOrder workOrder) {
        return new WorkOrderResponse(
            workOrder.getId(),
            workOrder.getOrderNumber(),
            workOrder.getCustomerName(),
            workOrder.getCustomerPhone(),
            workOrder.getEquipment(),
            workOrder.getDefectDescription(),
            workOrder.getNotes(),
            workOrder.getPriority(),
            workOrder.getTechnicianName(),
            workOrder.getEstimatedCompletionDate() == null ? null : workOrder.getEstimatedCompletionDate().toString(),
            workOrder.getServiceCost(),
            workOrder.getStatus(),
            workOrder.getCreatedAt().toString(),
            workOrder.getUpdatedAt().toString(),
            workOrder.hasAttachment(),
            workOrder.getAttachmentFileName()
        );
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String requiredText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Campo obrigatorio: " + field);
        }
        return value;
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

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private BigDecimal parseCost(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.replace(",", ".");
        BigDecimal parsed = new BigDecimal(normalized);
        if (parsed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valor de servico nao pode ser negativo.");
        }
        return parsed;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    public record CreateWorkOrderRequest(
        @NotBlank String customerName,
        String customerPhone,
        @NotBlank String equipment,
        @NotBlank String defectDescription,
        String notes,
        WorkOrderPriority priority,
        String technicianName,
        String estimatedCompletionDate,
        @DecimalMin("0.00") BigDecimal serviceCost
    ) {
    }

    public record UpdateStatusRequest(
        @NotNull WorkOrderStatus status
    ) {
    }

    public record UpdateWorkOrderDetailsRequest(
        WorkOrderPriority priority,
        String technicianName,
        String estimatedCompletionDate,
        @DecimalMin("0.00") BigDecimal serviceCost,
        String customerPhone,
        String notes
    ) {
    }

    public record WorkOrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String customerPhone,
        String equipment,
        String defectDescription,
        String notes,
        WorkOrderPriority priority,
        String technicianName,
        String estimatedCompletionDate,
        BigDecimal serviceCost,
        WorkOrderStatus status,
        String createdAt,
        String updatedAt,
        boolean hasAttachment,
        String attachmentFileName
    ) {
    }

    public record WorkOrderTimelineResponse(
        Long id,
        String eventType,
        String description,
        String createdBy,
        String createdAt
    ) {
    }

    public record WorkOrderMetricsResponse(
        long total,
        long open,
        long urgent,
        Map<String, Long> byStatus,
        Map<String, Long> byPriority,
        BigDecimal averageTicket,
        BigDecimal forecastRevenue
    ) {
    }
}









