export interface Citation {
  documentId: string;
  documentTitle: string;
  documentFilename?: string;
  chunkId?: string;
  pageNumber: number | null;
  section: string | null;
  similarityScore?: number;
  similarity?: number;
  chunkSnippet?: string;
  snippet?: string;
  citationSupportScore?: number;
  verified?: boolean;
  verificationReason?: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  createdAt: string;
  citations?: Citation[];
  evidenceQualityScore?: number;
  evidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW' | 'INSUFFICIENT';
  isRefusal?: boolean;
  retrievalStrategy?: string;
}

export interface ChatSession {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

export interface CreateChatSessionRequest {
  title: string;
}

export interface RetrievalFilter {
  documentId?: string | null;
  documentType?: string | null;
  section?: string | null;
  retrievalStrategy?: 'HYBRID_RERANK' | 'HYBRID' | 'VECTOR_ONLY' | 'KEYWORD_ONLY';
}

export interface AskQuestionRequest {
  message: string;
  query?: string;
  filters?: RetrievalFilter;
}

export interface ChatStreamEvent {
  type: 'START' | 'CITATIONS' | 'TOKEN' | 'COMPLETE' | 'ERROR';
  correlationId?: string;
  sessionId?: string;
  messageId?: string;
  token?: string;
  citations?: Citation[];
  error?: string;
  timestamp?: string;
  evidenceQualityScore?: number;
  evidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW' | 'INSUFFICIENT';
  retrievalStrategy?: string;
}

export interface RetrievalComparisonResponse {
  query: string;
  totalComparisonTimeMs: number;
  strategies: Record<string, {
    strategyName: string;
    latencyMs: number;
    resultCount: number;
    topScore: number;
    chunks: Array<{
      chunkId: string;
      documentId: string;
      documentTitle: string;
      section: string;
      pageNumber: number;
      rank: number;
      score: number;
      matchSource: string;
      snippet: string;
    }>;
  }>;
  strategyJaccardOverlap: Record<string, number>;
  recommendation: string;
}
