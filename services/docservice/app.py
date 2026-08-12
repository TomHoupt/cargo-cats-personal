#!/usr/bin/env python3
"""
DOCX Document Processor - Document Metadata Extraction Service
Processes DOCX files and extracts document metadata and content for analysis.
"""

import os
import zipfile
from flask import Flask, request, jsonify, render_template_string
import logging
from lxml import etree as lxml_etree

# Configure logging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max file size

# HTML template for the UI
HTML_TEMPLATE = '''
<!DOCTYPE html>
<html>
<head>
    <title>DOCX Document Processor</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; background-color: #f5f5f5; }
        .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .info { background-color: #e7f3ff; border: 1px solid #b3d9ff; color: #004085; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
        .upload-area { border: 2px dashed #ddd; padding: 30px; text-align: center; margin: 20px 0; border-radius: 5px; }
        .results { background-color: #f8f9fa; padding: 20px; border-radius: 5px; margin-top: 20px; }
        .error { background-color: #f8d7da; border: 1px solid #f5c6cb; color: #721c24; padding: 15px; border-radius: 5px; margin: 10px 0; }
        .success { background-color: #d4edda; border: 1px solid #c3e6cb; color: #155724; padding: 15px; border-radius: 5px; margin: 10px 0; }
        button { background-color: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 5px; cursor: pointer; }
        button:hover { background-color: #0056b3; }
        pre { background-color: #f8f9fa; padding: 15px; border-radius: 5px; overflow-x: auto; }
        .feature-info { background-color: #f8f9fa; border: 1px solid #dee2e6; padding: 15px; border-radius: 5px; margin: 20px 0; }
    </style>
</head>
<body>
    <div class="container">
        <h1>� DOCX Document Processor</h1>
        
        <div class="info">
            <strong>Document Analysis Service:</strong> Upload DOCX files to extract metadata, content, and document structure information.
        </div>

        <div class="feature-info">
            <h3>🔍 Processing Features:</h3>
            <p>This service analyzes DOCX files by extracting and parsing their internal XML structure to provide:</p>
            <ul>
                <li>Document metadata and properties</li>
                <li>Text content extraction</li>
                <li>Document structure analysis</li>
                <li>File format validation</li>
            </ul>
            <p><strong>Supported Format:</strong> Microsoft Word DOCX documents</p>
        </div>

        <h2>� Upload DOCX Document</h2>
        <form id="uploadForm" enctype="multipart/form-data">
            <div class="upload-area">
                <input type="file" id="fileInput" name="file" accept=".docx" required>
                <p>Select a DOCX file to analyze and extract information</p>
            </div>
            <button type="submit">Process Document</button>
        </form>

        <div id="results"></div>

        <h3>ℹ️ How It Works:</h3>
        <p>DOCX files are processed by:</p>
        <ol>
            <li>Extracting the ZIP archive structure</li>
            <li>Parsing internal XML documents</li>
            <li>Analyzing document properties and content</li>
            <li>Returning structured metadata</li>
        </ol>
    </div>

    <script>
        document.getElementById('uploadForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const fileInput = document.getElementById('fileInput');
            const resultsDiv = document.getElementById('results');
            
            if (!fileInput.files[0]) {
                resultsDiv.innerHTML = '<div class="error">Please select a file</div>';
                return;
            }

            const formData = new FormData();
            formData.append('file', fileInput.files[0]);

            resultsDiv.innerHTML = '<p>Processing document...</p>';

            try {
                const response = await fetch('/process-docx', {
                    method: 'POST',
                    body: formData
                });

                const result = await response.json();
                
                if (response.ok) {
                    resultsDiv.innerHTML = `
                        <div class="results">
                            <h3>✅ Document Analysis Results:</h3>
                            <pre>${JSON.stringify(result, null, 2)}</pre>
                        </div>
                    `;
                } else {
                    resultsDiv.innerHTML = `<div class="error">Error: ${result.error}</div>`;
                }
            } catch (error) {
                resultsDiv.innerHTML = `<div class="error">Network error: ${error.message}</div>`;
            }
        });
    </script>
</body>
</html>
'''


