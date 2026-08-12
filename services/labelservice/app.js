const express = require('express');
const PDFDocument = require('pdfkit');
const { v4: uuidv4 } = require('uuid');
const moment = require('moment');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// CORS middleware
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept');
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    next();
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({ status: 'OK', service: 'shipping-label-service', timestamp: new Date().toISOString() });
});

// Main endpoint to generate shipping label
app.post('/generate-label', (req, res) => {
    try {
        const { firstName, lastName, address, trackingNumber } = req.body;

        // Validate required fields
        if (!firstName || !lastName || !address) {
            return res.status(400).json({
                error: 'Missing required fields',
                required: ['firstName', 'lastName', 'address']
            });
        }
        
        // Validate that firstName and lastName are strings
        if (typeof firstName !== 'string' || typeof lastName !== 'string' || typeof address !== 'string') {
            return res.status(400).json({
                error: 'Invalid field types',
                message: 'firstName, lastName, and address must be strings'
            });
        }
        
        // Safe string processing: capitalize first letter, lowercase the rest
        const processedFirstName = firstName.charAt(0).toUpperCase() + firstName.slice(1).toLowerCase();
        const processedLastName = lastName.charAt(0).toUpperCase() + lastName.slice(1).toLowerCase();

        // Use provided tracking number or generate one
        const finalTrackingNumber = trackingNumber || `TRACK-${uuidv4().substring(0, 8).toUpperCase()}`;
        const currentDate = moment().format('MMMM DD, YYYY');
        const deliveryDate = moment().add(3, 'days').format('MMMM DD, YYYY');

        // Create PDF document
        const doc = new PDFDocument({ size: 'A4', margin: 50 });

        // Set response headers for PDF
        res.setHeader('Content-Type', 'application/pdf');
        res.setHeader('Content-Disposition', `attachment; filename="shipping-label-${finalTrackingNumber}.pdf"`);

        // Pipe PDF to response
        doc.pipe(res);

        // Company header
        doc.fontSize(24)
           .fillColor('#2c3e50')
           .text('CARGO CATS SHIPPING', { align: 'center' })
           .moveDown(0.5);

        // Tracking number prominently displayed
        doc.fontSize(16)
           .fillColor('#e74c3c')
           .text(`Tracking Number: ${finalTrackingNumber}`, { align: 'center' })
           .moveDown(1);

        // Draw a line separator
        doc.strokeColor('#bdc3c7')
           .lineWidth(2)
           .moveTo(50, doc.y)
           .lineTo(545, doc.y)
           .stroke()
           .moveDown(1);

        // Shipping details section
        doc.fontSize(18)
           .fillColor('#2c3e50')
           .text('SHIP TO:', 50, doc.y)
           .moveDown(0.5);

        // Recipient information
        doc.fontSize(14)
           .fillColor('#34495e')
           .text(`${processedFirstName} ${processedLastName}`, 50, doc.y, { lineGap: 5 })
           .text(address, 50, doc.y, { lineGap: 5 })
           .moveDown(1);

        // Service information box
        const boxY = doc.y;
        doc.rect(50, boxY, 495, 120)
           .strokeColor('#bdc3c7')
           .stroke();

        doc.fontSize(12)
           .fillColor('#2c3e50')
           .text('SERVICE DETAILS:', 60, boxY + 10)
           .text(`Ship Date: ${currentDate}`, 60, boxY + 30)
           .text(`Expected Delivery: ${deliveryDate}`, 60, boxY + 50)
           .text('Service Type: Standard Ground', 60, boxY + 70)
           .text('Weight: 2.5 lbs', 60, boxY + 90);

        // Barcode simulation (using text representation)
        doc.y = boxY + 140;
        doc.fontSize(10)
           .fillColor('#2c3e50')
           .text('TRACKING BARCODE:', { align: 'center' })
           .moveDown(0.3);

        // Simple barcode representation
        const barcodeText = finalTrackingNumber.replace(/-/g, '');
        doc.fontSize(20)
           .font('Courier')
           .text('||||| | || ||| || | ||| | || ||||| | ||| ||', { align: 'center' })
           .moveDown(0.3);

        doc.fontSize(12)
           .font('Helvetica')
           .text(barcodeText, { align: 'center' })
           .moveDown(1);

        // Footer with company info
        doc.fontSize(10)
           .fillColor('#7f8c8d')
           .text('Cargo Cats Shipping - Safe and Reliable Pet Transport', { align: 'center' })
           .text('For questions about your shipment, visit cargocats.localhost', { align: 'center' })
           .moveDown(0.5);

        // QR code placeholder
        doc.fontSize(8)
           .text('Scan for tracking updates:', 450, doc.y - 60)
           .rect(450, doc.y - 50, 40, 40)
           .strokeColor('#bdc3c7')
           .stroke()
           .fontSize(6)
           .text('QR', 465, doc.y - 35);

        // Finalize PDF
        doc.end();

    } catch (error) {
        console.error('Error generating shipping label:', error);
        res.status(500).json({
            error: 'Internal server error',
            message: 'Failed to generate shipping label'
        });
    }
});

// Error handling middleware
app.use((err, req, res, next) => {
    console.error('Unhandled error:', err);
    res.status(500).json({
        error: 'Internal server error',
        message: err.message
    });
});

// 404 handler
app.use((req, res) => {
    res.status(404).json({
        error: 'Not found',
        message: 'The requested endpoint does not exist'
    });
});

// Start server
app.listen(PORT, () => {
    console.log(`Shipping label service running on port ${PORT}`);
    console.log(`Health check: http://localhost:${PORT}/health`);
    console.log(`Generate label: POST http://localhost:${PORT}/generate-label`);
});

module.exports = app;