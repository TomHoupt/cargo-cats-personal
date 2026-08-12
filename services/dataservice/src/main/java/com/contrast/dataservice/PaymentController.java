package com.contrast.dataservice;

import com.contrast.dataservice.entity.Shipment;
import com.contrast.dataservice.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(originPatterns = "*")
public class PaymentController {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    @Qualifier("creditCardsJdbcTemplate")
    private JdbcTemplate creditCardsJdbcTemplate;
    
    @Autowired
    private ShipmentRepository shipmentRepository;
    
    @GetMapping("/payments")
    public List<Map<String, Object>> executeRawQuery(
            @RequestParam(value = "creditCard", required = false) String creditCard,
            @RequestParam(value = "shipmentId", required = false) String shipmentId) {
        
        System.out.println("=== PAYMENT ENDPOINT REQUEST ===");
        System.out.println("Endpoint: /payments");
        System.out.println("Credit Card param: " + (creditCard != null ? creditCard : "null"));
        System.out.println("Shipment ID param: " + (shipmentId != null ? shipmentId : "null"));
        System.out.println("================================");
        
        try {
            List<Map<String, Object>> result = null;
            
            // Validate required parameters
            if (creditCard != null && !creditCard.isEmpty() && shipmentId != null && !shipmentId.isEmpty()) {
                // Validate shipmentId is a valid numeric ID to prevent SQL injection
                long shipmentIdLong;
                try {
                    shipmentIdLong = Long.parseLong(shipmentId);
                } catch (NumberFormatException e) {
                    return List.of(Map.of(
                        "error", true,
                        "message", "Invalid shipmentId: must be a numeric value",
                        "shipment_id_param", shipmentId
                    ));
                }
                
                // Verify the shipment exists before processing payment
                Optional<Shipment> shipmentOpt = shipmentRepository.findById(shipmentIdLong);
                if (!shipmentOpt.isPresent()) {
                    return List.of(Map.of(
                        "error", true,
                        "message", "Shipment not found",
                        "shipment_id", shipmentId
                    ));
                }
                
                // Create credit card table if it doesn't exist in the credit_cards database
                String createTableSql = "CREATE TABLE IF NOT EXISTS credit_card (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "card_number VARCHAR(255) NOT NULL, " +
                    "shipment_id BIGINT NOT NULL)";
                creditCardsJdbcTemplate.execute(createTableSql);
                
                // Insert credit card data using parameterized query to prevent SQL injection
                String insertSql = "INSERT INTO credit_card (card_number, shipment_id) VALUES (?, ?)";
                System.out.println("DEBUG: Executing parameterized SQL statement on credit_cards database");
                System.out.println("DEBUG: Credit Card parameter: " + creditCard);
                System.out.println("DEBUG: Shipment ID parameter: " + shipmentId);
                
                // Execute the insert statement using parameterized query
                System.out.println("DEBUG: Using creditCardsJdbcTemplate to execute query on credit_cards database");
                creditCardsJdbcTemplate.update(insertSql, creditCard, shipmentIdLong);
                
                // Update the shipment entity using the repository to set masked credit card
                Shipment shipment = shipmentOpt.get();
                String maskedCard = "XXXX-XXXX-XXXX-" + 
                    (creditCard.length() > 4 ? creditCard.substring(creditCard.length() - 4) : creditCard);
                shipment.setCreditCard(maskedCard);
                shipmentRepository.save(shipment);
                
                System.out.println("DEBUG: Updated shipment using repository (safe parameterized approach)");
                
                // Create response with success message
                result = List.of(Map.of(
                    "success", true,
                    "message", "Credit card stored in separate database for shipment",
                    "shipment_id", shipmentId,
                    "credit_card", maskedCard
                ));
            } else {
                result = List.of(Map.of(
                    "error", true,
                    "message", "Both creditCard and shipmentId parameters are required for payment processing",
                    "credit_card_param", creditCard != null ? creditCard : "none",
                    "shipment_id_param", shipmentId != null ? shipmentId : "none"
                ));
            }
            
            return result;
        } catch (Exception e) {
            return List.of(Map.of(
                "error", true,
                "message", e.getMessage(),
                "type", e.getClass().getSimpleName(),
                "credit_card_param", creditCard != null ? creditCard : "none",
                "shipment_id_param", shipmentId != null ? shipmentId : "none"
            ));
        }
    }
}