def xml_parse(xml_content):
    """
    Parse XML content and extract document structure and text.
    Uses lxml parser for comprehensive XML processing.
    """
    logger.debug("Processing XML content with lxml parser...")
    
    try:
        logger.debug("Parsing XML document...")
        # Configure XML parser with secure settings to prevent XXE attacks
        parser = lxml_etree.XMLParser(
            resolve_entities=False,  # Disable external entity resolution (XXE protection)
            no_network=True,         # Block all network access
            load_dtd=False,          # Disable DTD loading
            dtd_validation=False,    # Skip validation for performance
            recover=True,            # Attempt recovery from errors
            huge_tree=False          # Prevent billion laughs and other entity expansion attacks
        )
        
        root = lxml_etree.fromstring(xml_content.encode('utf-8'), parser)
        
        # Extract text content from document
        text_content = []
        for elem in root.iter():
            if elem.text and elem.text.strip():
                text_content.append(elem.text.strip())
            if elem.tail and elem.tail.strip():
                text_content.append(elem.tail.strip())
        
        return {
            "parser": "lxml",
            "root_tag": root.tag,
            "text_content": text_content[:10],  # Show first 10 text elements
            "xml_output": lxml_etree.tostring(root, encoding='unicode', pretty_print=True)[:2000],
            "total_elements": len(list(root.iter())),
            "status": "Successfully processed XML document"
        }
        
    except Exception as e:
        logger.error(f"XML parsing failed: {e}")
        return {
            "parser": "lxml (FAILED)",
            "error": str(e),
            "content_preview": xml_content[:200] + "..." if len(xml_content) > 200 else xml_content,
            "status": "Parsing failed"
        }


def process_docx_file(file_obj, filename):
    """
    Process DOCX file and extract document content and metadata.
    DOCX files are ZIP archives containing XML documents.
    """
    logger.info(f"Processing DOCX file: {filename}")
    
    # Get file size from the file object
    file_obj.seek(0, 2)  # Seek to end
    file_size = file_obj.tell()
    file_obj.seek(0)  # Reset to beginning
    
    results = {
        "filename": filename,
        "size": file_size,
        "xml_files": {}
    }
    
    try:
        with zipfile.ZipFile(file_obj, 'r') as docx_zip:
            # List all files in DOCX archive
            file_list = docx_zip.namelist()
            results["internal_files"] = file_list
            
            # Process key XML files for content extraction
            xml_files_to_check = [
                'word/document.xml',  # Main document content
                'word/styles.xml',    # Document styles
                'word/settings.xml',  # Document settings
                'docProps/core.xml',  # Core properties
                'docProps/app.xml'    # Application properties
            ]
            
            for xml_file in xml_files_to_check:
                if xml_file in file_list:
                    logger.debug(f"Processing {xml_file}")
                    
                    try:
                        xml_content = docx_zip.read(xml_file).decode('utf-8')
                        
                        # Parse XML content
                        parse_result = xml_parse(xml_content)
                        results["xml_files"][xml_file] = parse_result
                        
                    except Exception as e:
                        logger.error(f"Error processing {xml_file}: {e}")
                        results["xml_files"][xml_file] = {"error": str(e)}
            
            return results
            
    except zipfile.BadZipFile:
        raise ValueError("Invalid DOCX file - not a valid ZIP archive")
    except Exception as e:
        logger.error(f"Error processing DOCX: {e}")
        raise


@app.route('/')
def index():
    """Serve the main page with file upload interface"""
    return render_template_string(HTML_TEMPLATE)


@app.route('/process-docx', methods=['POST'])
def process_docx():
    """Process uploaded DOCX file and extract content and metadata"""
    logger.info("Received DOCX processing request")
    
    if 'file' not in request.files:
        return jsonify({"error": "No file uploaded"}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "No file selected"}), 400
    
    if not file.filename.lower().endswith('.docx'):
        return jsonify({"error": "Only DOCX files are supported"}), 400
    
    try:
        logger.info(f"Processing DOCX file in memory: {file.filename}")
        
        # Process the DOCX file directly from memory
        results = process_docx_file(file, file.filename)
        
        return jsonify({
            "status": "success",
            "message": "DOCX processed successfully",
            "results": results
        })
        
    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        logger.error(f"Processing error: {e}")
        return jsonify({"error": f"Processing failed: {str(e)}"}), 500


@app.route('/health')
def health():
    """Health check endpoint"""
    return jsonify({"status": "healthy", "service": "docx-processor"})


if __name__ == '__main__':
    logger.info("Starting DOCX Document Processor Service")
    app.run(host='0.0.0.0', port=5000, debug=False)
