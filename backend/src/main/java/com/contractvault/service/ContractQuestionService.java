package com.contractvault.service;

import com.contractvault.dto.QuestionResponse;
import com.contractvault.exception.DocumentValidationException;
import com.contractvault.exception.ResourceNotFoundException;
import com.contractvault.model.Contract;
import com.contractvault.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractQuestionService {

    private final ContractRepository contractRepository;

    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
            "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
            "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
            "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
            "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
            "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
            "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
            "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
            "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those",
            "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're",
            "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while",
            "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll",
            "you're", "you've", "your", "yours", "yourself", "yourselves", "tell", "please", "find", "show",
            "give", "stated", "mentioned", "contain", "contains"
    )));

    // Common query typo corrections
    private static final Map<String, String> TYPO_MAP = new HashMap<>();
    static {
        TYPO_MAP.put("cotract", "contract");
        TYPO_MAP.put("cntract", "contract");
        TYPO_MAP.put("contraact", "contract");
        TYPO_MAP.put("aggreement", "agreement");
        TYPO_MAP.put("agreemnt", "agreement");
        TYPO_MAP.put("valur", "value");
        TYPO_MAP.put("valu", "value");
        TYPO_MAP.put("pyament", "payment");
        TYPO_MAP.put("paymnt", "payment");
        TYPO_MAP.put("duratin", "duration");
        TYPO_MAP.put("durtion", "duration");
        TYPO_MAP.put("slaary", "salary");
        TYPO_MAP.put("stipnd", "stipend");
        TYPO_MAP.put("termintion", "termination");
        TYPO_MAP.put("liabilty", "liability");
        TYPO_MAP.put("juridiction", "jurisdiction");
        TYPO_MAP.put("govrning", "governing");
        TYPO_MAP.put("govering", "governing");
        TYPO_MAP.put("notce", "notice");
        TYPO_MAP.put("invoce", "invoice");
        TYPO_MAP.put("partes", "parties");
        TYPO_MAP.put("compnay", "company");
        TYPO_MAP.put("emplyee", "employee");
    }

    // Comprehensive domain synonym map for contract terminology
    private static final Map<String, Set<String>> SYNONYMS = new HashMap<>();

    static {
        // Value / Price / Financials
        addSynonyms("value", "cost", "price", "fee", "fees", "amount", "sum", "total", "compensation", "stipend", "salary", "remuneration", "payment", "rate", "budget", "pricing", "charges", "worth", "payable", "inr", "usd", "rupees", "dollars", "rs", "euro", "eur");
        addSynonyms("cost", "value", "price", "fee", "fees", "amount", "sum", "total", "expense", "charges", "budget", "payment");
        addSynonyms("price", "value", "cost", "fee", "fees", "amount", "sum", "total", "pricing", "rate", "rates", "payment");
        addSynonyms("fee", "value", "cost", "price", "fees", "amount", "sum", "compensation", "stipend", "salary", "charges", "rate", "payment");
        addSynonyms("fees", "value", "cost", "price", "fee", "amount", "sum", "compensation", "stipend", "salary", "charges", "rate", "payment");
        addSynonyms("amount", "value", "sum", "total", "fee", "fees", "price", "cost", "payment", "compensation", "stipend", "salary", "rupees", "dollars");
        addSynonyms("worth", "value", "price", "cost", "amount", "fee", "total");
        addSynonyms("salary", "stipend", "pay", "payment", "fee", "cost", "price", "amount", "rupees", "inr", "compensation", "remuneration", "wage", "value");
        addSynonyms("stipend", "salary", "pay", "payment", "fee", "cost", "price", "amount", "rupees", "inr", "compensation", "remuneration", "wage", "value");
        addSynonyms("payment", "pay", "stipend", "salary", "paid", "payable", "fee", "cost", "invoice", "compensation", "amount", "value", "schedule", "milestone", "installment");
        addSynonyms("pay", "stipend", "salary", "payment", "paid", "payable", "fee", "cost", "invoice", "compensation", "remuneration", "amount", "value");
        addSynonyms("compensation", "salary", "stipend", "remuneration", "fee", "pay", "payment", "amount", "value");

        // Dates / Timeline
        addSynonyms("date", "dates", "effective", "commence", "commencement", "execution", "signed", "signing", "dated", "calendar", "day");
        addSynonyms("effective", "date", "commence", "commencement", "start", "begins", "execution", "valid", "validity");
        addSynonyms("commence", "start", "begins", "beginning", "commencement", "effective", "date");
        addSynonyms("start", "begins", "beginning", "commence", "commencement", "duration", "date", "effective");
        addSynonyms("end", "ending", "ends", "expire", "expiration", "finish", "termination", "duration", "expiry");
        addSynonyms("expire", "end", "expiry", "expiration", "ends", "termination", "duration", "validity");

        // Duration / Term
        addSynonyms("duration", "period", "term", "valid", "validity", "tenure", "years", "months", "days", "expire", "expiration", "end", "ends", "ending", "start", "begins", "beginning", "commence", "timeline");
        addSynonyms("term", "duration", "period", "validity", "tenure", "years", "months", "days", "termination", "valid", "timeline");
        addSynonyms("period", "duration", "term", "validity", "tenure", "months", "days", "notice", "time");

        // Parties / Entities
        addSynonyms("parties", "party", "between", "company", "employee", "employer", "candidate", "client", "customer", "vendor", "provider", "supplier", "contractor", "consultant", "signatory", "entities");
        addSynonyms("party", "parties", "between", "company", "employee", "employer", "client", "vendor", "provider", "contractor", "signatory");
        addSynonyms("client", "party", "parties", "customer", "company", "buyer");
        addSynonyms("vendor", "party", "parties", "provider", "supplier", "contractor", "consultant", "seller");
        addSynonyms("provider", "vendor", "supplier", "contractor", "consultant", "party", "parties");
        addSynonyms("company", "employer", "organization", "party", "parties", "client", "firm");
        addSynonyms("employer", "company", "organization", "party", "parties");
        addSynonyms("employee", "candidate", "intern", "appointed", "party", "parties", "person", "individual");
        addSynonyms("candidate", "employee", "intern", "individual");
        addSynonyms("intern", "internship", "candidate", "employee", "stipend", "duration");

        // Termination / Notice
        addSynonyms("termination", "terminate", "cancel", "cancellation", "end", "notice", "misconduct", "breach", "default", "discharge", "severance");
        addSynonyms("terminate", "termination", "cancel", "cancellation", "end", "notice", "misconduct", "breach", "default");
        addSynonyms("cancel", "termination", "terminate", "cancellation", "notice", "revoke");
        addSynonyms("notice", "period", "termination", "terminate", "days", "cancel", "written", "written notice", "mail", "contact");

        // Legal / Governing / Disputes
        addSynonyms("court", "dispute", "disputes", "jurisdiction", "governing", "law", "delhi", "india", "arbitration", "tribunal", "california", "new york");
        addSynonyms("dispute", "disputes", "court", "jurisdiction", "law", "governing", "arbitration", "resolution");
        addSynonyms("jurisdiction", "court", "courts", "governing", "law", "dispute", "laws", "venue");
        addSynonyms("governing", "law", "jurisdiction", "court", "dispute", "applicable");
        addSynonyms("law", "governing", "jurisdiction", "court", "laws", "applicable", "statutory", "legal");

        // Confidentiality & NDA
        addSynonyms("confidentiality", "confidential", "secret", "disclosure", "nondisclosure", "nda", "proprietary", "trade secret");
        addSynonyms("confidential", "confidentiality", "secret", "disclosure", "nondisclosure", "nda", "proprietary");

        // Liability & Indemnity
        addSynonyms("liability", "liable", "indemnify", "indemnification", "indemnity", "damages", "responsible", "claims", "loss", "limitation");
        addSynonyms("indemnity", "indemnify", "indemnification", "liability", "damages", "hold harmless");

        // Intellectual Property
        addSynonyms("intellectual", "property", "ip", "copyright", "patent", "trademark", "ownership", "rights", "license");
        addSynonyms("ip", "intellectual", "property", "copyright", "patent", "trademark", "ownership");

        // Scope & Duties
        addSynonyms("scope", "services", "deliverables", "obligations", "duties", "responsibilities", "work", "tasks", "sow");
        addSynonyms("services", "scope", "deliverables", "obligations", "duties", "work", "tasks");
        addSynonyms("deliverables", "services", "scope", "milestones", "work");
        addSynonyms("role", "position", "designation", "job", "intern", "internship", "responsibilities", "duties", "task", "work", "title");
        addSynonyms("hours", "schedule", "timing", "timings", "shift", "working", "daily", "time", "days");
        addSynonyms("working", "hours", "schedule", "work", "timing");
        addSynonyms("schedule", "hours", "timings", "working", "days", "shift", "payment", "timeline");
        addSynonyms("address", "location", "located", "drive", "sector", "office", "city");
        addSynonyms("email", "phone", "contact", "reach", "communication", "address");
    }

    private static void addSynonyms(String key, String... values) {
        SYNONYMS.computeIfAbsent(key, k -> new HashSet<>()).addAll(Arrays.asList(values));
    }

    private static final Pattern WORD_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern NUMBERED_SECTION_PATTERN = Pattern.compile(
            "^(?:\\d+(?:\\.\\d+)*[\\.\\)]\\s+[A-Za-z].*|Section\\s+\\d+.*|Article\\s+[IVX\\d]+.*|Clause\\s+\\d+.*)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern UPPERCASE_HEADER_PATTERN = Pattern.compile(
            "^[A-Z][A-Z0-9\\s,–-]{3,}:?$"
    );

    public ContractQuestionService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Transactional(readOnly = true)
    public QuestionResponse answerQuestion(Long contractId, String question) {
        if (!StringUtils.hasText(question)) {
            throw new DocumentValidationException("Question cannot be blank");
        }

        String trimmedQuestion = question.trim();
        if (trimmedQuestion.length() > 500) {
            throw new DocumentValidationException("Question cannot exceed 500 characters");
        }

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + contractId));

        if (!StringUtils.hasText(contract.getOriginalFilename()) && !StringUtils.hasText(contract.getStorageKey())) {
            throw new DocumentValidationException("Contract #" + contractId + " has no attached document file.");
        }

        String extractedText = contract.getExtractedText();
        if (!StringUtils.hasText(extractedText)) {
            throw new DocumentValidationException("Contract #" + contractId + " has no extracted document text available for Q&A.");
        }

        return findAnswerInText(contractId, trimmedQuestion, extractedText);
    }

    public QuestionResponse findAnswerInText(Long contractId, String question, String rawText) {
        String normalizedText = normalizeContractText(rawText);
        List<String> queryTokens = tokenizeAndFilter(question);

        if (queryTokens.isEmpty()) {
            return new QuestionResponse(
                    contractId,
                    question,
                    "I couldn't find a relevant answer in this contract. Please ask a more specific question.",
                    false,
                    null
            );
        }

        Set<String> expandedTerms = expandQueryTerms(queryTokens);
        List<Passage> passages = extractPassages(normalizedText);

        if (passages.isEmpty()) {
            return new QuestionResponse(
                    contractId,
                    question,
                    "I couldn't find a relevant answer in this contract.",
                    false,
                    null
            );
        }

        Passage bestPassage = null;
        double bestScore = 0.0;
        int bestMatchCount = 0;

        for (Passage passage : passages) {
            int[] matchStats = new int[1];
            double score = scorePassage(question, queryTokens, expandedTerms, passage, matchStats);
            if (score > bestScore) {
                bestScore = score;
                bestPassage = passage;
                bestMatchCount = matchStats[0];
            }
        }

        // Require confidence threshold: must have genuine matching tokens and sufficient score
        if (bestScore < 4.0 || bestPassage == null || bestMatchCount == 0) {
            return new QuestionResponse(
                    contractId,
                    question,
                    "I couldn't find a relevant answer in this contract. Please ask a specific question about the contract's terms, duration, payment, parties, or clauses.",
                    false,
                    null
            );
        }

        String answerText = bestPassage.extractBestSentenceAnswer(question, queryTokens, expandedTerms);
        String sourceExcerpt = bestPassage.formatExcerpt();

        return new QuestionResponse(
                contractId,
                question,
                answerText,
                true,
                sourceExcerpt
        );
    }

    private String normalizeContractText(String text) {
        if (text == null) return "";
        String s = text.replace('\u25a0', '₹');
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201c', '"').replace('\u201d', '"')
                .replace('\u2013', '-').replace('\u2014', '-');
        // Clean repeated page number headers/footers
        s = s.replaceAll("(?i)Page\\s+\\d+\\s+of\\s+\\d+", "");
        return s;
    }

    public static class Passage {
        final String title;
        final String body;

        public Passage(String title, String body) {
            this.title = title != null ? title.trim() : "";
            this.body = body != null ? body.trim() : "";
        }

        public String getFullContent() {
            if (title.isEmpty()) return body;
            if (body.isEmpty()) return title;
            return title + " " + body;
        }

        public String extractBestSentenceAnswer(String rawQuestion, List<String> queryTokens, Set<String> expandedTerms) {
            String cleanBody = cleanFormatting(body);
            if (cleanBody.isEmpty()) {
                return cleanFormatting(title);
            }

            // Split into sentences
            String[] sentences = cleanBody.split("(?<=[.!?])\\s+");
            if (sentences.length <= 1) {
                return cleanBody;
            }

            String qLower = rawQuestion.toLowerCase();
            String bestSentence = "";
            double bestSentScore = -1.0;

            for (String sentence : sentences) {
                String sTrim = sentence.trim();
                if (sTrim.length() < 10) continue;
                String sLower = sTrim.toLowerCase();
                double score = 0.0;

                for (String token : queryTokens) {
                    if (sLower.contains(token)) score += 3.5;
                }
                for (String term : expandedTerms) {
                    if (sLower.contains(term)) score += 1.8;
                }
                // Boost for amounts / currency
                if (qLower.contains("value") || qLower.contains("fee") || qLower.contains("cost") || qLower.contains("price") || qLower.contains("amount") || qLower.contains("pay")) {
                    if (sLower.matches(".*(?:\\$|₹|rs\\.?|inr|usd|fee|price|cost|amount|sum|stipend|salary).*\\d+.*")) {
                        score += 5.0;
                    }
                }
                // Boost for explicit calendar date if date asked
                if (qLower.contains("date") || qLower.contains("effective") || qLower.contains("signed") || qLower.contains("start")) {
                    if (sLower.matches(".*\\b(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+\\d{4}\\b.*") ||
                        sLower.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")) {
                        score += 8.0;
                    }
                }
                // Boost for duration / notice
                if (qLower.contains("duration") || qLower.contains("term") || qLower.contains("how long")) {
                    if (sLower.matches(".*\\b(?:one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s*(?:years?|months?|days?|weeks?)\\b.*")) {
                        score += 5.0;
                    }
                }

                if (score > bestSentScore) {
                    bestSentScore = score;
                    bestSentence = sTrim;
                }
            }

            if (!bestSentence.isEmpty() && bestSentScore > 0) {
                return bestSentence;
            }
            return cleanBody;
        }

        public String formatExcerpt() {
            String full = getFullContent();
            if (full.length() > 400) {
                return cleanFormatting(full.substring(0, 397)) + "...";
            }
            return cleanFormatting(full);
        }

        private static String cleanFormatting(String input) {
            return input.replaceAll("\\s+", " ").trim();
        }
    }

    private List<Passage> extractPassages(String text) {
        List<Passage> passages = new ArrayList<>();
        String[] lines = text.split("\n");

        String currentTitle = "";
        List<String> currentBody = new ArrayList<>();
        List<String> preambleLines = new ArrayList<>();
        boolean seenFirstSection = false;
        boolean inTableOfContents = false;

        for (String line : lines) {
            String stripped = line.trim();
            if (stripped.isEmpty()) continue;

            // Detect Table of Contents marker
            if (stripped.equalsIgnoreCase("TABLE OF CONTENTS") || stripped.equalsIgnoreCase("CONTENTS") || stripped.equalsIgnoreCase("INDEX")) {
                inTableOfContents = true;
                continue;
            }

            if (inTableOfContents) {
                // Check if Table of Contents has ended (actual contract preamble or section starts)
                if (stripped.toLowerCase().startsWith("this ") || stripped.toLowerCase().startsWith("whereas") ||
                    (stripped.startsWith("1.") && stripped.toLowerCase().contains("agreement"))) {
                    inTableOfContents = false;
                } else if (stripped.matches("^\\d+[\\.\\)]\\s+.*")) {
                    // Header listed in Table of Contents - skip to avoid creating empty passages
                    continue;
                }
            }

            boolean isNumberedSection = NUMBERED_SECTION_PATTERN.matcher(stripped).find();
            boolean isUppercaseHeader = UPPERCASE_HEADER_PATTERN.matcher(stripped).matches();

            if (isNumberedSection || isUppercaseHeader) {
                seenFirstSection = true;
                if (!currentTitle.isEmpty()) {
                    String joinedBody = String.join(" ", currentBody).trim();
                    // Only add if body is substantial (discards empty headers)
                    if (joinedBody.length() >= 25) {
                        passages.add(new Passage(currentTitle, joinedBody));
                    }
                }
                currentTitle = stripped;
                currentBody.clear();
            } else if (!seenFirstSection) {
                preambleLines.add(stripped);
            } else if (!currentTitle.isEmpty()) {
                if (stripped.toUpperCase().startsWith("IN WITNESS WHEREOF")) {
                    String joinedBody = String.join(" ", currentBody).trim();
                    if (joinedBody.length() >= 25) {
                        passages.add(new Passage(currentTitle, joinedBody));
                    }
                    currentTitle = "";
                    currentBody.clear();
                } else {
                    currentBody.add(stripped);
                }
            }
        }

        if (!currentTitle.isEmpty()) {
            String joinedBody = String.join(" ", currentBody).trim();
            if (joinedBody.length() >= 25) {
                passages.add(new Passage(currentTitle, joinedBody));
            }
        }

        // Preamble passage (parties, date, agreement name)
        if (!preambleLines.isEmpty()) {
            String preambleText = String.join(" ", preambleLines).trim();
            if (preambleText.length() >= 30) {
                passages.add(new Passage("Agreement Preamble & Parties", preambleText));
            }
        }

        // Generic metadata blocks
        extractMetadataPassages(text, passages);

        // Fallback paragraph passages
        String[] paragraphs = text.split("\n{2,}");
        for (String para : paragraphs) {
            String cleaned = para.replaceAll("\\s+", " ").trim();
            if (cleaned.length() >= 30 && !cleaned.toLowerCase().contains("table of contents")) {
                passages.add(new Passage("", cleaned));
            }
        }

        // Sliding sentence window passages
        List<String> sentences = splitIntoCleanSentences(text);
        for (int i = 0; i < sentences.size(); i++) {
            passages.add(new Passage("", sentences.get(i)));
            if (i + 1 < sentences.size()) {
                passages.add(new Passage("", sentences.get(i) + " " + sentences.get(i + 1)));
            }
        }

        return passages;
    }

    private void extractMetadataPassages(String text, List<Passage> passages) {
        // Employee signature / name block detection
        Pattern empSig = Pattern.compile("(?m)^([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)\\s*\\r?\\n\\s*(?:Employee|Candidate|Signatory)");
        Matcher empSigMatcher = empSig.matcher(text);
        if (empSigMatcher.find()) {
            String empName = empSigMatcher.group(1).replaceAll("(?i)Authorized\\s+Signatory\\s*", "").trim();
            passages.add(new Passage("Employee Details", "The employee identified in this contract is " + empName + "."));
        }

        // Company signature / name block detection
        Pattern compSig = Pattern.compile("(?m)^For\\s+([A-Z][^\\r\\n]+)");
        Matcher compSigMatcher = compSig.matcher(text);
        if (compSigMatcher.find()) {
            passages.add(new Passage("Company Details", "The company / employer identified in this contract is " + compSigMatcher.group(1).trim() + "."));
        }

        // Company address detection
        Pattern addressPattern = Pattern.compile("([0-9]+\\s+[^\\n]+(?:Drive|Street|Road|Sector|Avenue)[^\\n]*(?:\\n[^\\n]+(?:Noida|Delhi|Mumbai|Bangalore|Hyderabad|Chennai|India|Pradesh|[0-9]{5,6}))*)", Pattern.CASE_INSENSITIVE);
        Matcher addrMatcher = addressPattern.matcher(text);
        if (addrMatcher.find()) {
            String addr = addrMatcher.group(1).replaceAll("\\s+", " ").trim();
            passages.add(new Passage("Company Address", addr));
        }

        // Contact info block (Email | Phone)
        Pattern contactPattern = Pattern.compile("((?:Email|Phone|Contact)[^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher contactMatcher = contactPattern.matcher(text);
        if (contactMatcher.find()) {
            passages.add(new Passage("Contact Details", contactMatcher.group(1).replaceAll("\\s+", " ").trim()));
        }

        // Contract No & Date block
        Pattern contractNoPattern = Pattern.compile("(Contract\\s+No\\.[^\\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher noMatcher = contractNoPattern.matcher(text);
        if (noMatcher.find()) {
            passages.add(new Passage("Contract Overview", noMatcher.group(1).replaceAll("\\s+", " ").trim()));
        }
    }

    private double scorePassage(String rawQuestion, List<String> queryTokens, Set<String> expandedTerms, Passage passage, int[] matchStats) {
        String title = passage.title.toLowerCase();
        String body = passage.body.toLowerCase();
        String combined = passage.getFullContent().toLowerCase();

        // If body is empty or too short, do not match
        if (combined.length() < 15) {
            matchStats[0] = 0;
            return 0.0;
        }

        double score = 0.0;
        int tokenMatches = 0;
        int titleMatches = 0;
        int synonymMatches = 0;

        // 1. Direct Multi-word Phrase Matching
        for (int i = 0; i < queryTokens.size() - 1; i++) {
            String phrase = queryTokens.get(i) + " " + queryTokens.get(i + 1);
            if (combined.contains(phrase)) {
                score += 10.0;
                tokenMatches += 2;
            }
        }

        // 2. Query Token Matching
        for (String token : queryTokens) {
            String tokenStem = stem(token);

            // Matches in Title (ONLY if query token matches title)
            if (!title.isEmpty()) {
                if (title.contains(token)) {
                    score += 6.0;
                    titleMatches++;
                } else if (stem(title).contains(tokenStem)) {
                    score += 4.0;
                    titleMatches++;
                }
            }

            // Matches in Body
            if (containsWord(combined, token)) {
                score += 3.5;
                tokenMatches++;
            } else if (combined.contains(tokenStem)) {
                score += 2.0;
                tokenMatches++;
            }
        }

        // 3. Synonym / Concept Matches
        for (String term : expandedTerms) {
            if (queryTokens.contains(term)) continue;
            String termStem = stem(term);

            if (!title.isEmpty() && title.contains(term)) {
                score += 3.5;
                synonymMatches++;
            } else if (containsWord(combined, term)) {
                score += 2.0;
                synonymMatches++;
            } else if (combined.contains(termStem)) {
                score += 1.2;
                synonymMatches++;
            }
        }

        // CRITICAL: If 0 query tokens, 0 title matches, and 0 synonyms matched:
        // Score is strictly ZERO! NO UNCONDITIONAL TITLE BONUSES!
        if (tokenMatches == 0 && titleMatches == 0 && synonymMatches == 0) {
            matchStats[0] = 0;
            return 0.0;
        }

        // 4. Intent & Pattern Alignment Boosts
        String qLower = rawQuestion.toLowerCase();

        // A. Financial / Contract Value intent
        if (qLower.contains("value") || qLower.contains("cost") || qLower.contains("price") || qLower.contains("fee") ||
            qLower.contains("fees") || qLower.contains("amount") || qLower.contains("worth") || qLower.contains("salary") ||
            qLower.contains("stipend") || qLower.contains("compensation") || qLower.contains("pay")) {
            if (combined.matches(".*(?:\\$|₹|rs\\.?|inr|usd|eur|gbp)\\s*\\d+.*") ||
                combined.matches(".*\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:dollars?|rupees?|inr|usd|per\\s+(?:month|year|annum)|/-).*") ||
                combined.contains("sum of") || combined.contains("fee of") || combined.contains("amount of") ||
                combined.contains("compensation of") || combined.contains("stipend of")) {
                score += 7.0;
            }
        }

        // B. Duration / Term intent
        if (qLower.contains("duration") || qLower.contains("term") || qLower.contains("period") || qLower.contains("validity") ||
            qLower.contains("how long") || qLower.contains("lifespan")) {
            if (combined.matches(".*\\b(?:one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s*(?:years?|months?|days?|weeks?)\\b.*") ||
                combined.contains("valid for") || combined.contains("term of") || combined.contains("period of") ||
                combined.contains("shall remain in effect") || combined.contains("shall remain valid")) {
                score += 7.0;
            }
        }

        // C. Date intent
        if (qLower.contains("date") || qLower.contains("effective") || qLower.contains("commence") || qLower.contains("start") ||
            qLower.contains("sign") || qLower.contains("execution") || qLower.contains("when")) {
            boolean hasCalendarDate = combined.matches(".*\\b(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+\\d{4}\\b.*") ||
                    combined.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*") || combined.matches(".*\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b.*");
            if (hasCalendarDate) {
                score += 12.0;
            } else if (combined.contains("effective date") || combined.contains("entered into on") || combined.contains("commencing on")) {
                score += 4.0;
            }
        }

        // D. Parties / Who intent
        if (qLower.contains("party") || qLower.contains("parties") || qLower.contains("who") || qLower.contains("between") ||
            qLower.contains("client") || qLower.contains("vendor") || qLower.contains("provider") || qLower.contains("employer") || qLower.contains("employee")) {
            if (combined.contains("by and between") || combined.contains("entered into between") ||
                combined.contains("client") || combined.contains("provider") || combined.contains("consultant") ||
                combined.contains("employee") || combined.contains("employer") || passage.title.contains("Parties") || passage.title.contains("Signatures")) {
                score += 6.0;
            }
        }

        // E. Termination / Cancellation / Notice intent
        if (qLower.contains("terminate") || qLower.contains("termination") || qLower.contains("cancel") || qLower.contains("notice")) {
            if (combined.contains("written notice") || combined.contains("notice period") || combined.contains("immediate termination") ||
                combined.contains("breach of this agreement") || combined.contains("may terminate")) {
                score += 7.0;
            }
        }

        // F. Governing law / Court intent
        if (qLower.contains("court") || qLower.contains("dispute") || qLower.contains("law") || qLower.contains("governing") || qLower.contains("jurisdiction")) {
            if (combined.contains("governed by") || combined.contains("laws of") || combined.contains("jurisdiction of") ||
                combined.contains("courts of") || combined.contains("arbitration")) {
                score += 7.0;
            }
        }

        // G. Payment schedule / Invoice intent
        if (qLower.contains("payment") || qLower.contains("pay") || qLower.contains("invoice") || qLower.contains("when must")) {
            if (combined.contains("receipt of the invoice") || combined.contains("within 30 days") || combined.contains("payment shall be made") ||
                combined.contains("due date") || combined.contains("net 30") || combined.contains("net 60") || combined.contains("invoice")) {
                score += 7.0;
            }
        }

        // Bonus if multiple unique query tokens matched in this passage
        if (tokenMatches >= 2) {
            score += 4.0;
        }

        // Length normalization: slightly penalize excessive blobs over 400 chars, but do not crush
        if (combined.length() > 400) {
            score -= Math.min(4.0, (combined.length() - 400) * 0.003);
        }

        matchStats[0] = tokenMatches + titleMatches + synonymMatches;
        return score;
    }

    private boolean containsWord(String text, String word) {
        return text.matches(".*\\b" + Pattern.quote(word) + "\\b.*");
    }

    private String stem(String word) {
        String w = word.toLowerCase();
        String[] suffixes = {"ies", "ves", "tion", "sion", "ment", "able", "ible", "ance", "ence", "ing", "ed", "es", "s"};
        for (String suffix : suffixes) {
            if (w.length() > suffix.length() + 2 && w.endsWith(suffix)) {
                if (suffix.equals("ies")) return w.substring(0, w.length() - 3) + "y";
                return w.substring(0, w.length() - suffix.length());
            }
        }
        return w;
    }

    private List<String> tokenizeAndFilter(String input) {
        String[] rawTokens = WORD_PATTERN.split(input.toLowerCase());
        List<String> meaningful = new ArrayList<>();
        for (String raw : rawTokens) {
            String token = raw.trim();
            if (token.isEmpty()) continue;

            // Apply typo correction
            if (TYPO_MAP.containsKey(token)) {
                token = TYPO_MAP.get(token);
            }

            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                meaningful.add(token);
            }
        }
        return meaningful;
    }

    private Set<String> expandQueryTerms(List<String> tokens) {
        Set<String> expanded = new HashSet<>();
        for (String token : tokens) {
            if (SYNONYMS.containsKey(token)) {
                expanded.addAll(SYNONYMS.get(token));
            }
        }
        return expanded;
    }

    private List<String> splitIntoCleanSentences(String text) {
        List<String> list = new ArrayList<>();
        String[] parts = text.split("(?<=[.!?])\\s+|\\n{2,}");
        for (String p : parts) {
            String s = p.replaceAll("[\\r\\n]+", " ").trim();
            if (s.length() >= 15) {
                list.add(s);
            }
        }
        return list;
    }
}
