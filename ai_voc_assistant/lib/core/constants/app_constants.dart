class AppConstants {
  AppConstants._();

  static const String appName = 'AI VOC Assistant';
  static const String appVersion = '1.0.0';

  // DB
  static const String dbName = 'voc_assistant.db';
  static const int dbVersion = 3;

  // Tables
  static const String tableVocs = 'vocs';
  static const String tableResponses = 'responses';
  static const String tableKnowledgeBase = 'knowledge_base';
  static const String tableJiraLinks = 'jira_links';
  static const String tableSettings = 'settings';
  static const String tableEmails = 'emails';
  static const String tableEmailAttachments = 'email_attachments';
  static const String tableAgentLogs = 'agent_logs';
  static const String tableAiAccuracyMetrics = 'ai_accuracy_metrics';

  // Settings Keys
  static const String settingAiProvider = 'ai_provider';
  static const String settingOllamaUrl = 'ollama_url';
  static const String settingOllamaModel = 'ollama_model';
  static const String settingOpenAiKey = 'openai_api_key';
  static const String settingOpenAiModel = 'openai_model';
  static const String settingGeminiKey = 'gemini_api_key';
  static const String settingGeminiModel = 'gemini_model';
  static const String settingFaissEndpoint = 'faiss_endpoint';
  static const String settingJiraUrl = 'jira_url';
  static const String settingJiraProjectKey = 'jira_project_key';
  static const String settingJiraToken = 'jira_token';
  static const String settingJiraEmail = 'jira_email';
  static const String settingAdminPassword = 'admin_password';
  static const String settingUserName = 'user_name';
  static const String settingUserRole = 'user_role';
  static const String settingCustomCategories = 'custom_categories';
  static const String settingOutlookAccessToken = 'outlook_access_token';
  static const String settingOutlookMailbox = 'outlook_mailbox';
  static const String settingOutlookFolder = 'outlook_folder';
  static const String settingTeamsWebhook = 'teams_webhook';
  static const String settingSlackWebhook = 'slack_webhook';
  static const String settingConfluenceUrl = 'confluence_url';
  static const String settingConfluenceSpace = 'confluence_space';
  static const String settingConfluenceEmail = 'confluence_email';
  static const String settingConfluenceToken = 'confluence_token';
  static const String settingUrgencyWebhookThreshold = 'urgency_webhook_threshold';

  // AI Providers
  static const String aiProviderOllama = 'ollama';
  static const String aiProviderOpenAi = 'openai';
  static const String aiProviderGemini = 'gemini';

  // Default values
  static const String defaultOllamaUrl = 'http://localhost:11434';
  static const String defaultOllamaModel = 'llama3.2';
  static const String defaultOpenAiModel = 'gpt-4o-mini';
  static const String defaultGeminiModel = 'gemini-1.5-flash';
  static const String defaultAdminPassword = 'admin1234';
  static const String defaultUrgencyWebhookThreshold = 'High';

  // VOC Status
  static const String vocStatusOpen = 'OPEN';
  static const String vocStatusInProgress = 'IN_PROGRESS';
  static const String vocStatusResolved = 'RESOLVED';
  static const String vocStatusRejected = 'REJECTED';

  // Response Status
  static const String responseDraft = 'DRAFT';
  static const String responseApproved = 'APPROVED';

  // Priority
  static const String priorityHigh = 'HIGH';
  static const String priorityMedium = 'MEDIUM';
  static const String priorityLow = 'LOW';

  // Default Categories
  static const List<String> defaultCategories = [
    '장애',
    '기능문의',
    '사용법',
    '개선요청',
    '운영문의',
    '계약문의',
  ];

  // Vector Search
  static const int topKSimilar = 5;
  static const double similarityThreshold = 0.3;

  // Embedding dimension (OpenAI text-embedding-3-small)
  static const int embeddingDim = 1536;
}
