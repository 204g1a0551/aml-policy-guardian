package com.bank.aml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int topK = 5;
    private double similarityThreshold = 0.72;

    private String retrievalStrategy = "HYBRID_RERANK";
    private double rrfK = 60.0;
    private double vectorWeight = 0.6;
    private double keywordWeight = 0.4;
    private String rerankerStrategy = "LEXICAL_SEMANTIC_CROSS_ENCODER";
    private boolean parentChildEnabled = true;
    private boolean multiQueryEnabled = true;
    private double evidenceQualityThreshold = 0.65;
    private boolean insufficientEvidenceAbstention = true;
    private String llmModel = "gemini-1.5-flash";

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public String getRetrievalStrategy() {
        return retrievalStrategy;
    }

    public void setRetrievalStrategy(String retrievalStrategy) {
        this.retrievalStrategy = retrievalStrategy;
    }

    public double getRrfK() {
        return rrfK;
    }

    public void setRrfK(double rrfK) {
        this.rrfK = rrfK;
    }

    public double getVectorWeight() {
        return vectorWeight;
    }

    public void setVectorWeight(double vectorWeight) {
        this.vectorWeight = vectorWeight;
    }

    public double getKeywordWeight() {
        return keywordWeight;
    }

    public void setKeywordWeight(double keywordWeight) {
        this.keywordWeight = keywordWeight;
    }

    public String getRerankerStrategy() {
        return rerankerStrategy;
    }

    public void setRerankerStrategy(String rerankerStrategy) {
        this.rerankerStrategy = rerankerStrategy;
    }

    public boolean isParentChildEnabled() {
        return parentChildEnabled;
    }

    public void setParentChildEnabled(boolean parentChildEnabled) {
        this.parentChildEnabled = parentChildEnabled;
    }

    public boolean isMultiQueryEnabled() {
        return multiQueryEnabled;
    }

    public void setMultiQueryEnabled(boolean multiQueryEnabled) {
        this.multiQueryEnabled = multiQueryEnabled;
    }

    public double getEvidenceQualityThreshold() {
        return evidenceQualityThreshold;
    }

    public void setEvidenceQualityThreshold(double evidenceQualityThreshold) {
        this.evidenceQualityThreshold = evidenceQualityThreshold;
    }

    public boolean isInsufficientEvidenceAbstention() {
        return insufficientEvidenceAbstention;
    }

    public void setInsufficientEvidenceAbstention(boolean insufficientEvidenceAbstention) {
        this.insufficientEvidenceAbstention = insufficientEvidenceAbstention;
    }
}
