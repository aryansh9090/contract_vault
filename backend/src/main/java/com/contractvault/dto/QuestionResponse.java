package com.contractvault.dto;

public class QuestionResponse {

    private Long contractId;
    private String question;
    private String answer;
    private boolean found;
    private String sourceExcerpt;

    public QuestionResponse() {
    }

    public QuestionResponse(Long contractId, String question, String answer, boolean found, String sourceExcerpt) {
        this.contractId = contractId;
        this.question = question;
        this.answer = answer;
        this.found = found;
        this.sourceExcerpt = sourceExcerpt;
    }

    public Long getContractId() {
        return contractId;
    }

    public void setContractId(Long contractId) {
        this.contractId = contractId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public String getSourceExcerpt() {
        return sourceExcerpt;
    }

    public void setSourceExcerpt(String sourceExcerpt) {
        this.sourceExcerpt = sourceExcerpt;
    }
}
