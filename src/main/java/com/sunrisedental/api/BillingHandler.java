package com.sunrisedental.api;

import com.sunrisedental.dto.ClinicProfile;
import com.sunrisedental.dto.GenerateBillRequest;
import com.sunrisedental.dto.Receipt;
import com.sunrisedental.dto.ResponseFormat;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.Role;
import com.sunrisedental.service.ClinicFacade;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Billing endpoints: create a bill for an appointment, read the receipt, record the payment. */
public final class BillingHandler {

    private static final Logger LOG = Logger.getLogger(BillingHandler.class.getName());
    /** Inline styles are needed for the HTML receipt; everything else stays blocked. */
    private static final String HTML_CSP = "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'";

    private final ClinicFacade facade;
    private final RequestValidators validators;
    private final ReceiptStore store;
    private final ClinicProfile clinic;

    /**
     * Creates the handler.
     *
     * @param facade     the application facade
     * @param validators request validators
     * @param store      receipt file writer
     * @param clinic     clinic details for the receipts
     */
    public BillingHandler(ClinicFacade facade, RequestValidators validators, ReceiptStore store,
                          ClinicProfile clinic) {
        this.facade = facade;
        this.validators = validators;
        this.store = store;
        this.clinic = clinic;
    }

    /**
     * Registers the routes (both roles may bill and take payments).
     *
     * @param router the router
     */
    public void register(Router router) {
        Role[] staff = Role.values();
        router.add("POST", "/api/appointments/{id}/bill", this::create, staff);
        router.add("GET", "/api/bills/{id}", this::get, staff);
        router.add("POST", "/api/bills/{id}/pay", this::pay, staff);
    }

    private ApiResponse create(ApiRequest request) {
        long appointmentId = request.pathId("id");
        GenerateBillRequest billRequest = validators.generateBill(appointmentId, request.optionalJsonObject());
        Receipt receipt = facade.generateBill(billRequest, request.requireUser());
        return ApiResponse.created(withFiles(receipt), "/api/bills/" + receipt.billId());
    }

    private ApiResponse get(ApiRequest request) {
        long billId = request.pathId("id");
        ResponseFormat format = validators.receiptFormat(request.query());
        Receipt receipt = facade.getReceipt(billId);
        return switch (format) {
            case TEXT -> ApiResponse.text(200, "text/plain; charset=utf-8", ReceiptRenderer.text(receipt, clinic));
            case HTML -> ApiResponse.text(200, "text/html; charset=utf-8", ReceiptRenderer.html(receipt, clinic))
                    .header("Content-Security-Policy", HTML_CSP);
            default -> ApiResponse.ok(ReceiptRenderer.json(receipt, clinic));
        };
    }

    private ApiResponse pay(ApiRequest request) {
        long billId = request.pathId("id");
        PaymentMethod method = validators.payment(request.jsonObject());
        Receipt receipt = facade.payBill(billId, method);
        return ApiResponse.ok(withFiles(receipt));
    }

    /** Builds the JSON receipt and saves the printable files. A disk problem is logged, never fails the request. */
    private Map<String, Object> withFiles(Receipt receipt) {
        Map<String, Object> json = ReceiptRenderer.json(receipt, clinic);
        List<String> files = List.of();
        try {
            files = store.save(receipt);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not save receipt " + receipt.receiptNumber(), e);
        }
        json.put("files", files);
        return json;
    }
}
