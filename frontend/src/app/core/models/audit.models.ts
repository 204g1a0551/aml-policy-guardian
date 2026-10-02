export interface AuditLogItem {
  id: string;
  userId: string;
  username: string;
  action: string;
  resourceType: string;
  resourceId: string;
  requestId: string;
  metadata: string;
  createdAt: string;
}
