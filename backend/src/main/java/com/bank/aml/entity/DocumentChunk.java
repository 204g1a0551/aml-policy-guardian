package com.bank.aml.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId = "default-bank";

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(length = 255)
    private String section;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @org.hibernate.annotations.ColumnTransformer(write = "?::vector")
    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    @Column(name = "parent_chunk_id")
    private UUID parentChunkId;

    @Column(name = "is_parent", nullable = false)
    private Boolean isParent = false;

    @Column(name = "chunk_summary", columnDefinition = "TEXT")
    private String chunkSummary;

    @Column(name = "policy_code", length = 100)
    private String policyCode;

    @Column(name = "is_effective", nullable = false)
    private Boolean isEffective = true;

    @Column(name = "effective_date")
    private OffsetDateTime effectiveDate = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DocumentChunk() {}

    public DocumentChunk(UUID documentId, String chunkText, Integer chunkIndex, Integer pageNumber, String section, String metadata, String embedding) {
        this.documentId = documentId;
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.section = section;
        this.metadata = metadata;
        this.embedding = embedding;
        this.isParent = false;
        this.createdAt = OffsetDateTime.now();
    }

    public DocumentChunk(UUID documentId, String chunkText, Integer chunkIndex, Integer pageNumber, String section, String metadata, String embedding, UUID parentChunkId, Boolean isParent) {
        this(documentId, chunkText, chunkIndex, pageNumber, section, metadata, embedding);
        this.parentChunkId = parentChunkId;
        this.isParent = isParent != null ? isParent : false;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    public UUID getParentChunkId() { return parentChunkId; }
    public void setParentChunkId(UUID parentChunkId) { this.parentChunkId = parentChunkId; }

    public Boolean getIsParent() { return isParent; }
    public void setIsParent(Boolean isParent) { this.isParent = isParent; }

    public String getChunkSummary() { return chunkSummary; }
    public void setChunkSummary(String chunkSummary) { this.chunkSummary = chunkSummary; }

    public String getPolicyCode() { return policyCode; }
    public void setPolicyCode(String policyCode) { this.policyCode = policyCode; }

    public Boolean getIsEffective() { return isEffective; }
    public void setIsEffective(Boolean isEffective) { this.isEffective = isEffective; }

    public OffsetDateTime getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(OffsetDateTime effectiveDate) { this.effectiveDate = effectiveDate; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
