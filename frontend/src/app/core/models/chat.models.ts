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
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  createdAt: string;
  citations?: Citation[];
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

export interface AskQuestionRequest {
  message: string;
  query?: string;
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
}
