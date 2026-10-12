package com.contractvault.service;

import com.contractvault.exception.DocumentParsingException;
import com.contractvault.exception.DocumentValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentParserService {

    /**
     * Extracts text from a document. For scanned/image PDFs that contain no extractable
     * text, returns an empty string instead of throwing — the file is still stored and
     * the contract record is created. Q&A will indicate when no text is available.
     */
    public String extractText(Path filePath, String originalFilename) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new DocumentValidationException("Document file does not exist on server");
        }

        String extension = getFileExtension(originalFilename);
        validateDocumentSignature(filePath, extension);

        if ("pdf".equalsIgnoreCase(extension)) {
            return extractTextFromPdf(filePath);
        } else if ("docx".equalsIgnoreCase(extension)) {
            return extractTextFromDocx(filePath);
        } else if ("doc".equalsIgnoreCase(extension)) {
            return extractTextFromDoc(filePath);
        } else {
            throw new DocumentValidationException("Unsupported document format: ." + extension + ". Only PDF, DOCX, and DOC files are supported.");
        }
    }

    public void validateExtension(String filename) {
        String ext = getFileExtension(filename);
        if (!"pdf".equalsIgnoreCase(ext) && !"docx".equalsIgnoreCase(ext) && !"doc".equalsIgnoreCase(ext)) {
            throw new DocumentValidationException("Unsupported file type: ." + ext + ". Please upload a PDF, DOCX, or DOC document.");
        }
    }

    private String extractTextFromPdf(Path filePath) {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            // Return whatever was extracted (may be empty for scanned/image PDFs)
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse PDF document: the file appears to be invalid, password-protected, or corrupted.", e);
        }
    }

    private String extractTextFromDocx(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse Word document: the file appears to be invalid or corrupted.", e);
        }
    }

    private String extractTextFromDoc(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath);
             HWPFDocument document = new HWPFDocument(is);
             WordExtractor extractor = new WordExtractor(document)) {
            String text = extractor.getText();
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse DOC document: the file appears to be invalid or corrupted.", e);
        }
    }

    private void validateDocumentSignature(Path filePath, String extension) {
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read < 4) {
                throw new DocumentParsingException("File is too small or truncated to be a valid document.");
            }

            if ("pdf".equalsIgnoreCase(extension)) {
                // PDF magic bytes: %PDF (0x25, 0x50, 0x44, 0x46)
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    throw new DocumentParsingException("File content does not match standard PDF specification.");
                }
            } else if ("docx".equalsIgnoreCase(extension)) {
                // DOCX is a ZIP container: PK (0x50, 0x4B, 0x03, 0x04)
                if (header[0] != 0x50 || header[1] != 0x4B) {
                    throw new DocumentParsingException("File content does not match standard DOCX specification.");
                }
            } else if ("doc".equalsIgnoreCase(extension)) {
                // DOC is a Compound Document: D0 CF 11 E0
                if ((header[0] & 0xFF) != 0xD0 || (header[1] & 0xFF) != 0xCF
                        || (header[2] & 0xFF) != 0x11 || (header[3] & 0xFF) != 0xE0) {
                    throw new DocumentParsingException("File content does not match standard DOC specification.");
                }
            }
        } catch (DocumentParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to verify document signature", e);
        }
    }

    private String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
