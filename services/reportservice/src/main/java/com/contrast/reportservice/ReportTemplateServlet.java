package com.contrast.reportservice;

import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.freemarker.FreemarkerTemplateEngine;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

/**
 * ReportTemplateServlet - Processes shipping report templates using FreeMarker.
 * 
 * SECURITY: Implements template validation to prevent Server-Side Template Injection (SSTI).
 * Templates are validated against a whitelist of safe patterns and dangerous FreeMarker
 * built-ins are blocked to prevent arbitrary code execution.
 */
@WebServlet(name = "ReportTemplateServlet", urlPatterns = {"/template"})
public class ReportTemplateServlet extends HttpServlet {
    
    private FreemarkerTemplateEngine templateEngine;
    
    // Dangerous FreeMarker built-ins and patterns that enable code execution
    private static final List<Pattern> DANGEROUS_PATTERNS = Arrays.asList(
        Pattern.compile("\\?new\\s*\\(", Pattern.CASE_INSENSITIVE),           // ?new() - instantiate arbitrary classes
        Pattern.compile("\\?api\\b", Pattern.CASE_INSENSITIVE),               // ?api - access Java API
        Pattern.compile("\\?eval\\b", Pattern.CASE_INSENSITIVE),              // ?eval - evaluate expressions
        Pattern.compile("freemarker\\.template", Pattern.CASE_INSENSITIVE),   // FreeMarker internal classes
        Pattern.compile("java\\.lang\\.Runtime", Pattern.CASE_INSENSITIVE),   // Runtime class
        Pattern.compile("java\\.lang\\.Process", Pattern.CASE_INSENSITIVE),   // Process class
        Pattern.compile("java\\.lang\\.Class", Pattern.CASE_INSENSITIVE),     // Class class
        Pattern.compile("java\\.io\\.File", Pattern.CASE_INSENSITIVE),        // File class
        Pattern.compile("java\\.nio\\.file", Pattern.CASE_INSENSITIVE),       // NIO file operations
        Pattern.compile("\\.getClass\\s*\\(", Pattern.CASE_INSENSITIVE),      // getClass() reflection
        Pattern.compile("\\.forName\\s*\\(", Pattern.CASE_INSENSITIVE),       // Class.forName() reflection
        Pattern.compile("\\.newInstance\\s*\\(", Pattern.CASE_INSENSITIVE),   // newInstance() reflection
        Pattern.compile("exec\\s*\\(", Pattern.CASE_INSENSITIVE),             // exec() method
        Pattern.compile("<#assign", Pattern.CASE_INSENSITIVE),                // Variable assignment
        Pattern.compile("<#import", Pattern.CASE_INSENSITIVE),                // Import directive
        Pattern.compile("<#include", Pattern.CASE_INSENSITIVE),               // Include directive
        Pattern.compile("\\$\\{.*\\?.*\\(.*\\).*\\}", Pattern.DOTALL)         // Complex expressions with method calls
    );
    
    // Allowed safe patterns - only simple variable substitution and safe built-ins
    private static final Pattern SAFE_VARIABLE_PATTERN = Pattern.compile(
        "\\$\\{[a-zA-Z_][a-zA-Z0-9_]*(\\?[a-zA-Z_]+)?\\}"
    );
    
    @Override
    public void init() throws ServletException {
        super.init();
        templateEngine = new FreemarkerTemplateEngine();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"ok\",\"service\":\"reportservice\",\"endpoint\":\"/template\"}");
    }
    
    /**
     * Validates template content to prevent Server-Side Template Injection attacks.
     * Checks for dangerous FreeMarker built-ins and patterns that could enable code execution.
     * 
     * @param template The template content to validate
     * @return true if the template is safe, false if it contains dangerous patterns
     */
    private boolean isTemplateSafe(String template) {
        if (template == null || template.trim().isEmpty()) {
            return false;
        }
        
        // Check for dangerous patterns
        for (Pattern dangerousPattern : DANGEROUS_PATTERNS) {
            if (dangerousPattern.matcher(template).find()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Extracts a descriptive error message when a dangerous pattern is detected.
     * 
     * @param template The template content that failed validation
     * @return A user-friendly error message describing the security issue
     */
    private String getValidationErrorMessage(String template) {
        for (Pattern dangerousPattern : DANGEROUS_PATTERNS) {
            if (dangerousPattern.matcher(template).find()) {
                return "Template contains potentially dangerous pattern: " + dangerousPattern.pattern() + 
                       ". Only simple variable substitution is allowed (e.g., ${variableName}).";
            }
        }
        return "Template validation failed. Only simple variable substitution is allowed.";
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String template = request.getParameter("template");
        String shipmentId = request.getParameter("shipmentId");
        String recipientName = request.getParameter("recipientName");
        String origin = request.getParameter("origin");
        String destination = request.getParameter("destination");
        
        response.setContentType("application/json");
        
        if (template == null || template.trim().isEmpty()) {
            response.setStatus(400);
            response.getWriter().write("{\"error\": \"Template content is required\"}");
            return;
        }
        
        // SECURITY: Validate template to prevent Server-Side Template Injection
        if (!isTemplateSafe(template)) {
            response.setStatus(400);
            String errorMsg = getValidationErrorMessage(template);
            response.getWriter().write("{\"error\": " + escapeJsonString(errorMsg) + "}");
            return;
        }
        
        if (shipmentId == null || shipmentId.trim().isEmpty()) shipmentId = "N/A";
        if (recipientName == null || recipientName.trim().isEmpty()) recipientName = "N/A";
        if (origin == null || origin.trim().isEmpty()) origin = "N/A";
        if (destination == null || destination.trim().isEmpty()) destination = "N/A";
        
        try {
            // Process validated template with user data
            StringReader reader = new StringReader(template);
            StringWriter writer = new StringWriter();
            IContext context = templateEngine.createContext();
            context.put("shipmentId", shipmentId);
            context.put("recipientName", recipientName);
            context.put("origin", origin);
            context.put("destination", destination);
            context.put("date", java.time.LocalDate.now().toString());
            context.put("company", "Global Shipping Co.");
            
            templateEngine.process("report-template", context, reader, writer);
            
            String result = writer.toString();

            response.getWriter().write("{\"success\": true, \"output\": " + escapeJsonString(result) + "}");
            
        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\": " + escapeJsonString("Template processing failed: " + e.getMessage()) + "}");
        }
    }
    
    private String escapeJsonString(String text) {
        if (text == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
